package app.cue.launcher.system.bridge

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference

internal enum class Carrier(val tag: String) { TCP("tcp"), L2CAP("l2cap") }

internal class Carried<T>(val carrier: Carrier, val value: T)

internal interface L2capSocket : Closeable {

    val input: InputStream

    val output: OutputStream

    fun connect()
}

internal fun interface L2capRoute {

    fun open(): L2capSocket
}

internal fun interface L2capRouteSource {

    fun l2capRoute(): L2capRoute?
}

// The companion window's way to the Mac without IP, and the carrier that last opened a connection,
// for as long as the Mac is present. Neither is persisted: the PSM changes when the Mac restarts, and
// which carrier works depends on where the phone is.
@SingleIn(AppScope::class)
class BridgeCarriers internal constructor(
    private val lanUp: () -> Boolean,
    private val log: (String) -> Unit = {},
) {

    @Inject
    constructor(application: Application) : this({ lanTransportUp(application) }, BridgeLog::event)

    private class Attached(val source: L2capRouteSource) {

        @Volatile
        var preferred = Carrier.TCP
    }

    private val attached = AtomicReference<Attached?>(null)

    internal fun attach(source: L2capRouteSource) {
        attached.set(Attached(source))
    }

    internal fun detach(source: L2capRouteSource) {
        attached.updateAndGet { current -> current?.takeUnless { it.source === source } }
    }

    internal fun l2capRoute(): L2capRoute? = attached.get()?.source?.l2capRoute()

    // TCP first unless L2CAP opened the last connection, so where only Bluetooth reaches the Mac only
    // the first dial waits for TCP to fail. The route is read when its turn comes, so a GATT link that
    // came up during a slow TCP attempt is used. A dial that ends after the window closed remembers
    // nothing, because the memory went with it. Without Wi-Fi or Ethernet a TCP dial could only go
    // over mobile data and wait out its timeout, so it is not made.
    internal suspend fun <T : Any> dial(tcp: suspend () -> T?, l2cap: suspend (L2capRoute) -> T): Carried<T>? {
        val window = attached.get()
        val order = if (window?.preferred == Carrier.L2CAP) {
            listOf(Carrier.L2CAP, Carrier.TCP)
        } else {
            listOf(Carrier.TCP, Carrier.L2CAP)
        }
        return dialCarriers(order) { carrier ->
            when (carrier) {
                Carrier.TCP -> if (lanUp()) tcp() else null.also { log("tcp skipped: no Wi-Fi or Ethernet") }
                Carrier.L2CAP -> window?.source?.l2capRoute()?.let { l2cap(it) }
            }
        }?.also { window?.preferred = it.carrier }
    }
}

// One read of the default network, no callback: a socket goes over the default network, and a VPN
// lists the transports under it.
internal fun lanTransportUp(context: Context): Boolean {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = connectivity?.activeNetwork?.let(connectivity::getNetworkCapabilities) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}

// Null from connect means the carrier has no route now and is skipped. When every carrier that had
// one failed, a pin mismatch is reported over the others: it is the failure the user can act on.
internal suspend fun <T : Any> dialCarriers(order: List<Carrier>, connect: suspend (Carrier) -> T?): Carried<T>? {
    var failure: IOException? = null
    for (carrier in order) {
        val value = try {
            connect(carrier)
        } catch (mismatch: BridgePinMismatchException) {
            failure = mismatch
            null
        } catch (thrown: IOException) {
            if (failure == null) failure = thrown
            null
        }
        if (value != null) return Carried(carrier, value)
    }
    failure?.let { throw it }
    return null
}

internal const val L2CAP_CONNECT_TIMEOUT_MS = 10_000

// Blocking like the TLS connect, with the same watcher: a caller that goes away closes the socket,
// which ends a blocked connect or handshake read. The connection is returned open or closed.
internal suspend fun connectL2cap(
    route: L2capRoute,
    bleKey: ByteArray,
    connectTimeoutMillis: Int = L2CAP_CONNECT_TIMEOUT_MS,
): BridgeConnection = coroutineScope {
    val opening = AtomicReference<Closeable?>(null)
    val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            opening.getAndSet(null)?.let { runCatching { it.close() } }
        }
    }
    try {
        withContext(Dispatchers.IO) {
            val job = coroutineContext.job
            val socket = route.open()
            opening.set(socket)
            if (!job.isActive) runCatching { socket.close() }
            try {
                // The socket's connect takes no timeout of its own, so the deadline closes it instead.
                ReadDeadline { runCatching { socket.close() } }
                    .apply { millis = connectTimeoutMillis }
                    .guard { socket.connect() }
                L2capChannel.connect(socket, L2capStreams(socket.input, socket.output), bleKey)
            } catch (failure: IOException) {
                runCatching { socket.close() }
                // The failure of a socket the watcher closed is the cancellation, not a carrier failure.
                job.ensureActive()
                throw failure
            }
        }.also { opening.set(null) }
    } finally {
        watcher.cancel()
    }
}
