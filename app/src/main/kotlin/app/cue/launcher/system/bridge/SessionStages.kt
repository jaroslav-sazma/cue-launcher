package app.cue.launcher.system.bridge

import app.cue.launcher.data.bridge.BridgeSettings

// reached: the session got past the hello exchange before it ended. onSummon: the link is not
// restored on its own, only when a summon asks for a connection anew.
internal data class SessionEnd(
    val state: BridgeLinkState,
    val stable: Boolean = false,
    val final: Boolean = false,
    val reached: Boolean = false,
    val onSummon: Boolean = false,
)

// The Mac reads the switch from every session's hello and keeps the last one between sessions: with
// it off, it neither polls its pasteboard nor sends a clip.
internal fun sessionHello(settings: BridgeSettings): BridgeMessage.Hello =
    BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION, clipboardFromMac = settings.clipboardFromMac)

internal sealed interface SessionOpening {

    data object Established : SessionOpening

    data class Refused(val end: SessionEnd, val bye: BridgeMessage.Bye? = null) : SessionOpening
}

// The first frame on a session connection has to be the Mac's hello; anything else, the end of the
// stream included, ends the attempt as if the Mac were not there.
internal fun sessionOpening(first: BridgeInbound?): SessionOpening {
    val message = (first as? BridgeInbound.Message)?.message
    return when {
        message !is BridgeMessage.Hello -> SessionOpening.Refused(SessionEnd(BridgeLinkState.UNREACHABLE))
        message.v != BRIDGE_PROTOCOL_VERSION -> SessionOpening.Refused(
            SessionEnd(BridgeLinkState.VERSION_MISMATCH, final = true),
            BridgeMessage.Bye(BYE_VERSION_MISMATCH),
        )
        else -> SessionOpening.Established
    }
}

internal sealed interface SessionStep {

    data object Sync : SessionStep

    data class Icon(val hash: String) : SessionStep

    data class Signal(val frame: String) : SessionStep

    data class Reply(val key: String, val text: String) : SessionStep

    // announce is for a receiver that shows notices, and the phone shows none.
    data class Clipboard(val text: String, val sensitive: Boolean, val ageMs: Long? = null) : SessionStep

    data class End(val end: SessionEnd) : SessionStep

    data object ProtocolError : SessionStep
}

internal fun sessionStep(message: BridgeMessage): SessionStep = when (message) {
    BridgeMessage.SyncRequest -> SessionStep.Sync
    is BridgeMessage.IconGet -> SessionStep.Icon(message.hash)
    is BridgeMessage.Signal -> SessionStep.Signal(message.frame)
    is BridgeMessage.Bye -> SessionStep.End(byeEnd(message.reason))
    is BridgeMessage.ReplyText -> SessionStep.Reply(message.key, message.text)
    is BridgeMessage.ClipboardSet -> SessionStep.Clipboard(message.text, message.sensitive, message.ageMs)
    else -> SessionStep.ProtocolError
}

// Another version waits for an update. A replaced link is not brought back on its own, which would
// replace the newer connection in turn, but on the next summon: the newer one may well be gone by
// then, a Send to Mac push or a visit that closed.
internal fun byeEnd(reason: String?): SessionEnd = when (reason) {
    BYE_VERSION_MISMATCH -> SessionEnd(BridgeLinkState.VERSION_MISMATCH, final = true)
    BYE_REPLACED -> SessionEnd(BridgeLinkState.REPLACED, onSummon = true)
    else -> SessionEnd(BridgeLinkState.UNREACHABLE)
}

// A session has to outlast the longest backoff before a drop counts as fresh trouble again;
// otherwise a Mac that accepts and closes straight away would be hammered once a second.
internal fun sessionStable(elapsedNanos: Long): Boolean = elapsedNanos >= SESSION_MAX_BACKOFF_MS * NANOS_PER_MILLI

internal sealed interface SessionRetry {

    data class After(val millis: Long) : SessionRetry

    data object OnSummon : SessionRetry

    data object Never : SessionRetry
}

internal class SessionBackoff {

    private var next = SESSION_INITIAL_BACKOFF_MS

    // A link woken by a summon starts the backoff over: it was up until it was replaced.
    fun after(end: SessionEnd): SessionRetry {
        if (end.stable || end.onSummon) next = SESSION_INITIAL_BACKOFF_MS
        return when {
            end.final -> SessionRetry.Never
            end.onSummon -> SessionRetry.OnSummon
            else -> SessionRetry.After(next).also { next = (next * 2).coerceAtMost(SESSION_MAX_BACKOFF_MS) }
        }
    }
}

internal const val SESSION_INITIAL_BACKOFF_MS = 1_000L
internal const val SESSION_MAX_BACKOFF_MS = 60_000L

// Only a session the companion window opened in the background closes on its own.
internal const val SESSION_IDLE_CLOSE_MS = 10_000L
private const val NANOS_PER_MILLI = 1_000_000L
