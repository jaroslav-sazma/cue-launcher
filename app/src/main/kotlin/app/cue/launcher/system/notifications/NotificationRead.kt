package app.cue.launcher.system.notifications

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.cue.launcher.engine.PostedNotification

fun postedNotificationOf(notification: StatusBarNotification): PostedNotification {
    val posted = notification.notification
    val extras = posted.extras
    return PostedNotification(
        key = notification.key,
        packageName = notification.packageName,
        title = extras.text(Notification.EXTRA_TITLE, Notification.EXTRA_TITLE_BIG),
        text = extras.text(Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT),
        isGroupSummary = posted.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        postTime = notification.postTime,
        ongoing = notification.isOngoing,
    )
}

// What the app itself or the user took down. The rest is the system tidying up — an uninstall, a
// snooze, a channel switched off, a profile paused — and none of that means the thing has ended.
internal fun endedByItsOwner(reason: Int): Boolean = reason in OWNER_REASONS

private val OWNER_REASONS = setOf(
    NotificationListenerService.REASON_CLICK,
    NotificationListenerService.REASON_CANCEL,
    NotificationListenerService.REASON_CANCEL_ALL,
    NotificationListenerService.REASON_APP_CANCEL,
    NotificationListenerService.REASON_APP_CANCEL_ALL,
    NotificationListenerService.REASON_TIMEOUT,
)

private fun Bundle.text(vararg keys: String): String? = keys
    .firstNotNullOfOrNull { key -> getCharSequence(key)?.toString()?.takeIf(String::isNotBlank) }
