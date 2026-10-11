package app.cue.launcher.feature.scenarios.builder

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import app.cue.launcher.engine.PostedNotification
import app.cue.launcher.system.notifications.NotificationListenerLink

@Stable
fun interface ActiveNotifications {

    // null while the listener is not bound, which is not the same as an app with nothing posted.
    fun of(packageName: String): List<PostedNotification>?

    fun reconnect() = Unit

    companion object {

        val NONE: ActiveNotifications = ActiveNotifications { null }
    }
}

fun activeNotificationsOf(link: NotificationListenerLink): ActiveNotifications = object : ActiveNotifications {
    // The listener may not be bound while no scenario needs it; picking a sample is reason enough.
    override fun of(packageName: String): List<PostedNotification>? {
        link.bindForSample()
        return link.activeNotifications(packageName)
    }

    override fun reconnect() = link.refresh()
}

val LocalActiveNotifications: ProvidableCompositionLocal<ActiveNotifications> =
    compositionLocalOf { ActiveNotifications.NONE }
