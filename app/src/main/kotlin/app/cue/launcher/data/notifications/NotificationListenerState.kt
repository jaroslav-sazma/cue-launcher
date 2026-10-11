package app.cue.launcher.data.notifications

data class NotificationListenerState(
    val enabled: Boolean = false,
    val connected: Boolean = false,
) {

    val usable: Boolean get() = enabled && connected
}

fun shouldRequestRebind(state: NotificationListenerState): Boolean = state.enabled && !state.connected
