package app.cue.launcher.data.scenario

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
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
    // The end event of the condition that fired, as it was then, so editing the scenario does not
    // change how a running activation ends. Left out when null, so other rows keep their old shape.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val until: EndEvent? = null,
    // When the trigger last fired: a re-fire keeps the activation's since, so the queue order holds,
    // yet an end event is judged against this. Null for an activation no event carried.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val firedAt: Long? = null,
) {

    val isEmpty: Boolean
        get() = icon == null &&
            fields.isEmpty() &&
            progress == null &&
            semantic == SemanticStyle.UNSPECIFIED &&
            reply == null &&
            notificationKey == null &&
            until == null &&
            firedAt == null

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
