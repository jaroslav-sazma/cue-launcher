package app.cue.launcher.system.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.cue.launcher.CueApplication
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.di.AppGraph
import app.cue.launcher.engine.NotificationSnapshot
import app.cue.launcher.engine.PostedNotification
import app.cue.launcher.engine.following
import app.cue.launcher.engine.notificationEndCandidates
import app.cue.launcher.engine.notificationListeners
import app.cue.launcher.engine.notificationPackages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class CueNotificationListener : NotificationListenerService() {

    private val graph: AppGraph?
        get() = (applicationContext as? CueApplication)?.graph

    private val link: NotificationListenerLink?
        get() = graph?.notificationListener

    private var connection: Connection? = null

    private val badges: NotificationBadgeTracker? by lazy {
        graph?.let { graph ->
            NotificationBadgeTracker(
                scope = graph.processScope,
                profiles = graph.userProfiles,
                active = ::activeOrEmpty,
                rankings = { currentRanking },
                publish = { counts -> link?.publishBadges(this, counts) },
            )
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val graph = graph ?: return
        val since = graph.notificationListener.onConnected(this) ?: return
        val previous = connection?.also { it.close() }
        connection = Connection(graph).also { it.open(since, after = previous?.drained) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        link?.onDisconnected(this)
        connection?.close()
        connection = null
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        super.onNotificationRankingUpdate(rankingMap)
        if (current) badges?.ranked()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !current) return
        badges?.posted(sbn)
        connection?.events?.trySend(Event.Posted(sbn))
    }

    // The overload with the reason is the one the system calls; the shorter ones only exist for it to
    // forward to.
    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        if (sbn == null || !current) return
        badges?.removed(sbn)
        connection?.events?.trySend(Event.Removed(sbn, reason, at = System.currentTimeMillis()))
    }

    private val current: Boolean
        get() = link?.isCurrent(this) == true

    private sealed interface Event {
        class Posted(val sbn: StatusBarNotification) : Event
        class Removed(val sbn: StatusBarNotification, val reason: Int, val at: Long) : Event
        class Watch(val scenarios: List<Scenario>) : Event
    }

    // One per binding. The callbacks only queue what happened, and a single coroutine works through
    // the queue in order, off the main thread: the full read at the connect, the replay, every post
    // and removal, and the switch to another set of watched apps. Nothing it keeps is touched from
    // anywhere else, so the order the system reported things in is the order they are judged in. A
    // closed connection still works off what it had queued, and the next one waits for that.
    private inner class Connection(private val graph: AppGraph) {

        val events = Channel<Event>(Channel.UNLIMITED)

        var drained: Job? = null
            private set

        private var watchers: Job? = null

        // What is posted from the apps a scenario listens to, as the routing reads it, so a removal
        // looks here for what remains instead of copying every posted notification — pictures
        // included — across the binder each time. Until the scenarios are known it holds every app.
        private val posted = HashMap<String, PostedNotification>()

        private var watched: Watched? = null

        fun open(since: Long, after: Job?) {
            watchers = graph.processScope.launch {
                launch { graph.scenarioRepository.observeScenarios().collect { events.trySend(Event.Watch(it)) } }
                graph.settingsRepository.settings
                    .map { it.appearance.notificationBadges }
                    .distinctUntilChanged()
                    .collect { wanted -> badges?.want(wanted) }
            }
            drained = graph.processScope.launch {
                after?.join()
                guarded { replay(since) }
                for (event in events) {
                    guarded {
                        when (event) {
                            is Event.Posted -> posted(event.sbn)
                            is Event.Removed -> removed(event.sbn, event.reason, event.at)
                            is Event.Watch -> watch(event.scenarios)
                        }
                    }
                }
            }
        }

        fun close() {
            events.close()
            watchers?.cancel()
            badges?.want(false)
        }

        // One notification an app built in a way nothing here expects must not stop every tile after
        // it from firing or ending.
        @Suppress("TooGenericExceptionCaught")
        private suspend fun guarded(step: suspend () -> Unit) {
            try {
                step()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(NOTIFICATION_LISTENER_TAG, "A notification event could not be handled", failure)
            }
        }

        private suspend fun replay(since: Long) {
            val active = activeOrNull() ?: return
            active.forEach { posted[it.key] = postedNotificationOf(it) }
            val bySbn = active.associateBy(StatusBarNotification::getKey)
            graph.conditionMonitor.reconcileNotifications(since) {
                // A listener released meanwhile reads nothing, which must not pass for a cleared shade.
                if (!current) return@reconcileNotifications null
                NotificationSnapshot(posted.values.toList()) { notification ->
                    bySbn[notification.key]?.let { notification.withTileData(this@CueNotificationListener, it) }
                        ?: notification
                }
            }
        }

        private suspend fun posted(sbn: StatusBarNotification) {
            val known = watched
            if (known?.listensTo(sbn.packageName) == false) return
            val notification = postedNotificationOf(sbn).following(posted[sbn.key])
            val seen = posted.put(sbn.key, notification) != null
            val routing = notificationRouting(known?.scenarios) { scenarios ->
                notificationListeners(scenarios, notification).isNotEmpty()
            }
            if (routing == NotificationRouting.MATCHED && !seen) {
                Log.i(NOTIFICATION_LISTENER_TAG, "Notification from ${notification.packageName} goes to a trigger")
            }
            if (routing != NotificationRouting.IGNORED) {
                val forTile = notification.withTileData(this@CueNotificationListener, sbn)
                graph.conditionMonitor.onNotificationPosted(forTile, known?.scenarios)
            }
        }

        private suspend fun removed(sbn: StatusBarNotification, reason: Int, at: Long) {
            val known = watched
            if (known?.listensTo(sbn.packageName) == false) return
            val tracked = posted.remove(sbn.key)
            val removed = postedNotificationOf(sbn)
            val routing = notificationRouting(known?.scenarios) { scenarios ->
                notificationEndCandidates(scenarios, removed).isNotEmpty()
            }
            if (routing == NotificationRouting.MATCHED) {
                Log.i(NOTIFICATION_LISTENER_TAG, "Notification from ${removed.packageName} was cancelled")
            }
            if (routing != NotificationRouting.IGNORED) {
                graph.conditionMonitor.onNotificationRemoved(removed, posted.values.toList(), known?.scenarios)
            }
            if (endedByItsOwner(reason)) {
                graph.conditionMonitor.onNotificationEnded(removed.following(tracked), at, known?.scenarios)
            }
        }

        // Apps nobody listens to any more leave the index; an app a scenario starts listening to is
        // read in, for the notifications of it that are already posted.
        private fun watch(scenarios: List<Scenario>) {
            val previous = watched?.packages
            val next = Watched(scenarios, notificationPackages(scenarios))
            watched = next
            posted.values.removeAll { !next.listensTo(it.packageName) }
            val added = if (previous == null) emptySet() else next.packages - previous
            if (added.isEmpty()) return
            activeOrNull()
                ?.filter { next.listensTo(it.packageName) && it.packageName.lowercase() in added }
                ?.forEach { posted[it.key] = postedNotificationOf(it) }
        }
    }

    private class Watched(val scenarios: List<Scenario>, val packages: Set<String>) {
        fun listensTo(packageName: String): Boolean = packageName.lowercase() in packages
    }
}

internal enum class NotificationRouting { MATCHED, UNJUDGED, IGNORED }

// Right after binding the scenarios have not arrived yet, so every notification is passed on to be
// judged by the monitor; only a confirmed match may put its package in the log.
internal fun notificationRouting(
    watched: List<Scenario>?,
    matches: (List<Scenario>) -> Boolean,
): NotificationRouting = when {
    watched == null -> NotificationRouting.UNJUDGED
    matches(watched) -> NotificationRouting.MATCHED
    else -> NotificationRouting.IGNORED
}
