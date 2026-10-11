package app.cue.launcher.system.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.os.UserHandle
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import app.cue.launcher.data.notifications.BadgeCandidate
import app.cue.launcher.data.notifications.BadgeKey

internal data class PostedBadge(
    val key: BadgeKey,
    val number: Int,
    val isGroupSummary: Boolean,
    val hasContent: Boolean,
    val ongoing: Boolean,
)

internal fun postedBadgeOf(sbn: StatusBarNotification, serialOf: (UserHandle) -> Long): PostedBadge {
    val notification = sbn.notification
    val extras = notification.extras
    return PostedBadge(
        key = BadgeKey(sbn.packageName, serialOf(sbn.user)),
        number = notification.number,
        isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        hasContent = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT)
            .any { !extras.getCharSequence(it).isNullOrBlank() },
        ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
    )
}

// canShowBadge already folds in everything the system decides: the global dots setting, the app
// and channel switches, Do Not Disturb hiding badges, suppressed bubbles and media notifications.
internal fun PostedBadge.candidate(ranking: Ranking?): BadgeCandidate = BadgeCandidate(
    key = key,
    number = number,
    canShowBadge = ranking?.canShowBadge() ?: true,
    isGroupSummary = isGroupSummary,
    hasContent = hasContent,
    ongoingOnDefaultChannel = ongoing && ranking?.channel?.id == NotificationChannel.DEFAULT_CHANNEL_ID,
)
