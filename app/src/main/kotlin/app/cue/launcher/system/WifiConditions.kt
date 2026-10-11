package app.cue.launcher.system

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import app.cue.launcher.engine.StateAspect
import app.cue.launcher.engine.StateProvider
import app.cue.launcher.engine.SystemState
import app.cue.launcher.engine.WifiState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@SingleIn(AppScope::class)
@Inject
class WifiConditions(private val application: Application) : StateProvider {

    override val aspect: StateAspect = StateAspect.WIFI

    override fun observe(): Flow<SystemState> = callbackFlow {
        val connectivity = application.getSystemService(ConnectivityManager::class.java)
        if (connectivity == null) {

            send(SystemState())
            awaitClose { }
            return@callbackFlow
        }

        val networks = mutableMapOf<Network, WifiState>()
        val answered = AtomicBoolean(false)
        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                networks[network] = readingOf(networkCapabilities)
                answered.set(true)
                trySend(SystemState(wifi = wifiStateOf(networks.values)))
            }

            override fun onLost(network: Network) {
                networks.remove(network)
                answered.set(true)
                trySend(SystemState(wifi = wifiStateOf(networks.values)))
            }
        }
        connectivity.registerNetworkCallback(WIFI_NETWORKS, callback)
        val measured = measure(connectivity)
        if (measured.connected) {

            launch {
                delay(SSID_GRACE_MILLIS)
                if (!answered.get()) trySend(SystemState(wifi = measured))
            }
        } else {

            send(SystemState(wifi = measured))
        }
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }

    private fun measure(connectivity: ConnectivityManager): WifiState {
        val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
        val connected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        return if (connected) readingOf(capabilities) else WifiState(connected = false)
    }

    private fun readingOf(capabilities: NetworkCapabilities): WifiState {
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return WifiState(connected = false)
        }
        val info = capabilities.transportInfo as? WifiInfo
        return WifiState(
            connected = true,
            ssid = normalizedSsid(info?.ssid),
            bssid = normalizedBssid(info?.bssid),
        )
    }

    private companion object {

        val WIFI_NETWORKS: NetworkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        const val SSID_GRACE_MILLIS = 500L
    }
}

internal fun wifiStateOf(readings: Collection<WifiState>): WifiState = WifiState(
    connected = readings.isNotEmpty(),
    ssid = readings.firstNotNullOfOrNull(WifiState::ssid),
    bssid = readings.firstNotNullOfOrNull(WifiState::bssid),
)

internal fun normalizedSsid(raw: String?): String? {
    val name = raw?.removeSurrounding("\"") ?: return null
    return name.takeIf { it.isNotEmpty() && it != WifiManager.UNKNOWN_SSID }
}

internal fun normalizedBssid(raw: String?): String? =
    raw?.takeIf { it.isNotEmpty() && it != HIDDEN_BSSID }

private const val HIDDEN_BSSID = "02:00:00:00:00:00"

val LOCATION_PERMISSIONS: List<String> = listOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

// Android 17 blocks every connection to a local address without it: the Home Assistant address at
// home, and resolving a .local name. A Nabu Casa or other public address is unaffected, and an
// instance approved in the system picker is reached without it.
val ACCESS_LOCAL_NETWORK_PERMISSION: String = Manifest.permission.ACCESS_LOCAL_NETWORK

fun localNetworkGranted(context: Context): Boolean =
    context.checkSelfPermission(ACCESS_LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED

fun fineLocationGranted(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

// Precise location implies approximate, so this holds whichever of the two the user picked.
fun approximateLocationGranted(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

fun locationServicesEnabled(context: Context): Boolean =
    context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true
