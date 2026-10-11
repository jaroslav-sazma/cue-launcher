package app.cue.launcher.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import app.cue.launcher.engine.BluetoothState
import app.cue.launcher.engine.StateAspect
import app.cue.launcher.engine.StateProvider
import app.cue.launcher.engine.SystemState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

@SingleIn(AppScope::class)
@Inject
class BluetoothConditions(private val application: Application) : StateProvider {

    override val aspect: StateAspect = StateAspect.BLUETOOTH

    override fun observe(): Flow<SystemState> = when {

        !bluetoothConnectGranted(application) -> flowOf(SystemState())
        else -> connectionChanges().map { SystemState(bluetooth = measure()) }
    }

    private fun connectionChanges(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }
        // The ACL broadcasts come from the Bluetooth module's own uid, which RECEIVER_NOT_EXPORTED
        // shuts out; all three actions are protected, so no app can fake them.
        application.registerReceiver(receiver, CONNECTION_CHANGES, Context.RECEIVER_EXPORTED)
        send(Unit)
        awaitClose { application.unregisterReceiver(receiver) }
    }

    private suspend fun measure(): BluetoothState =
        BluetoothState(connectedBluetoothDevices(application).map(ConnectedBtDevice::address).toSet())

    private companion object {
        val CONNECTION_CHANGES = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
    }
}

data class ConnectedBtDevice(val address: String, val name: String?)

// Lint does not follow the bluetoothConnectGranted() guard on the first line, and every call that
// needs the permission is wrapped in runCatching besides.
@SuppressLint("MissingPermission")
@Suppress("ReturnCount")
suspend fun connectedBluetoothDevices(context: Context): List<ConnectedBtDevice> {
    if (!bluetoothConnectGranted(context)) return emptyList()
    val manager = context.getSystemService(BluetoothManager::class.java)
    val adapter = manager?.adapter

    if (manager == null || adapter == null || !adapter.isEnabled) return emptyList()
    val gatt = runCatching { manager.getConnectedDevices(BluetoothProfile.GATT) }
        .getOrDefault(emptyList())
    val profiles = coroutineScope {
        AUDIO_PROFILES.map { profile -> async { connectedVia(context, adapter, profile) } }.awaitAll()
    }
    return (gatt + profiles.flatten()).map(::btDeviceOf).distinctBy(ConnectedBtDevice::address)
}

@SuppressLint("MissingPermission")
fun pairedBluetoothDevices(context: Context): List<ConnectedBtDevice> {
    if (!bluetoothConnectGranted(context)) return emptyList()
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    val bonded = runCatching { adapter?.bondedDevices }.getOrNull().orEmpty()
    return bonded.map(::btDeviceOf)
        .distinctBy(ConnectedBtDevice::address)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: it.address })
}

fun pairedNotConnected(
    connected: List<ConnectedBtDevice>,
    paired: List<ConnectedBtDevice>,
): List<ConnectedBtDevice> {
    val taken = connected.mapTo(HashSet()) { it.address.uppercase(Locale.ROOT) }
    return paired.filterNot { it.address.uppercase(Locale.ROOT) in taken }
}

@SuppressLint("MissingPermission")
private fun btDeviceOf(device: BluetoothDevice): ConnectedBtDevice = ConnectedBtDevice(
    address = device.address.uppercase(Locale.ROOT),
    name = runCatching { device.name }.getOrNull()?.takeIf(String::isNotBlank),
)

private suspend fun connectedVia(
    context: Context,
    adapter: BluetoothAdapter,
    profile: Int,
): List<BluetoothDevice> = withTimeoutOrNull(PROXY_TIMEOUT_MILLIS) {
    suspendCancellableCoroutine { continuation ->
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(type: Int, proxy: BluetoothProfile) {
                val devices = runCatching { proxy.connectedDevices }.getOrDefault(emptyList())
                adapter.closeProfileProxy(type, proxy)
                if (continuation.isActive) continuation.resume(devices)
            }

            override fun onServiceDisconnected(type: Int) {
                if (continuation.isActive) continuation.resume(emptyList())
            }
        }
        val opened = runCatching { adapter.getProfileProxy(context, listener, profile) }
            .getOrDefault(false)
        if (!opened && continuation.isActive) continuation.resume(emptyList())
    }
} ?: emptyList()

private val AUDIO_PROFILES = listOf(
    BluetoothProfile.HEADSET,
    BluetoothProfile.A2DP,
    BluetoothProfile.HEARING_AID,
    BluetoothProfile.LE_AUDIO,
)

private const val PROXY_TIMEOUT_MILLIS = 2_000L

fun bluetoothConnectGranted(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

const val BLUETOOTH_CONNECT_PERMISSION: String = Manifest.permission.BLUETOOTH_CONNECT
