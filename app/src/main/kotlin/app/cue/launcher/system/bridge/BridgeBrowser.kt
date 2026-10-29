package app.cue.launcher.system.bridge

import android.app.Application
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress

internal sealed interface MacBrowse {

    data object None : MacBrowse

    data class One(val target: PairingTarget) : MacBrowse

    data class Several(val targets: List<PairingTarget>) : MacBrowse
}

internal fun browseOutcome(found: Collection<PairingTarget>): MacBrowse {
    val targets = found.distinct().sortedBy { it.name.lowercase() }
    return when (targets.size) {
        0 -> MacBrowse.None
        1 -> MacBrowse.One(targets.single())
        else -> MacBrowse.Several(targets)
    }
}

// Only a Mac in pairing mode carries the flag, and only one of this protocol version is reached.
internal fun pairingTargetOf(
    name: String,
    addresses: List<InetAddress>,
    port: Int,
    attributes: Map<String, ByteArray?>,
): PairingTarget? = bonjourEndpointOf(addresses, port, attributes[BRIDGE_TXT_VERSION_KEY])
    ?.takeIf { attributes[BRIDGE_TXT_PAIR_KEY]?.decodeToString() == BRIDGE_TXT_PAIR_ON }
    ?.let { PairingTarget(name, it.host, it.port) }

// The browse belongs to the pairing screen and lasts seconds: the discovery listener and every
// resolve callback are released when it ends, times out or its caller goes, so nothing keeps
// listening on the network.
@SingleIn(AppScope::class)
@Inject
class BridgeBrowser(private val application: Application) {

    internal suspend fun browse(): MacBrowse {
        val manager = application.getSystemService(NsdManager::class.java) ?: return MacBrowse.None
        val session = BrowseSession(manager)
        return try {
            session.start()
            withTimeoutOrNull(BROWSE_TIMEOUT_MS) {
                if (session.firstHit.await()) delay(SETTLE_MS)
            }
            browseOutcome(session.found())
        } finally {
            session.stop()
        }
    }

    private class BrowseSession(private val manager: NsdManager) {

        // True with the first Mac in pairing mode, false when the browse could not run at all.
        val firstHit = CompletableDeferred<Boolean>()

        private val lock = Any()
        private val targets = mutableMapOf<String, PairingTarget>()
        private val resolving = mutableMapOf<String, NsdManager.ServiceInfoCallback>()
        private var discovering = false
        private var ended = false

        private val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                BridgeLog.event("looking for a Mac in pairing mode did not start: $errorCode")
                synchronized(lock) { discovering = false }
                firstHit.complete(false)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) = resolve(serviceInfo)

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(lock) { targets.remove(serviceInfo.serviceName) }
            }
        }

        fun start() {
            synchronized(lock) { discovering = true }
            runCatching {
                manager.discoverServices(BRIDGE_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, null, DISPATCHER, discovery)
            }.onFailure { failure ->
                BridgeLog.event("looking for a Mac in pairing mode was refused: ${failure.javaClass.simpleName}")
                synchronized(lock) { discovering = false }
                firstHit.complete(false)
            }
        }

        fun found(): List<PairingTarget> = synchronized(lock) { targets.values.toList() }

        fun stop() {
            val (callbacks, wasDiscovering) = synchronized(lock) {
                ended = true
                (resolving.values.toList() to discovering).also {
                    resolving.clear()
                    discovering = false
                }
            }
            if (wasDiscovering) runCatching { manager.stopServiceDiscovery(discovery) }
            callbacks.forEach { runCatching { manager.unregisterServiceInfoCallback(it) } }
        }

        private fun resolve(service: NsdServiceInfo) {
            val name = service.serviceName
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    val target =
                        pairingTargetOf(name, serviceInfo.hostAddresses, serviceInfo.port, serviceInfo.attributes)
                    synchronized(lock) {
                        if (target == null) targets.remove(name) else targets[name] = target
                    }
                    if (target != null) firstHit.complete(true)
                }

                override fun onServiceLost() {
                    synchronized(lock) { targets.remove(name) }
                }

                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    BridgeLog.event("resolving a Mac did not start: $errorCode")
                }

                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            synchronized(lock) {
                if (ended || name in resolving) return
                runCatching { manager.registerServiceInfoCallback(service, DISPATCHER, callback) }
                    .onSuccess { resolving[name] = callback }
                    .onFailure { BridgeLog.event("resolving a Mac was refused: ${it.javaClass.simpleName}") }
            }
        }
    }

    private companion object {
        val DISPATCHER = Dispatchers.IO.asExecutor()

        const val BROWSE_TIMEOUT_MS = 10_000L

        // Once one Mac answered, a moment more for the others on the network, so two Macs in pairing
        // mode are offered as a choice instead of whichever answered first.
        const val SETTLE_MS = 2_000L
    }
}
