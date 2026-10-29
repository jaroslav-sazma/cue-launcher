package app.cue.launcher.system.bridge

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

// Where an established session hands what the Mac sends.
internal interface SessionInbox {

    suspend fun sync()

    suspend fun icon(hash: String)

    suspend fun replyText(key: String, text: String)

    suspend fun signal(signal: BleSignal)

    // With the frame's seq, which orders lock-states that passed the replay window out of order.
    suspend fun lockState(seq: Long, locked: Boolean)

    suspend fun clip(text: String, sensitive: Boolean, ageMs: Long?)
}

// The read loop of one established session and, for a visit, its one timer: bytes arriving and every
// message sent start it again, and its expiry ends the connection with bye, which the read then sees
// as the end of the stream. Bytes count, not frames, so a clip crawling over L2CAP is not cut off.
internal class SessionConversation(
    private val connection: BridgeConnection,
    private val open: suspend (ByteArray) -> BleOpened?,
    private val inbox: SessionInbox,
    private val idleCloseMs: Long?,
    private val log: (String) -> Unit = BridgeLog::event,
    private val info: (String) -> Unit = BridgeLog::info,
) {

    private val traffic = MutableStateFlow(0L)

    @Volatile
    var idled = false
        private set

    fun touch() {
        traffic.update { it + 1 }
    }

    fun send(message: BridgeMessage, binary: ByteArray? = null) {
        connection.send(message, binary)
        touch()
        log("send ${BridgeLog.describe(message)} conn=$CONNECTION")
    }

    suspend fun run(): SessionEnd = coroutineScope {
        connection.activity.follow(::touch)
        val idle = idleCloseMs?.let { millis -> launch { closeWhenIdle(millis) } }
        try {
            listen()
        } finally {
            idle?.cancel()
            connection.activity.follow(null)
        }
    }

    private suspend fun closeWhenIdle(millis: Long) {
        traffic.collectLatest {
            delay(millis)
            idled = true
            info("session idle, closing")
            try {
                val bye = BridgeMessage.Bye(BYE_IDLE)
                connection.send(bye)
                log("send ${BridgeLog.describe(bye)} conn=$CONNECTION")
            } catch (_: IOException) {
                log("send bye conn=$CONNECTION failed")
            }
            connection.close()
        }
    }

    private suspend fun listen(): SessionEnd {
        var end: SessionEnd? = null
        while (end == null) {
            end = when (val inbound = connection.read()) {
                null -> SessionEnd(BridgeLinkState.UNREACHABLE)
                is BridgeInbound.Unknown -> {
                    log("recv ${inbound.type} conn=$CONNECTION unknown type, ignored")
                    null
                }
                is BridgeInbound.Message -> {
                    log("recv ${BridgeLog.describe(inbound.message)} conn=$CONNECTION")
                    handle(inbound.message)
                }
            }
        }
        return end
    }

    private suspend fun handle(message: BridgeMessage): SessionEnd? = when (val step = sessionStep(message)) {
        is SessionStep.End -> step.end
        SessionStep.ProtocolError -> {
            info("protocol error: ${wireType(message)} on the session")
            SessionEnd(BridgeLinkState.UNREACHABLE)
        }
        else -> {
            deliver(step)
            null
        }
    }

    private suspend fun deliver(step: SessionStep) {
        when (step) {
            SessionStep.Sync -> inbox.sync()
            is SessionStep.Icon -> inbox.icon(step.hash)
            is SessionStep.Signal -> receiveSignal(step.frame)
            is SessionStep.Reply -> inbox.replyText(step.key, step.text)
            is SessionStep.Clipboard -> inbox.clip(step.text, step.sensitive, step.ageMs)
            is SessionStep.End, SessionStep.ProtocolError -> Unit
        }
    }

    private suspend fun receiveSignal(encoded: String) {
        val frame = runCatching { decodeBase64(encoded) }.getOrNull()
        if (frame == null) {
            log("recv signal dropped: not base64")
            return
        }
        val opened = open(frame)
        if (opened is BleOpened.Accepted) {
            when (val signal = inboundSignal(opened.frame, CONNECTION, log)) {
                null -> Unit
                is BleSignal.LockState -> inbox.lockState(opened.frame.seq, signal.locked)
                else -> inbox.signal(signal)
            }
        } else {
            logDropped(opened, CONNECTION, log)
        }
    }

    companion object {
        const val CONNECTION = "session"
    }
}
