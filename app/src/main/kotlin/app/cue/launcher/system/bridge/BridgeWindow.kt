package app.cue.launcher.system.bridge

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.companion.ActionRequest
import android.companion.DevicePresenceEvent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import app.cue.launcher.data.bridge.BridgeAssociation
import app.cue.launcher.data.bridge.BridgeConfigRepository
import app.cue.launcher.data.bridge.OpenedPairing
import app.cue.launcher.system.bluetoothConnectGranted
import app.cue.launcher.system.notifications.NotificationListenerLink
import app.cue.launcher.system.notifications.NotificationReplySender
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private sealed interface WindowInput {
    class Presence(val associationId: Int, val event: Int) : WindowInput
    class Action(val associationId: Int, val action: Int, val operation: Int) : WindowInput
    class Gatt(val generation: Int, val input: PresenceInput) : WindowInput
    class Expired(val token: Long) : WindowInput
    class Radio(val on: Boolean) : WindowInput
    class AttendanceFailed(val generation: Int) : WindowInput
}

private class Attendance(val generation: Int) {

    lateinit var job: Job

    @Volatile
    var gatt: BridgeGattLink? = null

    fun report(inputs: SendChannel<WindowInput>, input: PresenceInput) {
        inputs.trySend(WindowInput.Gatt(generation, input))
    }
}

