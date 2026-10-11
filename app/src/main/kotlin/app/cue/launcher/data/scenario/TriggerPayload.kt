package app.cue.launcher.data.scenario

import kotlinx.serialization.Serializable

@Serializable
data class TriggerPayload(
    val icon: String? = null,
    val fields: Map<String, String> = emptyMap(),
    val progress: TileProgress? = null,
    val semantic: SemanticStyle = SemanticStyle.UNSPECIFIED,
    val reply: NotificationReply? = null,
    // The key of the notification that fired the activation, so one dismissed while the listener
    // was not bound can still end it.
    val notificationKey: String? = null,
) {

    val isEmpty: Boolean
        get() = icon == null &&
            fields.isEmpty() &&
            progress == null &&
            semantic == SemanticStyle.UNSPECIFIED &&
            reply == null &&
            notificationKey == null

    operator fun get(key: String): String? = fields[key]

    companion object {

        val EMPTY = TriggerPayload()
    }
}

internal fun TriggerPayload.encodeOrNull(): String? =
    if (isEmpty) null else scenarioJson.encodeToString(this)

internal fun triggerPayloadOf(json: String?): TriggerPayload {
    if (json.isNullOrEmpty()) return TriggerPayload.EMPTY
    return runCatching { scenarioJson.decodeFromString<TriggerPayload>(json) }
        .getOrDefault(TriggerPayload.EMPTY)
}
