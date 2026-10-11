package app.cue.launcher.system.notifications

import android.os.UserHandle
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import app.cue.launcher.data.notifications.BadgeKey
import app.cue.launcher.data.notifications.badgeCounts
import app.cue.launcher.system.UserProfiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Shared rather than per instance: a rebind creates a new listener, and the old one's final clear
// must not land after the new one's first counts.
private val badgeWorker = Dispatchers.Default.limitedParallelism(1)

// Everything runs in order on one worker: reading the posted notifications back copies all of
// them, pictures included, across the binder, and a listener bound at process start would do that
// on the frame the home screen is drawing. One queue keeps a post that lands during a reload from
// being overwritten by the older snapshot. Only switching badges on and a fresh bind read them
// back; a post, a removal or a ranking change (Do Not Disturb, a channel switch) is applied to what
// is already held, because a progress notification is reposted every second or so.
internal class NotificationBadgeTracker(
    private val scope: CoroutineScope,
    private val profiles: UserProfiles,
    private val active: () -> List<StatusBarNotification>,
    private val rankings: () -> RankingMap?,
    private val publish: (Map<BadgeKey, Int>) -> Unit,
) {

    private val sources = mutableMapOf<String, PostedBadge>()

    private val serials = mutableMapOf<UserHandle, Long>()

    // Read off the worker too, so a post with the badges off is dropped without a dispatch.
    @Volatile
    private var wanted = false

    fun posted(sbn: StatusBarNotification) {
        if (!wanted) return
        onWorker {
            if (!wanted) return@onWorker
            sources[sbn.key] = postedBadgeOf(sbn, ::serialOf)
            publish()
        }
    }

    fun removed(sbn: StatusBarNotification) {
        if (!wanted) return
        onWorker { if (sources.remove(sbn.key) != null) publish() }
    }

    fun ranked() {
        if (!wanted) return
        onWorker { if (sources.isNotEmpty()) publish() }
    }

    fun want(wanted: Boolean) = onWorker {
        this.wanted = wanted
        sources.clear()
        if (wanted) active().associateTo(sources) { sbn -> sbn.key to postedBadgeOf(sbn, ::serialOf) }
        publish()
    }

    private fun onWorker(block: () -> Unit) {
        scope.launch(badgeWorker) { block() }
    }

    // getSerialNumberForUser is a binder call; a profile's serial never changes while it exists.
    private fun serialOf(user: UserHandle): Long = serials.getOrPut(user) { profiles.serialOf(user) }

    private fun publish() {
        val map = rankings()
        val ranking = Ranking()
        val candidates = sources.map { (key, posted) ->
            posted.candidate(ranking.takeIf { map?.getRanking(key, it) == true })
        }
        publish(badgeCounts(candidates))
    }
}
