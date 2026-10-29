package app.cue.launcher.system.bridge

import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import app.cue.launcher.system.notifications.NotificationListenerLink
import app.cue.launcher.system.notifications.NotificationMirrorSink
import app.cue.launcher.system.notifications.ReplyDelivery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

// The mirror of one session connection. The listener's callbacks, the Mac's requests and the
// listener's binding all only queue, and one coroutine works through the queue in order, so the
// ledger is never touched from two threads.
@Suppress("TooManyFunctions") // one way into the queue per kind of event, beside the steps that work it off
internal class BridgeMirror(
    private val listener: NotificationListenerLink,
    private val icons: BridgeAppIcons,
    private val rules: MirrorRules,
    private val sendMessage: (BridgeMessage, ByteArray?) -> Unit,
    private val sendSignal: suspend (BleSignal) -> Boolean,
    deliverReply: suspend (key: String, text: String) -> ReplyDelivery,
) : NotificationMirrorSink {

    private sealed interface Event {
        class Posted(val sbn: StatusBarNotification, val rankings: RankingMap?) : Event
        class Removed(val key: String) : Event
        data object Sync : Event
        class Icon(val hash: String) : Event
        class Dismiss(val keyHash: String, val revision: Long?) : Event
        class Reply(val signal: BleSignal.Reply) : Event
        class ReplyText(val key: String, val text: String) : Event
        class Exclusions(val packages: Set<String>) : Event
    }

    private class Entry(val detail: BridgeMessage.NotifDetail, val icon: AppIcon?)

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

    fun requestSync() {
        events.trySend(Event.Sync)
    }

    fun requestIcon(hash: String) {
        events.trySend(Event.Icon(hash))
    }

    fun requestDismiss(signal: BleSignal.Dismiss) {
        events.trySend(Event.Dismiss(signal.keyHash, signal.revision))
    }

    fun requestReply(signal: BleSignal.Reply) {
        events.trySend(Event.Reply(signal))
    }

    fun requestReplyText(key: String, text: String) {
        events.trySend(Event.ReplyText(key, text))
    }

    // The exclusions are read before the first set, so an app kept off the Mac is never in it.
    suspend fun run() = listener.mirrorInto(this) {
        excluded = rules.exclusions.first()
        coroutineScope {
            launch { rules.exclusions.collect { events.trySend(Event.Exclusions(it)) } }
            launch { listener.state.mirrorSyncTriggers().collect { events.trySend(Event.Sync) } }
            for (event in events) guarded { handle(event) }
        }
    }

    private suspend fun handle(event: Event) {
        when (event) {
            is Event.Posted -> {
                val key = event.sbn.key
                val skip = rules.skip(rules.candidate(event.sbn, event.rankings), excluded)
                if (skip != null) BridgeLog.event("mirror skip k=${notificationKeyHash(key)} reason=${skip.wire}")
                when (val news = ledger.news(key, passes = skip == null)) {
                    is PostNews.Content -> detail(event.sbn, event.rankings, news.keyHash)
                    is PostNews.Ring -> ring(news.signal)
                    null -> Unit
                }
            }
            is Event.Removed -> ring(ledger.removed(event.key))
            Event.Sync -> sync()
            is Event.Icon -> icon(event.hash)
            is Event.Dismiss -> commands.dismiss(event.keyHash, event.revision)
            is Event.Reply -> commands.reply(event.signal)
            is Event.ReplyText -> commands.replyText(event.key, event.text)
            is Event.Exclusions -> exclude(event.packages)
        }
    }

    // A change sends the whole set again, which takes a newly excluded app's notifications off the Mac
    // and brings back those of an app let through again.
    private fun exclude(packages: Set<String>) {
        if (packages == excluded) return
        excluded = packages
        sync()
    }

    private suspend fun ring(signal: BleSignal?) {
        if (signal != null && !sendSignal(signal)) {
            BridgeLog.event("mirror ${signal.type.wireName} k=${signalKeyHash(signal)} not sent")
        }
    }

    // A set read from the shade always goes out, whatever single notifications fail to read and
    // however many there are, as the Mac waits for it before it asks again.
    private fun sync() {
        val entries = when (mirrorSyncOf(listener.state.value)) {
            MirrorSync.SEND_EMPTY -> emptyList()
            MirrorSync.WAIT -> null
            MirrorSync.READ_SHADE -> listener.readShade { active, ranks -> active.mapNotNull { entry(it, ranks) } }
        }
        if (entries == null) {
            BridgeLog.event("active-set waits for the notification listener")
            return
        }
        val fitted = fitActiveSet(entries.map(Entry::detail))
        if (fitted.dropped > 0) BridgeLog.event("active-set leaves out the ${fitted.dropped} oldest to fit one frame")
        entries.forEach { entry -> entry.icon?.let { ledger.advertise(it.hash, it.png) } }
        sendMessage(fitted.set, null)
        ledger.reset(fitted.set.notifications.map { it.key })
    }

    // Once the set is out a post goes with its content, which spares the Mac asking for the whole
    // shade again. One that cannot be read rings instead, and the set the Mac then asks for decides.
    private suspend fun detail(sbn: StatusBarNotification, rankings: RankingMap?, keyHash: String) {
        val entry = entry(sbn, rankings)
        if (entry == null) {
            ring(BleSignal.NotifPosted(keyHash))
            return
        }
        entry.icon?.let { ledger.advertise(it.hash, it.png) }
        sendMessage(entry.detail, null)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun entry(sbn: StatusBarNotification, rankings: RankingMap?): Entry? = try {
        val candidate = rules.candidate(sbn, rankings)
        if (rules.skip(candidate, excluded) != null) {
            null
        } else {
            val face = icons.face(sbn.packageName)
            Entry(rules.detail(sbn, candidate, face), face.icon)
        }
    } catch (failure: Exception) {
        BridgeLog.event("active-set leaves out k=${notificationKeyHash(sbn.key)}: ${failure.javaClass.simpleName}")
        null
    }

    // A hash this connection never advertised gets no answer, as the spec has it.
    private fun icon(hash: String) {
        val png = ledger.icon(hash)
        if (png == null) {
            BridgeLog.event("icon-get ${BridgeLog.prefix(hash)} unknown, not answered")
        } else {
            sendMessage(BridgeMessage.IconData(hash, png.size), png)
        }
    }
}

internal fun NotificationListenerLink.shadeRevisionOf(key: String): Long? =
    readShade { active, _ -> active.firstOrNull { it.key == key }?.let(::mirrorRevisionOf) }

// One notification built in a way nothing here expects must not end the mirror; a dead connection
// does, and passes through.
@Suppress("TooGenericExceptionCaught")
internal suspend fun guarded(step: suspend () -> Unit) {
    try {
        step()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (lost: IOException) {
        throw lost
    } catch (failure: Exception) {
        BridgeLog.warn("a mirror event could not be handled", failure)
    }
}
