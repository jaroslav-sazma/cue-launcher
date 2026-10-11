package app.cue.launcher.data.scenario

import kotlinx.serialization.Serializable

@Serializable
data class NotificationReply(
    val key: String,
    val hint: String? = null,
)