// The time the system keeps BridgeCompanionService bound because the paired Mac's advertisement is
// in range. Everything the bridge does in the background lives in this scope and dies with it, and
// all but the presence debounce and the Bluetooth state receiver ends sooner, once the debounced
// presence says absent: the GATT link with the L2CAP route read over it, the listener hold, the short
// visits, and a clip held for the unlock with its receiver. Nothing else runs then, even if the
// system keeps the service bound.
@Suppress("LongParameterList", "TooManyFunctions")
@SingleIn(AppScope::class)
@Inject
class BridgeWindow(
    private val application: Application,
    private val config: BridgeConfigRepository,
    private val listener: NotificationListenerLink,
    private val link: BridgeSessionLink,
    private val guard: BridgeReplayGuard,
    private val clipboard: IncomingClipboard,
    private val replies: NotificationReplySender,
    private val nearby: BridgeNearby,
    private val carriers: BridgeCarriers,
    private val rules: MirrorRules,
    private val macLock: BridgeMacLock,
    private val lockMac: BridgeLockMac,
) {

    private var scope: CoroutineScope? = null

    private var inputs: Channel<WindowInput>? = null

    // A settle step already running on another thread may still publish after the cancel; under this
    // lock it sees its scope inactive and stays silent, so ABSENT is the last value a closed window
    // leaves.
    private val publishing = Any()

    // The service's callbacks all come on the main thread, so these need no lock of their own.
    fun open() {
        if (scope != null) return
        val channel = Channel<WindowInput>(Channel.UNLIMITED)
        val opened = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, failure ->
                BridgeLog.warn("companion window failed", failure)
            },
        )
        scope = opened
        inputs = channel
        clipboard.open(opened)
        opened.launch { WindowRun(opened, channel).follow() }
        opened.launch { radioStates().collect { channel.trySend(WindowInput.Radio(it)) } }
        BridgeLog.info("companion window opened")
    }

    // Bluetooth broadcasts may come from the Bluetooth module's own uid, which RECEIVER_NOT_EXPORTED
    // would shut out; the action is protected, so no app can fake it.
    private fun radioStates(): Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> trySend(false)
                    BluetoothAdapter.STATE_ON -> trySend(true)
                }
            }
        }
        application.registerReceiver(receiver, RADIO_CHANGES, Context.RECEIVER_EXPORTED)
        awaitClose { application.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    fun close() {
        synchronized(publishing) {
            scope?.cancel()
            nearby.publish(MacPresence.ABSENT)
            macLock.forget()
        }
        clipboard.close()
        inputs?.close()
        scope = null
        inputs = null
        BridgeLog.info("companion window closed")
    }

    // Forgotten only once ABSENT is out: a lock-state checked against the presence before then is
    // cleared here, and one checked after is refused.
    private fun publish(window: CoroutineScope, presence: MacPresence) = synchronized(publishing) {
        if (window.isActive) {
            nearby.publish(presence)
            if (presence == MacPresence.ABSENT) macLock.forget()
        }
    }

    fun presence(associationId: Int, event: Int) {
        inputs?.trySend(WindowInput.Presence(associationId, event))
    }

    fun action(associationId: Int, action: Int, operation: Int) {
        inputs?.trySend(WindowInput.Action(associationId, action, operation))
    }

    // One coroutine works through the inputs in order, so the machine, the debounce and the attendance
    // are only ever touched from here.
    private inner class WindowRun(private val scope: CoroutineScope, private val inputs: Channel<WindowInput>) {

        private val machine = PresenceMachine()
        private var debounce: Job? = null
        private var attendance: Attendance? = null
        private var generation = 0

        suspend fun follow() {
            for (input in inputs) {
                val step = when (input) {
                    is WindowInput.Presence -> presenceStep(machine, input)
                    is WindowInput.Action -> act(input, attendance)
                    is WindowInput.Gatt -> gattStep(input)
                    is WindowInput.Expired -> machine.expired(input.token)
                    is WindowInput.Radio -> radio(input.on)
                    is WindowInput.AttendanceFailed -> failed(input.generation)
                }
                if (step != null) settle(step)
            }
        }

        // Not a lost connection, which would attend again at once while the Mac is still present: only
        // a new outside event does, so a failure that repeats cannot spin.
        private fun failed(generation: Int): PresenceStep? {
            if (generation != attendance?.generation) return null
            BridgeLog.info("attendance failed, the Mac counts as absent")
            return machine.on(PresenceInput.ATTENDANCE_FAILED)
        }

        private fun radio(on: Boolean): PresenceStep {
            val gatt = attendance?.gatt
            return if (on) {
                BridgeLog.info("bluetooth on, the gatt link starts over")
                gatt?.adapterOn()
                machine.on(PresenceInput.BLUETOOTH_ON)
            } else {
                BridgeLog.info("bluetooth off, the gatt link dropped")
                gatt?.adapterOff()
                machine.on(PresenceInput.BLUETOOTH_OFF)
            }
        }

        private fun gattStep(input: WindowInput.Gatt): PresenceStep? {
            if (input.generation != attendance?.generation) return null
            val wasLinked = machine.linked
            val step = machine.on(input.input)
            if (machine.linked && !wasLinked) BridgeLog.info("gatt link authenticated by a frame from the Mac")
            return step
        }

        private fun settle(step: PresenceStep) {
            order(step.debounce)
            val attended = attendance
            if (step.present && attended == null) {
                attendance = attend(scope, inputs, ++generation)
            } else if (!step.present && attended != null) {
                val ended = attended.job.isCompleted
                attended.job.cancel()
                clipboard.absent()
                attendance = null
                BridgeLog.info(if (ended) "Mac absent, its attendance had already ended" else "Mac absent")
            }
            publish(
                scope,
                when {
                    !step.present -> MacPresence.ABSENT
                    machine.linked -> MacPresence.LINKED
                    else -> MacPresence.NEARBY
                },
            )
        }

        private fun order(order: DebounceOrder) {
            when (order) {
                is DebounceOrder.Arm -> {
                    debounce?.cancel()
                    debounce = scope.launch {
                        delay(PRESENCE_DEBOUNCE_MS)
                        inputs.send(WindowInput.Expired(order.token))
                    }
                    BridgeLog.event("presence debounce armed")
                }
                DebounceOrder.Cancel -> {
                    debounce?.cancel()
                    debounce = null
                    BridgeLog.event("presence debounce cancelled")
                }
                DebounceOrder.Keep -> Unit
            }
        }
    }

    private suspend fun presenceStep(machine: PresenceMachine, input: WindowInput.Presence): PresenceStep? {
        val name = presenceEventName(input.event)
        if (pairedAssociation()?.id != input.associationId) {
            BridgeLog.event("presence $name for association ${input.associationId}, not ours, ignored")
            return null
        }
        BridgeLog.event("presence $name")
        return when (input.event) {
            DevicePresenceEvent.EVENT_BLE_APPEARED -> machine.on(PresenceInput.CDM_APPEARED)
            DevicePresenceEvent.EVENT_BLE_DISAPPEARED -> machine.on(PresenceInput.CDM_DISAPPEARED)
            else -> null
        }
    }

    // Scanning and advertising requests do not apply, as the phone does neither. A transport request
    // makes sure a connection attempt is under way; its deactivation never takes down a link that is
    // up, because the system cycles it every few seconds and following it would churn the link.
    private fun act(input: WindowInput.Action, attendance: Attendance?): PresenceStep? {
        BridgeLog.event(
            "action requested ${actionName(input.action)} ${operationName(input.operation)} " +
                "association=${input.associationId}",
        )
        if (input.action == ActionRequest.REQUEST_TRANSPORT && input.operation == ActionRequest.OP_ACTIVATE) {
            val gatt = attendance?.gatt
            if (gatt == null) {
                BridgeLog.event("transport requested with no link to keep, nothing to do")
            } else {
                gatt.ensure()
            }
        }
        return null
    }

    private fun attend(scope: CoroutineScope, inputs: SendChannel<WindowInput>, generation: Int): Attendance {
        BridgeLog.info("Mac present")
        val attendance = Attendance(generation)
        attendance.job = scope.launch {
            listener.holdForBridge { attended(attendance, inputs) }
        }
        attendance.job.invokeOnCompletion { cause ->
            if (cause != null && cause !is CancellationException) {
                inputs.trySend(WindowInput.AttendanceFailed(generation))
            }
        }
        return attendance
    }

    // The key is opened for the time the Mac is present and wiped once every child of the scope
    // that could seal or open with it has finished.
    private suspend fun attended(attendance: Attendance, inputs: SendChannel<WindowInput>) {
        val association = pairedAssociation()
        val pairing = association?.let { config.openPairing() }
        if (association == null || pairing == null) {
            BridgeLog.info("companion window has no usable pairing")
            return
        }
        try {
            coroutineScope {
                val frames = Channel<ByteArray>(Channel.UNLIMITED)
                val subscriptions = Channel<Unit>(Channel.CONFLATED)
                val gatt = gattFor(association) { event ->
                    when (event) {
                        GattEvent.Connected -> attendance.report(inputs, PresenceInput.GATT_CONNECTED)
                        GattEvent.Disconnected -> attendance.report(inputs, PresenceInput.GATT_DISCONNECTED)
                        GattEvent.Subscribed -> subscriptions.trySend(Unit)
                        is GattEvent.Frame -> frames.trySend(event.bytes)
                    }
                }
                attendance.gatt = gatt
                gatt?.let(carriers::attach)
                val visits = this
                val sender = WindowSender(pairing, gatt, visits)
                val mirror = WindowMirror(
                    listener = listener,
                    rules = rules,
                    sessionUp = { link.hasSession },
                    ring = sender::ring,
                    sendSignal = sender::send,
                    deliverReply = replies::deliver,
                )
                launch { mirror.run() }
                launch {
                    for (frame in frames) {
                        if (!receive(pairing, frame, mirror, visits)) continue
                        attendance.report(inputs, PresenceInput.GATT_AUTHENTICATED)
                    }
                }
                launch {
                    gattPings(subscriptions.receiveAsFlow(), link.sessionsStarted).collect { sender.ping(it) }
                }
                sender.lockRoute?.let(lockMac::attach)
                gatt?.start()
                link.summon(visits, "presence")
                try {
                    awaitCancellation()
                } finally {
                    sender.lockRoute?.let(lockMac::detach)
                    attendance.gatt = null
                    gatt?.let(carriers::detach)
                    gatt?.close()
                    frames.close()
                    subscriptions.close()
                }
            }
        } finally {
            synchronized(pairing) { pairing.bleKey.fill(0) }
        }
    }

    // A signal is the session's while one is up; without one the window carries it out itself. True
    // when the frame opened: whatever it carries, it came from the Mac. The Mac's ping says only that.
    private suspend fun receive(
        pairing: OpenedPairing,
        frame: ByteArray,
        mirror: WindowMirror,
        visits: CoroutineScope,
    ): Boolean {
        val opened = guard.open(pairing, frame)
        if (opened !is BleOpened.Accepted) {
            logDropped(opened, VIA_GATT)
            return false
        }
        when (val signal = inboundSignal(opened.frame, VIA_GATT)) {
            null, BleSignal.Ping -> Unit
            is BleSignal.LockState -> macLock.received(pairing, opened.frame.seq, signal.locked)
            else -> if (!link.dispatchToSession(signal)) act(signal, mirror, visits)
        }
        return true
    }

    private suspend fun act(signal: BleSignal, mirror: WindowMirror, visits: CoroutineScope) {
        when (signal) {
            BleSignal.SyncRequest -> link.summon(visits, "sync-request")
            is BleSignal.Reply ->
                if (signal.text == null) link.summon(visits, "reply-text") else mirror.requestReply(signal)
            is BleSignal.Dismiss -> mirror.requestDismiss(signal)
            is BleSignal.ClipboardSmall -> clipboard.clip(signal.text, signal.sensitive, signal.ageMs)
            BleSignal.ClipboardPending -> clipboard.pending {
                if (visits.isActive) link.summon(visits, "clipboard-pending")
            }
            else -> Unit
        }
    }

    private fun gattFor(association: BridgeAssociation, events: (GattEvent) -> Unit): BridgeGattLink? {
        val adapter = application.getSystemService(BluetoothManager::class.java)?.adapter
        val address = association.address
        val device = if (adapter != null && address != null) {
            BridgeGattLink.deviceOf(adapter, address, association.addressType)
        } else {
            null
        }
        return when {
            !bluetoothConnectGranted(application) -> {
                BridgeLog.info("no nearby devices access, the GATT link stays off")
                null
            }
            adapter == null -> {
                BridgeLog.info("no Bluetooth adapter, the GATT link stays off")
                null
            }
            device == null -> {
                BridgeLog.info("the association has no usable address, the GATT link stays off")
                null
            }
            else -> BridgeGattLink(adapter, device, events)
        }
    }

    private suspend fun pairedAssociation(): BridgeAssociation? {
        val settings = config.settings()
        return settings.pairing?.association?.takeIf { settings.enabled }
    }

    // Signals of the window go over GATT; a session that is up carries them instead, and a doorbell
    // that cannot ring over GATT summons a visit, whose opening active-set tells the Mac the same.
    private inner class WindowSender(
        private val pairing: OpenedPairing,
        private val gatt: BridgeGattLink?,
        private val visits: CoroutineScope,
    ) {

        suspend fun ring(signal: BleSignal): Boolean {
            if (send(signal)) return true
            link.summon(visits, "doorbell")
            return false
        }

        private val gattSender = gatt?.let { carrier ->
            GattSender(
                fits = carrier::fits,
                reserve = { config.reserveOutgoingSeq(pairing) },
                seal = { signal, seq, plaintext ->
                    synchronized(pairing) {
                        BleFrameCodec.seal(pairing.bleKey, BleDirection.PHONE_TO_MAC, signal.type.code, seq, plaintext)
                    }
                },
                write = carrier::write,
            )
        }

        suspend fun send(signal: BleSignal): Boolean = if (link.hasSession) link.send(signal) else overGatt(signal)

        // A caller from outside the window writes as a child of the attendance, whose key is wiped only
        // once its children have finished; a window that closed first has sent nothing.
        val lockRoute: GattSignalRoute? = gatt?.let {
            GattSignalRoute { signal ->
                val sending = visits.async { overGatt(signal) }
                sending.join()
                !sending.isCancelled && sending.await()
            }
        }

        // Always over GATT, session or not: only a frame this subscription wrote itself confirms it on
        // the Mac, so a ping that got lost is made up for by the next doorbell or session start.
        suspend fun ping(reason: String) {
            if (!overGatt(BleSignal.Ping)) BridgeLog.event("gatt ping after the $reason not sent")
        }

        private suspend fun overGatt(signal: BleSignal): Boolean {
            val sent = gattSender?.send(signal) ?: return false
            BridgeLog.event(
                "send signal ${signal.type.wireName} seq=${sent.seq} k=${signalKeyHash(signal) ?: "-"} " +
                    "via=$VIA_GATT ${sent.written.name.lowercase()}",
            )
            return sent.written == GattWrite.SENT
        }
    }

    private companion object {
        const val VIA_GATT = "gatt"
        val RADIO_CHANGES = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
    }
}

// The phone pings after every subscription it makes and at every session start after the window
// opened; the session counter's current value is from before.
internal fun gattPings(subscriptions: Flow<Unit>, sessionsStarted: Flow<Long>): Flow<String> = merge(
    subscriptions.map { "subscription" },
    sessionsStarted.drop(1).map { "session start" },
)

private fun presenceEventName(event: Int): String = when (event) {
    DevicePresenceEvent.EVENT_BLE_APPEARED -> "ble-appeared"
    DevicePresenceEvent.EVENT_BLE_DISAPPEARED -> "ble-disappeared"
    DevicePresenceEvent.EVENT_ASSOCIATION_REMOVED -> "association-removed"
    else -> "event-$event"
}

private fun actionName(action: Int): String = when (action) {
    ActionRequest.REQUEST_NEARBY_SCANNING -> "nearby-scanning"
    ActionRequest.REQUEST_NEARBY_ADVERTISING -> "nearby-advertising"
    ActionRequest.REQUEST_TRANSPORT -> "transport"
    else -> "action-$action"
}

private fun operationName(operation: Int): String = when (operation) {
    ActionRequest.OP_ACTIVATE -> "activate"
    ActionRequest.OP_DEACTIVATE -> "deactivate"
    else -> "op-$operation"
}
