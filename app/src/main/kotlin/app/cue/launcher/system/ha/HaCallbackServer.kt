package app.cue.launcher.system.ha

import android.app.Application
import android.util.Log
import app.cue.launcher.R
import app.cue.launcher.data.ha.HA_CALLBACK_ADDRESS
import app.cue.launcher.data.ha.HA_CALLBACK_STATE
import app.cue.launcher.data.ha.haCallbackQuery
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

// Where the browser comes back to after the user signed in. A loopback socket on a port nobody else
// holds is the native-app pattern of RFC 8252: no page has to be hosted anywhere, and an instance with
// no internet access can complete the flow.
@SingleIn(AppScope::class)
@Inject
class HaCallbackServer(private val application: Application) {

    fun open(): ServerSocket? = try {
        ServerSocket(ANY_PORT, BACKLOG, HA_CALLBACK_ADDRESS)
            .apply { soTimeout = CALLBACK_TIMEOUT_MILLIS }
    } catch (cause: IOException) {
        Log.w(TAG, "No loopback socket for the sign-in callback (${cause::class.java.simpleName})")
        null
    }

    suspend fun await(server: ServerSocket, state: String): Map<String, String>? =
        haAwaitCallback(server, state, HaCallbackWait(CALLBACK_TIMEOUT_MILLIS), ::page)

    private fun page(): String {
        val title = application.getString(R.string.ha_signin_page_title)
        val detail = application.getString(R.string.ha_signin_page_detail)
        val body = "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>$title</title></head><body style=\"font-family:sans-serif;padding:2rem\">" +
            "<h1>$title</h1><p>$detail</p></body></html>"
        return "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\nConnection: close\r\n\r\n$body"
    }

    private companion object {

        const val ANY_PORT = 0

        const val BACKLOG = 1

        const val CALLBACK_TIMEOUT_MILLIS = 180_000
    }
}

// Anything on the device may connect to a loopback port, so a request that does not carry our own
// state is junk: it is dropped and the wait goes on, whatever it was — a connection that closes at
// once, says nothing, sends garbage or the wrong state. Only the deadline, the matching callback or
// cancelling the sign-in end the wait; a short pause after each piece of junk is what bounds the work
// a sender knocking in a loop can cause, not a count it could exhaust to end the sign-in.
//
// A blocking accept or read cannot be interrupted, so cancelling the sign-in closes the sockets under
// it instead — the server and the connection being read — and the blocked call returns.
internal suspend fun haAwaitCallback(
    server: ServerSocket,
    state: String,
    wait: HaCallbackWait,
    page: () -> String,
): Map<String, String>? = coroutineScope {
    val sockets = HaCallbackSockets(server)
    val deadline = System.nanoTime() + wait.timeoutMillis * NANOS_PER_MILLI
    val waiting = async(Dispatchers.IO) {
        HaCallbackListener(sockets, state, deadline, page).listen(wait.junkPauseMillis, wait.log)
    }
    try {
        waiting.await()
    } finally {
        sockets.close()
    }
}

internal class HaCallbackWait(
    val timeoutMillis: Int,
    val junkPauseMillis: Long = JUNK_PAUSE_MILLIS,
    val log: (String) -> Unit = { Log.i(TAG, it) },
)

