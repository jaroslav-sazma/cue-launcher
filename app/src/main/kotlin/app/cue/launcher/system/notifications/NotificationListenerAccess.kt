package app.cue.launcher.system.notifications

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

fun notificationListenerComponent(context: Context): ComponentName =
    ComponentName(context.applicationContext, CueNotificationListener::class.java)

fun notificationListenerEnabled(context: Context): Boolean {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return false
    return manager.isNotificationListenerAccessGranted(notificationListenerComponent(context))
}

fun notificationListenerSettingsIntent(context: Context): Intent {
    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            notificationListenerComponent(context).flattenToString(),
        )
    val resolved = context.packageManager.resolveActivity(detail, 0) != null
    return if (resolved) detail else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}

fun NotificationListenerService.activeOrEmpty(): List<StatusBarNotification> = activeOrNull().orEmpty()

// null when the posted notifications cannot be read — denied, or the listener not bound — which is
// not the same as nothing posted.
fun NotificationListenerService.activeOrNull(): List<StatusBarNotification>? = try {
    activeNotifications?.toList()
} catch (denied: SecurityException) {

    Log.w(NOTIFICATION_LISTENER_TAG, "Posted notifications cannot be read", denied)
    null
}

fun requestNotificationListenerRebind(context: Context) {
    val component = notificationListenerComponent(context)
    try {
        NotificationListenerService.requestRebind(component)
        Log.i(NOTIFICATION_LISTENER_TAG, "Requesting a listener rebind")
    } catch (error: SecurityException) {

        Log.w(NOTIFICATION_LISTENER_TAG, "Rebind was denied", error)
    }
}

fun requestNotificationListenerUnbind(context: Context) {
    try {
        NotificationListenerService.requestUnbind(notificationListenerComponent(context))
    } catch (error: SecurityException) {

        Log.w(NOTIFICATION_LISTENER_TAG, "Unbind was denied", error)
    }
}

internal const val NOTIFICATION_LISTENER_TAG = "CueNotifications"
