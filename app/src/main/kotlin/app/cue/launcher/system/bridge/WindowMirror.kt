package app.cue.launcher.system.bridge

import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import app.cue.launcher.system.notifications.NotificationListenerLink
import app.cue.launcher.system.notifications.NotificationMirrorSink
import app.cue.launcher.system.notifications.ReplyDelivery
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// The mirror of the companion window, for the time no session is up: it rings the doorbells a
// session's mirror would ring, and carries out the Mac's dismissals and short replies. Its ledger
// starts from what the shade shows when the listener binds, so only a removal of something the Mac
// can mirror rings; while a session is up its ledger keeps count, and that session's mirror rings.
// The Mac's commands resolve through that ledger too, so they reach nothing the filter keeps back.
@Suppress("LongParameterList")
internal class WindowMirror(
    private val listener: NotificationListenerLink,
    private val rules: MirrorRules,
    private val sessionUp: () -> Boolean,
    private val ring: suspend (BleSignal) -> Boolean,
    sendSignal: suspend (BleSignal) -> Boolean,
    deliverReply: suspend (key: String, text: String) -> ReplyDelivery,
) : NotificationMirrorSink {

    private sealed interface Event {
        class Posted(val sbn: StatusBarNotification, val rankings: RankingMap?) : Event
        class Removed(val key: String) : Event
        data object Seed : Event
        class Dismiss(val keyHash: String, val revision: Long?) : Event
        class Reply(val signal: BleSignal.Reply) : Event
        class Exclusions(val packages: Set<String>) : Event
    }

    private val events = Channel<Event>(Channel.UNLIMITED)

    private val ledger = MirrorLedger()

    private var excluded = emptySet<String>()

    private val commands = MirrorCommands(
        ledger::keyOf,
        listener::shadeRevisionOf,
        ledger::tombstone,
        listener::cancel,
        deliverReply,
        sendSignal,
    )

    override fun posted(sbn: StatusBarNotification, rankings: RankingMap?) {
        events.trySend(Event.Posted(sbn, rankings))
    }

    override fun removed(sbn: StatusBarNotification) {
        events.trySend(Event.Removed(sbn.key))
    }

    fun requestDismiss(signal: BleSignal.Dismiss) {
        events.trySend(Event.Dismiss(signal.keyHash, signal.revision))
    }

    fun requestReply(signal: BleSignal.Reply) {
        events.trySend(Event.Reply(signal))
    }

    suspend fun run() = listener.mirrorInto(this) {
        excluded = rules.exclusions.first()
        coroutineScope {
            launch { rules.exclusions.collect { events.trySend(Event.Exclusions(it)) } }
            launch { listener.state.mirrorSyncTriggers().collect { events.trySend(Event.Seed) } }
            for (event in events) guarded { handle(event) }
        }
    }

    private suspend fun handle(event: Event) {
        when (event) {
            is Event.Posted -> rings(ledger.posted(event.sbn.key, passes(event.sbn, event.rankings)))
            is Event.Removed -> rings(ledger.removed(event.key))
            Event.Seed -> seed()
            is Event.Dismiss -> commands.dismiss(event.keyHash, event.revision)
            is Event.Reply -> commands.reply(event.signal)
            is Event.Exclusions -> if (event.packages != excluded) {
                excluded = event.packages
                seed()
            }
        }
    }

    private suspend fun rings(signal: BleSignal?) {
        if (signal == null || sessionUp()) return
        if (!ring(signal)) BridgeLog.event("window ${signal.type.wireName} k=${signalKeyHash(signal)} not sent")
    }

    private fun seed() {
        val keys = when (mirrorSyncOf(listener.state.value)) {
            MirrorSync.SEND_EMPTY -> emptyList()
            MirrorSync.WAIT -> null
            MirrorSync.READ_SHADE -> listener.readShade { active, ranks ->
                active.filter { passes(it, ranks) }.map { it.key }
            }
        } ?: return
        ledger.reset(keys)
        BridgeLog.event("window ledger seeded count=${keys.size}")
    }

    private fun passes(sbn: StatusBarNotification, rankings: RankingMap?): Boolean =
        rules.passes(sbn, rankings, excluded)
}