private class HaCallbackListener(
    private val sockets: HaCallbackSockets,
    private val state: String,
    private val deadline: Long,
    private val page: () -> String,
) {

    private var timedOut = false

    suspend fun listen(junkPauseMillis: Long, log: (String) -> Unit): Map<String, String>? {
        var callback: Map<String, String>? = null
        var junk = 0
        while (callback == null && !over() && currentCoroutineContext().isActive) {
            callback = accept()?.let(::answer)
            if (callback == null && !over()) {
                junk++
                delay(junkPauseMillis)
            }
        }
        if (junk > 0) log("Dropped $junk request(s) on the callback port without the sign-in state")
        if (callback == null && !sockets.closed) log("Sign-in callback never arrived")
        return callback
    }

    private fun over(): Boolean = timedOut || sockets.closed || remaining(deadline) == null

    // Null when nothing was accepted. A failure other than the deadline or a closed server is a
    // connection that went away before it was taken — junk like any other, so the wait goes on.
    private fun accept(): Socket? {
        val timeout = remaining(deadline) ?: return null
        return try {
            sockets.server.soTimeout = timeout
            sockets.accept()
        } catch (_: SocketTimeoutException) {
            timedOut = true
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun answer(socket: Socket): Map<String, String>? = try {
        request(socket)
            .takeIf { haCallbackStateMatches(state, it[HA_CALLBACK_STATE]) }
            ?.also { respond(socket) }
    } finally {
        runCatching { socket.close() }
    }

    // soTimeout bounds a single read only, so a caller dripping one byte at a time would keep it
    // waiting for ever. Each read gets what is left of the connection's own budget instead, and the
    // line itself is capped, so neither time nor memory is the caller's to choose.
    private fun request(socket: Socket): Map<String, String> = try {
        val reads = readsFor(socket, READ_TIMEOUT_MILLIS)
        haRequestLine(socket.getInputStream(), MAX_REQUEST_LINE_BYTES, reads)?.let(::haCallbackQuery).orEmpty()
    } catch (_: IOException) {
        emptyMap()
    }

    // The callback has arrived whether or not the browser is still there to be told so. A socket closed
    // with part of the request still unread is reset by the kernel, and the browser may then show an
    // error in place of the page; so the sending side is closed behind the page, and the rest of the
    // request is read off until the browser closes its side too.
    private fun respond(socket: Socket) {
        try {
            socket.getOutputStream().bufferedWriter().apply {
                write(page())
                flush()
            }
            socket.shutdownOutput()
            haDrain(socket.getInputStream(), MAX_DRAIN_BYTES, readsFor(socket, DRAIN_TIMEOUT_MILLIS))
        } catch (_: IOException) {
        }
    }

    private fun readsFor(socket: Socket, millis: Int): () -> Boolean {
        val until = minOf(deadline, System.nanoTime() + millis * NANOS_PER_MILLI)
        return { remaining(until)?.also { socket.soTimeout = it } != null }
    }
}

private fun remaining(deadline: Long): Int? = ((deadline - System.nanoTime()) / NANOS_PER_MILLI)
    .takeIf { it > 0 }
    ?.coerceAtMost(Int.MAX_VALUE.toLong())
    ?.toInt()

// Remembers what a cancelled sign-in has to close. close() may run on another thread at any point, so
// a connection accepted just as it ran is closed by whichever side sees the other second.
private class HaCallbackSockets(val server: ServerSocket) {

    private val current = AtomicReference<Socket?>(null)

    @Volatile
    var closed = false
        private set

    fun accept(): Socket {
        val socket = server.accept()
        current.set(socket)
        if (closed) runCatching { socket.close() }
        return socket
    }

    fun close() {
        closed = true
        runCatching { server.close() }
        current.getAndSet(null)?.let { runCatching { it.close() } }
    }
}

// Anything on the device can connect to the callback port and guess, so the comparison takes the same
// time however much of the state a guess got right.
internal fun haCallbackStateMatches(expected: String, received: String?): Boolean =
    expected.isNotEmpty() && received != null &&
        MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), received.toByteArray(Charsets.UTF_8))

// The request line up to its line feed, or null when it does not arrive whole: past maxBytes, at the
// end of the stream, or once beforeRead says the time is up.
internal fun haRequestLine(input: InputStream, maxBytes: Int, beforeRead: () -> Boolean): String? {
    val line = ByteArrayOutputStream()
    val chunk = ByteArray(REQUEST_CHUNK_BYTES)
    var result: String? = null
    var reading = true
    while (reading && line.size() < maxBytes && beforeRead()) {
        val read = input.read(chunk, 0, minOf(chunk.size, maxBytes - line.size()))
        val end = (0 until read.coerceAtLeast(0)).firstOrNull { chunk[it] == LINE_FEED }
        if (read > 0) line.write(chunk, 0, end ?: read)
        if (end != null) result = line.toString(Charsets.UTF_8).removeSuffix("\r")
        reading = read >= 0 && end == null
    }
    return result
}

// Reads off and drops what the other side still sends, until it closes its end, maxBytes have come or
// beforeRead says the time is up.
internal fun haDrain(input: InputStream, maxBytes: Int, beforeRead: () -> Boolean) {
    val chunk = ByteArray(REQUEST_CHUNK_BYTES)
    var left = maxBytes
    var read = 0
    while (read >= 0 && left > 0 && beforeRead()) {
        read = input.read(chunk, 0, minOf(chunk.size, left))
        left -= read.coerceAtLeast(0)
    }
}

private const val REQUEST_CHUNK_BYTES = 1024

private const val READ_TIMEOUT_MILLIS = 5_000

private const val MAX_REQUEST_LINE_BYTES = 8 * 1024

private const val DRAIN_TIMEOUT_MILLIS = 1_000

private const val MAX_DRAIN_BYTES = 64 * 1024

private const val JUNK_PAUSE_MILLIS = 30L

private const val NANOS_PER_MILLI = 1_000_000L

private const val TAG = "HaCallback"

private const val LINE_FEED = '\n'.code.toByte()
