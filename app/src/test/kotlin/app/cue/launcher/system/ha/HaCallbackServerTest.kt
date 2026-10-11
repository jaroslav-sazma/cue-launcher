package app.cue.launcher.system.ha

import app.cue.launcher.data.ha.haAuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

// Anything on the device can connect to the callback port, so what one connection may cost is fixed
// here rather than by whoever is on the other end.
class HaCallbackServerTest {

    private fun stream(text: String): InputStream = ByteArrayInputStream(text.toByteArray())

    @Test
    fun `the request line is read up to its line end`() {
        val request = stream("GET /cue-launcher/auth?code=a&state=s HTTP/1.1\r\nHost: x\r\n\r\n")

        val line = haRequestLine(request, LIMIT) { true }

        assertEquals("GET /cue-launcher/auth?code=a&state=s HTTP/1.1", line)
    }

    @Test
    fun `a line longer than the cap is dropped without reading on`() {
        val reads = CountingStream(stream("GET /" + "a".repeat(100_000) + " HTTP/1.1\r\n"))

        assertNull(haRequestLine(reads, LIMIT) { true })
        assertEquals(LIMIT, reads.bytes)
    }

    @Test
    fun `a line that never ends is not a request`() {
        assertNull(haRequestLine(stream("GET /cue-launcher/auth?state=s"), LIMIT) { true })
        assertNull(haRequestLine(stream(""), LIMIT) { true })
    }

    // The per-connection deadline is checked before every read, so a sender dripping one byte at a
    // time runs out of time instead of holding the wait.
    @Test
    fun `reading stops as soon as the time is up`() {
        var reads = 0

        val line = haRequestLine(OneByteStream("GET / HTTP/1.1\r\n"), LIMIT) { reads++ < 3 }

        assertNull(line)
        assertEquals(4, reads)
    }

    @Test
    fun `draining reads to the end of the stream and no further than its cap`() {
        val short = CountingStream(stream("Host: x\r\n\r\n"))
        haDrain(short, LIMIT) { true }
        assertEquals(11, short.bytes)
        val long = CountingStream(stream("a".repeat(100_000)))
        haDrain(long, LIMIT) { true }
        assertEquals(LIMIT, long.bytes)
    }

    @Test
    fun `draining stops as soon as the time is up`() {
        var reads = 0
        val drained = CountingStream(OneByteStream("Host: x\r\n\r\n"))

        haDrain(drained, LIMIT) { reads++ < 3 }

        assertEquals(3, drained.bytes)
    }

    // A browser sends its headers along with the request line; closing with them unread resets the
    // connection, and a reset that arrives before the browser has read the page throws the page away.
    @Test
    fun `the browser gets the whole page after a request with headers`() = runBlocking {
        val state = haAuthState()
        val page = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
        val headers = "Host: 127.0.0.1\r\n" + "X-Filler: ${"a".repeat(100)}\r\n".repeat(60) + "\r\n"
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val waiting = async(Dispatchers.IO) {
                haAwaitCallback(server, state, HaCallbackWait(WAIT_MILLIS, log = {})) { page }
            }
            val answer = withContext(Dispatchers.IO) {
                Socket(InetAddress.getLoopbackAddress(), server.localPort).use { socket ->
                    socket.soTimeout = WAIT_MILLIS
                    socket.getOutputStream().apply {
                        write("GET /cue-launcher/auth?code=c&state=$state HTTP/1.1\r\n$headers".toByteArray())
                        flush()
                    }
                    Thread.sleep(BROWSER_BUSY_MILLIS)
                    socket.getInputStream().readBytes().decodeToString()
                }
            }

            assertEquals(page, answer)
            assertEquals("c", waiting.await()?.get("code"))
        }
    }

    @Test
    fun `only the exact state lets the callback through`() {
        val state = haAuthState()

        assertTrue(haCallbackStateMatches(state, state))
        assertFalse(haCallbackStateMatches(state, state.dropLast(1)))
        assertFalse(haCallbackStateMatches(state, state + "x"))
        assertFalse(haCallbackStateMatches(state, ""))
        assertFalse(haCallbackStateMatches(state, null))
        assertFalse(haCallbackStateMatches("", ""))
    }

    // Every connection used to count toward a cap, so a few hundred empty ones ended the sign-in as
    // cancelled. Junk of every kind must leave the wait running until the real callback arrives.
    @Test
    fun `no amount of junk on the callback port ends the wait`() = runBlocking {
        val state = haAuthState()
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val waiting = async(Dispatchers.IO) {
                haAwaitCallback(server, state, HaCallbackWait(WAIT_MILLIS, junkPauseMillis = 0, log = {})) { "" }
            }
            val junk = listOf("", "\r\n", "garbage\n", "GET / HTTP/1.1\r\n", "GET /?state=nope HTTP/1.1\r\n")
            withContext(Dispatchers.IO) {
                repeat(JUNK_CONNECTIONS) { send(server.localPort, junk[it % junk.size]) }
                send(server.localPort, "GET /cue-launcher/auth?code=c&state=$state HTTP/1.1\r\n")
            }

            assertEquals("c", waiting.await()?.get("code"))
        }
    }

    @Test
    fun `the wait ends at the deadline when nothing arrives`() = runBlocking {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            assertNull(haAwaitCallback(server, haAuthState(), HaCallbackWait(100, log = {})) { "" })
        }
    }

    // The backlog holds a single connection, so a connect can be refused while the server is still
    // busy with the previous one, and a junk connection the server already dropped resets the write.
    private fun send(port: Int, text: String) {
        repeat(CONNECT_ATTEMPTS) {
            try {
                Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
                    socket.getOutputStream().apply {
                        write(text.toByteArray())
                        flush()
                    }
                }
                return
            } catch (_: SocketException) {
                Thread.sleep(1)
            }
        }
        error("The callback port refused every connection")
    }

    private class CountingStream(private val inner: InputStream) : InputStream() {

        var bytes = 0

        override fun read(): Int = inner.read().also { if (it >= 0) bytes++ }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            inner.read(buffer, offset, length).also { if (it > 0) bytes += it }
    }

    private class OneByteStream(text: String) : InputStream() {

        private val inner = ByteArrayInputStream(text.toByteArray())

        override fun read(): Int = inner.read()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            inner.read(buffer, offset, minOf(length, 1))
    }

    private companion object {
        const val LIMIT = 8 * 1024
        const val WAIT_MILLIS = 30_000
        const val JUNK_CONNECTIONS = 400
        const val CONNECT_ATTEMPTS = 1_000
        const val BROWSER_BUSY_MILLIS = 300L
    }
}
