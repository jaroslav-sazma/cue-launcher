package app.cue.launcher.system.bridge

import android.app.Application
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import app.cue.launcher.data.bridge.BridgeConfigRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import kotlin.coroutines.resume

// The Mac's address can change after pairing — a new DHCP lease, another network — while its Bonjour
// instance name stays. A connection that fails at the saved address asks the network once for that
// name. Trust never comes from the answer: the address found is tried with the same pinned handshake
// and saved only once that passed, so a spoofed record costs at most one failed attempt.
@SingleIn(AppScope::class)
@Inject
class BridgeLocator(
    private val application: Application,
    private val config: BridgeConfigRepository,
) {

    internal suspend fun <T> dial(
        endpoint: BridgeEndpoint,
        pin: String,
        macName: String,
        connect: suspend (BridgeEndpoint) -> T,
    ): T = dialRelocating(
        endpoint = endpoint,
        connect = connect,
        locate = { if (config.settings().devEndpoint == null) locate(macName) else null },
        moved = { found ->
            if (config.moveEndpoint(pin, found.host, found.port)) BridgeLog.info("Mac found at a new address, saved")
        },
    )

    private suspend fun locate(name: String): BridgeEndpoint? {
        BridgeLog.event("saved address failed, resolving the Mac by name")
        val found = withTimeoutOrNull(LOCATE_TIMEOUT_MS) { resolve(name) }
        if (found == null) BridgeLog.event("the Mac did not answer by name")
        return found
    }

    // The callback is dropped as soon as the name resolves, the wait times out or the caller goes, so
    // nothing keeps listening on the network.
    private suspend fun resolve(name: String): BridgeEndpoint? {
        val manager = application.getSystemService(NsdManager::class.java) ?: return null
        val wanted = NsdServiceInfo().apply {
            serviceName = name
            serviceType = BRIDGE_SERVICE_TYPE
        }
        var registered: NsdManager.ServiceInfoCallback? = null
        return try {
            suspendCancellableCoroutine { continuation ->
                val callback = object : NsdManager.ServiceInfoCallback {
                    private var answered = false

                    override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                        val endpoint = bonjourEndpointOf(
                            serviceInfo.hostAddresses,
                            serviceInfo.port,
                            serviceInfo.attributes[BRIDGE_TXT_VERSION_KEY],
                        )
                        if (answered || endpoint == null) return
                        answered = true
                        continuation.resume(endpoint)
                    }

                    override fun onServiceLost() = Unit

                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                        if (answered) return
                        answered = true
                        BridgeLog.event("resolving the Mac did not start: $errorCode")
                        continuation.resume(null)
                    }

                    override fun onServiceInfoCallbackUnregistered() = Unit
                }
                runCatching { manager.registerServiceInfoCallback(wanted, DISPATCHER, callback) }
                    .onSuccess { registered = callback }
                    .onFailure { failure ->
                        BridgeLog.event("resolving the Mac was refused: ${failure.javaClass.simpleName}")
                        continuation.resume(null)
                    }
            }
        } finally {
            registered?.let { runCatching { manager.unregisterServiceInfoCallback(it) } }
        }
    }

    private companion object {
        val DISPATCHER = Dispatchers.IO.asExecutor()

        // A responder on the same network answers in a fraction of this.
        const val LOCATE_TIMEOUT_MS = 3_000L
    }
}

// A pin mismatch is an IOException too: whatever answers at the old address now is not the Mac.
internal suspend fun <T> dialRelocating(
    endpoint: BridgeEndpoint,
    connect: suspend (BridgeEndpoint) -> T,
    locate: suspend () -> BridgeEndpoint?,
    moved: suspend (BridgeEndpoint) -> Unit,
): T = try {
    connect(endpoint)
} catch (failure: IOException) {
    val found = locate()?.takeIf { it != endpoint } ?: throw failure
    connect(found).also { moved(found) }
}

// The spec's discovery rule: an instance of another protocol version is not connected to. IPv4 comes
// first; a link-local IPv6 address would need a scope that a saved literal does not keep.
internal fun bonjourEndpointOf(addresses: List<InetAddress>, port: Int, version: ByteArray?): BridgeEndpoint? {
    if (version?.decodeToString() != BRIDGE_PROTOCOL_VERSION.toString() || port !in 1..MAX_PORT) return null
    val address = addresses.firstOrNull { it is Inet4Address }
        ?: addresses.firstOrNull { it is Inet6Address && !it.isLinkLocalAddress }
    return address?.hostAddress?.let { BridgeEndpoint(it, port) }
}

private const val MAX_PORT = 65_535
