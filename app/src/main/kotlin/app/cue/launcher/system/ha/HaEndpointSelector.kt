package app.cue.launcher.system.ha

import app.cue.launcher.data.ha.HaConfig
import app.cue.launcher.data.ha.HaEndpoint
import app.cue.launcher.data.ha.HaEndpointKind
import app.cue.launcher.data.ha.HaNetwork
import app.cue.launcher.data.ha.haEndpointOrder
import app.cue.launcher.data.ha.haHomeNetworkMatches
import app.cue.launcher.data.ha.haNetworkKey
import app.cue.launcher.engine.WifiState
import app.cue.launcher.system.WifiConditions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap

@SingleIn(AppScope::class)
@Inject
class HaEndpointSelector(
    private val wifiConditions: WifiConditions,
    private val discovery: HaDiscovery,
) {

    private val lastWorking = ConcurrentHashMap<String, HaEndpointKind>()

    suspend fun network(): HaNetwork = wifiConditions.observe().first().wifi.toHaNetwork()

    suspend fun endpoints(config: HaConfig): List<HaEndpoint> = endpoints(config, network())

    suspend fun endpoints(config: HaConfig, network: HaNetwork): List<HaEndpoint> =
        haEndpointOrder(config, atHome(config, network), lastWorking[cacheKey(network)])

    // A listed network answers without touching the radio; the paired instance answering on mDNS is
    // the fallback for a phone that never got the location permission. Both have to be on Wi-Fi —
    // on mobile data there is no local network to be at home on.
    suspend fun atHome(config: HaConfig, network: HaNetwork): Boolean = when {
        !network.connected -> false
        haHomeNetworkMatches(config.homeNetworks, network.ssid, network.bssid) -> true
        else -> config.isPaired && discovery.visible(config.instanceUuid)
    }

    fun remember(network: HaNetwork, kind: HaEndpointKind) {
        lastWorking[cacheKey(network)] = kind
    }

    fun invalidate() {
        lastWorking.clear()
        discovery.forget()
    }

    private fun cacheKey(network: HaNetwork): String = haNetworkKey(network) ?: OTHER_NETWORKS

    private companion object {
        const val OTHER_NETWORKS = " other"
    }
}

fun WifiState?.toHaNetwork(): HaNetwork = HaNetwork(
    connected = this?.connected == true,
    ssid = this?.ssid,
    bssid = this?.bssid,
)
