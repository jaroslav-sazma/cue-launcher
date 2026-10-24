package app.cue.launcher.data.scenario

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Speaks the condition vocabulary, yet is a type of its own, so an end event can carry neither a
// duration nor extract rules nor an end event of its own.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface EndEvent {

    @Serializable
    @SerialName("time_instant")
    data class AtTime(
        val at: String,
        val days: List<Int> = ALL_WEEK_DAYS,
    ) : EndEvent

    @Serializable
    @SerialName("alarm")
    data object Alarm : EndEvent

    @Serializable
    @SerialName("bt_disconnected")
    data class BtDisconnected(val mac: String? = null) : EndEvent

    @Serializable
    @SerialName("notification")
    data class Notification(
        @SerialName("package") val packageName: String,
        val contains: String? = null,
    ) : EndEvent

    @Serializable
    @SerialName("ha_event")
    data class HaEvent(val code: String) : EndEvent
}

enum class EndEventType(val condition: ConditionType) {
    TIME_INSTANT(ConditionType.TIME_INSTANT),
    ALARM(ConditionType.ALARM),
    BT_DISCONNECTED(ConditionType.BT_DISCONNECTED),
    NOTIFICATION(ConditionType.NOTIFICATION),
    HA_EVENT(ConditionType.HA_EVENT),
    ;

    val jsonName: String get() = condition.jsonName

    companion object {
        fun fromJsonName(jsonName: String): EndEventType? = entries.firstOrNull { it.jsonName == jsonName }
    }
}

// A missing type reads as an empty name. An until that is not an object is left to the decoder,
// which rejects the scenario as unreadable.
internal fun unknownUntilType(condition: JsonObject): String? {
    val until = condition[FIELD_UNTIL] as? JsonObject ?: return null
    val type = (until[FIELD_TYPE] as? JsonPrimitive)?.takeIf { it.isString }?.content
    return if (type != null && EndEventType.fromJsonName(type) != null) null else type.orEmpty()
}
