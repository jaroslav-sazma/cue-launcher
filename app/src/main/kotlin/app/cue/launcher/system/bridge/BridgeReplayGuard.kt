package app.cue.launcher.system.bridge

import app.cue.launcher.data.bridge.BridgeConfigRepository
import app.cue.launcher.data.bridge.OpenedPairing
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// The one replay check for frames from the Mac, whichever way they came: GATT and the session's
// signal message share one sequence and one window, so the check and the recording of the persisted
// window happen under one lock, and a frame that arrives both ways is acted on once.
@SingleIn(AppScope::class)
@Inject
class BridgeReplayGuard(private val config: BridgeConfigRepository) {

    private val lock = Mutex()

    // null when the pairing the frame was meant for is gone or has lost its sequence state.
    internal suspend fun open(pairing: OpenedPairing, frame: ByteArray): BleOpened? = lock.withLock {
        val window = config.incomingWindow(pairing) ?: return@withLock null
        val opened = synchronized(pairing) {
            BleFrameCodec.open(pairing.bleKey, BleDirection.MAC_TO_PHONE, frame, window)
        }
        when {
            opened !is BleOpened.Accepted -> opened
            config.recordIncomingSeq(pairing, opened.frame.seq) -> opened
            else -> BleOpened.Rejected(BleRejection.REPLAY)
        }
    }
}

// An accepted frame keeps its seq even when its content is useless, as the spec has it; only a
// signal the phone may receive is handed on.
internal fun inboundSignal(frame: BleFrame, via: String, log: (String) -> Unit = BridgeLog::event): BleSignal? {
    val signal = decodeBleSignal(frame.type, frame.plaintext)
        ?.takeIf { BleDirection.MAC_TO_PHONE in it.type.senders }
    if (signal == null) {
        log("recv signal type ${frame.type} seq=${frame.seq} unknown or invalid, ignored via=$via")
    } else {
        val hash = signalKeyHash(signal) ?: "-"
        log("recv signal ${signal.type.wireName} seq=${frame.seq} k=$hash via=$via")
    }
    return signal
}

internal fun logDropped(opened: BleOpened?, via: String, log: (String) -> Unit = BridgeLog::event) {
    when (opened) {
        null -> log("recv signal dropped: not paired via=$via")
        is BleOpened.Rejected -> log("recv signal dropped: ${opened.reason} via=$via")
        is BleOpened.Accepted -> Unit
    }
}
