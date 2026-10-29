package app.cue.launcher.system.bridge

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattConnectionSettings
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothStatusCodes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

internal sealed interface GattEvent {

    data object Connected : GattEvent

    data object Disconnected : GattEvent

    data object Subscribed : GattEvent

    class Frame(val bytes: ByteArray) : GattEvent
}

internal enum class GattWrite { SENT, NOT_READY, TOO_LARGE, FAILED, TIMED_OUT }

// The stack may never answer a write — a link that dies without a disconnect callback, a busy
// controller — and the writes behind it would wait for the link to drop.
internal suspend fun awaitGattWrite(done: CompletableDeferred<Boolean>, timeoutMillis: Long): GattWrite =
    when (withTimeoutOrNull(timeoutMillis) { done.await() }) {
        true -> GattWrite.SENT
        false -> GattWrite.FAILED
        null -> GattWrite.TIMED_OUT
    }

internal const val GATT_WRITE_TIMEOUT_MS = 5_000L

// The phone as central of the Mac's one GATT service. Everything is driven by the stack's callbacks:
// a lost link goes back to an autoConnect attempt, which the system paces and holds until the Mac is
// in reach again, so nothing here ever waits on a timer. Lives only inside the companion window, which
// follows the adapter for it: the stack reports no disconnect when the adapter turns off.
@SuppressLint("MissingPermission") // the window opens a link only with BLUETOOTH_CONNECT granted
@Suppress("TooManyFunctions")
internal class BridgeGattLink(
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
    private val events: (GattEvent) -> Unit,
) : L2capRouteSource {

    private val lock = Any()

    private var gatt: BluetoothGatt? = null

    private var direct = false

    private var connected = false

    private var closed = false

    private var doorbell: BluetoothGattCharacteristic? = null

    private var payload = 0

    // Read per connection and per service change: the Mac publishes its channel anew, under another
    // PSM, when it restarts, and adds its service again with it.
    private var psm: Int? = null

    private var mtuAfterPsm = 0

    // Discovery, MTU, PSM read and subscription run as one sequence. A service change during one is
    // run after it, as the stack takes one operation at a time.
    private var discovering = false

    private var rediscover = false

    // Held back until the subscription's descriptor write succeeds: the stack runs one operation at a
    // time, so a doorbell written while that write is in flight fails as busy.
    private var pendingDoorbell: BluetoothGattCharacteristic? = null

    private var pendingPayload = 0

    private var pendingWrite: CompletableDeferred<Boolean>? = null

    private val writes = Mutex()

    private val pacing = GattPacing(
        request = { priority ->
            val target = synchronized(lock) { gatt }
            target != null && survived("priority request", false) { target.requestConnectionPriority(priority) }
        },
        log = BridgeLog::event,
    )

    // The adapter coming on may have started the link already.
    fun start() = synchronized(lock) { if (gatt == null) open(direct = true) }

    // The system's transport request while no link is up: a direct attempt reaches a Mac in range at
    // once, where the autoConnect one waits for the stack's slower background scan. An attempt that
    // is already direct, or a link that is up, is left as it is.
    fun ensure() = synchronized(lock) {
        if (closed || connected || direct) return@synchronized
        BridgeLog.event("gatt connect now")
        gatt?.let(::release)
        gatt = null
        open(direct = true)
    }

    // No disconnect callback may come when the adapter turns off, and the object dies with the stack.
    // Nothing is reopened before the adapter is on again; the window tells the machine itself.
    fun adapterOff() {
        synchronized(lock) { drop() }
        pacing.disconnected()
    }

    // Any connection from before the restart is dead even where no off was seen, and the machine
    // forgets it on the same input, so the link starts over whatever it believes it holds.
    fun adapterOn() {
        synchronized(lock) {
            if (closed) return
            drop()
            open(direct = true)
        }
        pacing.disconnected()
    }

    fun close() {
        synchronized(lock) {
            closed = true
            gatt?.let { target -> survived("disconnect", Unit) { target.disconnect() } }
            drop()
        }
        pacing.disconnected()
    }

    private fun drop() {
        gatt?.let(::release)
        gatt = null
        connected = false
        direct = false
        discovering = false
        rediscover = false
        psm = null
        withdraw()
        pendingWrite?.complete(false)
        pendingWrite = null
    }

    private fun release(target: BluetoothGatt) = survived("close", Unit) { target.close() }

    override fun l2capRoute(): L2capRoute? = synchronized(lock) {
        psm?.takeIf { connected }?.let { BluetoothL2capRoute(device, it, pacing) }
    }

    private fun withdraw() {
        doorbell = null
        payload = 0
        pendingDoorbell = null
        pendingPayload = 0
    }

    // One frame per write with response, never fragmented; one write at a time, as the stack allows.
    suspend fun write(frame: ByteArray): GattWrite = writes.withLock {
        val done = CompletableDeferred<Boolean>()
        val started = synchronized(lock) {
            val target = gatt
            val characteristic = doorbell
            when {
                target == null || characteristic == null || payload == 0 -> GattWrite.NOT_READY
                frame.size > payload -> GattWrite.TOO_LARGE
                else -> {
                    pendingWrite = done
                    val status = survived("write", null) {
                        target.writeCharacteristic(
                            characteristic, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                        )
                    }
                    if (status == BluetoothStatusCodes.SUCCESS) {
                        GattWrite.SENT
                    } else {
                        pendingWrite = null
                        GattWrite.FAILED
                    }
                }
            }
        }
        if (started != GattWrite.SENT) return@withLock started
        awaitGattWrite(done, GATT_WRITE_TIMEOUT_MS).also { written ->
            if (written == GattWrite.TIMED_OUT) {
                synchronized(lock) { if (pendingWrite === done) pendingWrite = null }
                BridgeLog.info("gatt write got no response within $GATT_WRITE_TIMEOUT_MS ms")
            }
        }
    }

    fun fits(frameSize: Int): Boolean = synchronized(lock) { doorbell != null && frameSize <= payload }

    // An object made while the adapter is turning off would be dead from the start; adapterOn brings
    // the link back. Direct only with an attempt under way, or ensure() would never retry.
    private fun open(direct: Boolean) {
        this.direct = false
        if (closed) return
        if (adapter.state != BluetoothAdapter.STATE_ON) {
            BridgeLog.event("gatt connect skipped, bluetooth is not on")
            return
        }
        BridgeLog.event("gatt connecting ${if (direct) "direct" else "auto"}")
        val settings = BluetoothGattConnectionSettings.Builder()
            .setAutoConnectEnabled(!direct)
            .setTransport(BluetoothDevice.TRANSPORT_LE)
            .build()
        val opened = survived("connect", null) { device.connectGatt(settings, Runnable::run, callback) }
        if (opened == null) BridgeLog.info("gatt connect not started")
        gatt = opened
        this.direct = direct && opened != null
    }

    private fun discover(target: BluetoothGatt) {
        if (!target.discoverServices()) {
            BridgeLog.event("gatt discovery not started")
            sequenceDone(target)
        }
    }

    private fun sequenceDone(target: BluetoothGatt) {
        val again = synchronized(lock) {
            if (target !== gatt) return
            val queued = rediscover
            rediscover = false
            discovering = queued
            queued
        }
        if (again) discover(target)
    }

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(target: BluetoothGatt, status: Int, newState: Int) {
            val lost = synchronized(lock) {
                if (target !== gatt) {
                    target.close()
                    return
                }
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connected = true
                        direct = false
                        discovering = true
                        rediscover = false
                        mtuAfterPsm = 0
                        null
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        val was = connected
                        connected = false
                        discovering = false
                        rediscover = false
                        psm = null
                        withdraw()
                        pendingWrite?.complete(false)
                        target.close()
                        gatt = null
                        open(direct = false)
                        was
                    }
                    else -> return
                }
            }
            if (lost != null) pacing.disconnected()
            when (lost) {
                null -> {
                    BridgeLog.info("gatt connected")
                    events(GattEvent.Connected)
                    discover(target)
                }
                true -> {
                    BridgeLog.info("gatt disconnected, status=$status")
                    events(GattEvent.Disconnected)
                }
                false -> BridgeLog.event("gatt connect failed, status=$status")
            }
        }

        // The Mac adds its service again when its PSM changes, which ends the subscription; the
        // stack drops its cached services but leaves the rediscovery to the app.
        override fun onServiceChanged(target: BluetoothGatt) {
            val start = synchronized(lock) {
                if (target !== gatt || !connected) return
                psm = null
                doorbell = null
                payload = 0
                val idle = !discovering
                if (idle) discovering = true else rediscover = true
                idle
            }
            BridgeLog.info("gatt service changed")
            if (start) discover(target)
        }

        // The MTU is exchanged once per connection, so a rediscovery goes on with the one it has.
        override fun onServicesDiscovered(target: BluetoothGatt, status: Int) {
            val known = synchronized(lock) { mtuAfterPsm }
            if (known > 0) {
                readPsm(target, known)
            } else if (!target.requestMtu(REQUESTED_MTU)) {
                BridgeLog.event("gatt mtu request refused")
                readPsm(target, DEFAULT_MTU)
            }
        }

        override fun onMtuChanged(target: BluetoothGatt, mtu: Int, status: Int) = readPsm(target, mtu)

        // Before the subscription, which the stack would otherwise have to run alongside: it takes one
        // operation at a time. Without the characteristic — a Mac in pairing mode, or one without the
        // channel — there is simply no L2CAP route.
        private fun readPsm(target: BluetoothGatt, mtu: Int) {
            val characteristic = target.getService(SERVICE_UUID)?.getCharacteristic(PSM_UUID)
            synchronized(lock) {
                if (target !== gatt) return
                psm = null
                mtuAfterPsm = mtu
            }
            if (characteristic == null || !target.readCharacteristic(characteristic)) {
                BridgeLog.event("gatt has no psm")
                subscribe(target, mtu)
            }
        }

        override fun onCharacteristicRead(
            target: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (characteristic.uuid != PSM_UUID) return
            val read = if (status == BluetoothGatt.GATT_SUCCESS) bridgePsmOf(value) else null
            val mtu = synchronized(lock) {
                if (target !== gatt) return
                psm = read
                mtuAfterPsm
            }
            BridgeLog.event(if (read == null) "gatt psm unreadable, status=$status" else "gatt psm=$read")
            subscribe(target, mtu)
        }

        private fun subscribe(target: BluetoothGatt, mtu: Int) {
            val service = target.getService(SERVICE_UUID)
            val bell = service?.getCharacteristic(DOORBELL_UUID)
            val command = service?.getCharacteristic(COMMAND_UUID)
            val descriptor = command?.getDescriptor(CCCD_UUID)
            if (bell == null || command == null || descriptor == null) {
                // Staying connected without the service costs nothing; leaving would only bring the
                // autoConnect attempt straight back. Signals then go over TCP.
                BridgeLog.info("gatt has no bridge service, mtu=$mtu")
                sequenceDone(target)
                return
            }
            synchronized(lock) {
                if (target !== gatt) return
                withdraw()
                pendingDoorbell = bell
                pendingPayload = (mtu - ATT_HEADER_BYTES).coerceAtLeast(0)
            }
            target.setCharacteristicNotification(command, true)
            val started = target.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            BridgeLog.event("gatt mtu=$mtu payload=${mtu - ATT_HEADER_BYTES}")
            if (started != BluetoothStatusCodes.SUCCESS) {
                BridgeLog.info("gatt subscribe not started, status=$started")
                sequenceDone(target)
            }
        }

        // A subscription alone does not tell the Mac who subscribed; the ping that follows does. One that
        // a service change overtook went to the service that was removed, and the rediscovery redoes it.
        override fun onDescriptorWrite(target: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid != CCCD_UUID || descriptor.characteristic?.uuid != COMMAND_UUID) return
            val (subscribed, overtaken) = synchronized(lock) {
                val bell = pendingDoorbell
                if (target !== gatt || bell == null) return
                pendingDoorbell = null
                val success = status == BluetoothGatt.GATT_SUCCESS && !rediscover
                if (success) {
                    doorbell = bell
                    payload = pendingPayload
                }
                success to rediscover
            }
            when {
                subscribed -> {
                    BridgeLog.info("gatt subscribed")
                    events(GattEvent.Subscribed)
                    pacing.subscribed()
                }
                overtaken -> BridgeLog.event("gatt subscription overtaken by a service change")
                else -> BridgeLog.info("gatt subscribe failed, status=$status")
            }
            sequenceDone(target)
        }

        override fun onCharacteristicChanged(
            target: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == COMMAND_UUID && target === synchronized(lock) { gatt }) {
                events(GattEvent.Frame(value.copyOf()))
            }
        }

        override fun onCharacteristicWrite(
            target: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            synchronized(lock) { pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS) }
        }
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString(BRIDGE_GATT_SERVICE)
        val DOORBELL_UUID: UUID = UUID.fromString(BRIDGE_GATT_DOORBELL)
        val COMMAND_UUID: UUID = UUID.fromString(BRIDGE_GATT_COMMAND)
        val PSM_UUID: UUID = UUID.fromString(BRIDGE_GATT_PSM)
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val REQUESTED_MTU = 247
        private const val DEFAULT_MTU = 23
        private const val ATT_HEADER_BYTES = 3

        fun deviceOf(adapter: BluetoothAdapter, address: String, addressType: Int?): BluetoothDevice? = try {
            when (addressType) {
                BluetoothDevice.ADDRESS_TYPE_PUBLIC, BluetoothDevice.ADDRESS_TYPE_RANDOM ->
                    adapter.getRemoteLeDevice(address, addressType)
                else -> adapter.getRemoteDevice(address)
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

// The binder dies with the Bluetooth module when the adapter turns off, and a call into a
// BluetoothGatt from before rethrows the RemoteException as a RuntimeException. The calls the
// window's coroutines make go through here; the stack's own callbacks cannot meet a dead one. The
// rethrow is a plain RuntimeException, so nothing narrower catches it.
@Suppress("TooGenericExceptionCaught")
private inline fun <T> survived(call: String, fallback: T, block: () -> T): T = try {
    block()
} catch (dead: RuntimeException) {
    BridgeLog.warn("gatt $call failed", dead)
    fallback
}

// The insecure channel on purpose: the secure one demands a bonded device, and the bridge never bonds.
// The record layer keyed from the pairing is the channel's security.
@SuppressLint("MissingPermission") // routes come only from a GATT link, which needs BLUETOOTH_CONNECT
private class BluetoothL2capRoute(
    private val device: BluetoothDevice,
    private val psm: Int,
    private val pacing: GattPacing,
) : L2capRoute {

    override fun open(): L2capSocket {
        val socket = try {
            device.createInsecureL2capChannel(psm)
        } catch (denied: SecurityException) {
            throw IOException("no nearby devices access", denied)
        }
        return BluetoothL2capSocket(socket, pacing, pacing.channelOpened())
    }
}

// The link is paced up before the channel connects and down again once it is closed, however often
// the watchers close it.
@SuppressLint("MissingPermission")
private class BluetoothL2capSocket(
    private val socket: BluetoothSocket,
    private val pacing: GattPacing,
    private val channel: Any,
) : L2capSocket {

    override val input: InputStream get() = socket.inputStream

    override val output: OutputStream get() = socket.outputStream

    override fun connect() {
        try {
            socket.connect()
        } catch (denied: SecurityException) {
            throw IOException("no nearby devices access", denied)
        }
    }

    override fun close() {
        try {
            socket.close()
        } finally {
            pacing.channelClosed(channel)
        }
    }
}

private const val PSM_BYTES = 2

internal fun bridgePsmOf(value: ByteArray): Int? {
    if (value.size != PSM_BYTES) return null
    val psm = (value[0].toUByte().toInt() shl Byte.SIZE_BITS) or value[1].toUByte().toInt()
    return psm.takeIf { it != 0 }
}

internal const val BRIDGE_GATT_SERVICE = "e8f0e1fc-e5f4-40ba-a53f-87911835c3bb"
internal const val BRIDGE_GATT_DOORBELL = "0978ea53-a36b-43a4-a38d-5a26bc34b549"
internal const val BRIDGE_GATT_COMMAND = "eec765d8-f473-40be-a4b4-6998d3ef5602"
internal const val BRIDGE_GATT_PSM = "012d7eb8-5ac2-4600-b849-a9475da8a1d8"
