package app.cue.launcher.data.scenario

import app.cue.launcher.data.ha.haEntityIdOrNull
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Scenario(

    val id: String,
    val name: String,

    val enabled: Boolean = true,

    val priority: Int = 0,

    // Defaults are encoded, so without NEVER every file ever exported would grow a null field.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pinnedPosition: Int? = null,
    val size: TileSize,

    val match: ConditionMatch = ConditionMatch.ALL,
    val conditions: List<Condition> = emptyList(),
    val content: TileContent,
) {

    val isFallback: Boolean get() = conditions.isEmpty()

    val instantConditions: List<Condition.InstantCondition>
        get() = conditions.filterIsInstance<Condition.InstantCondition>()

    // Only ANY may hold more than one event condition, so ALL can keep reading the single one.
    val instantCondition: Condition.InstantCondition?
        get() = instantConditions.firstOrNull()

    val stateConditions: List<Condition.StateCondition>
        get() = conditions.filterIsInstance<Condition.StateCondition>()
}

@Serializable
enum class ConditionMatch {
    @SerialName("all")
    ALL,

    @SerialName("any")
    ANY,
}

const val SCENARIO_SCHEMA_VERSION = 1

private const val WEEK_FIRST_DAY = 1
private const val WEEK_LAST_DAY = 7

val ALL_WEEK_DAYS: List<Int> = (WEEK_FIRST_DAY..WEEK_LAST_DAY).toList()

val WEEK_DAYS: IntRange = WEEK_FIRST_DAY..WEEK_LAST_DAY

@Serializable
enum class TileSize(val jsonName: String) {
    @SerialName("1x1")
    GRID_1X1("1x1"),

    @SerialName("2x2")
    GRID_2X2("2x2"),

    @SerialName("4x2")
    HERO_4X2("4x2"),

    @SerialName("overlay")
    OVERLAY("overlay"),
    ;

    companion object {
        fun fromJsonName(jsonName: String): TileSize? = entries.firstOrNull { it.jsonName == jsonName }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface Condition {

    sealed interface StateCondition : Condition {

        val negate: Boolean
    }

    sealed interface InstantCondition : Condition {

        val durationSec: Int?

        val until: EndEvent?
    }

    @Serializable
    @SerialName("time_window")
    data class TimeWindow(
        val from: String,
        val to: String,
        val days: List<Int> = ALL_WEEK_DAYS,
        override val negate: Boolean = false,
    ) : StateCondition

    @Serializable
    @SerialName("bt_connected")
    data class BtConnected(
        val mac: String? = null,
        override val negate: Boolean = false,
    ) : StateCondition

    @Serializable
    @SerialName("wifi")
    data class Wifi(
        val mode: WifiMode,
        val ssid: String? = null,
        override val negate: Boolean = false,
    ) : StateCondition

    @Serializable
    @SerialName("charging")
    data class Charging(override val negate: Boolean = false) : StateCondition

    @Serializable
    @SerialName("audio_output")
    data class AudioOutput(override val negate: Boolean = false) : StateCondition

    @Serializable
    @SerialName("media_playing")
    data class MediaPlaying(
        override val negate: Boolean = false,

        // Empty means any app at all.
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val packages: List<String> = emptyList(),
    ) : StateCondition

    @Serializable
    @SerialName("ringer_mode")
    data class RingerMode(
        val mode: RingerModeState,
        override val negate: Boolean = false,
    ) : StateCondition

    @Serializable
    @SerialName("calendar_event")
    data class CalendarEvent(override val negate: Boolean = false) : StateCondition

    @Serializable
    @SerialName("ha_state")
    data class HaState(
        val entity: String,
        val states: List<String>,
        override val negate: Boolean = false,
    ) : StateCondition

    @Serializable
    @SerialName("mac_nearby")
    data class MacNearby(override val negate: Boolean = false) : StateCondition

    @Serializable
    @SerialName("mac_locked")
    data class MacLocked(override val negate: Boolean = false) : StateCondition

    @Serializable
    @SerialName("time_instant")
    data class TimeInstant(
        val at: String,
        val days: List<Int> = ALL_WEEK_DAYS,
        override val durationSec: Int? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val until: EndEvent? = null,
    ) : InstantCondition

    @Serializable
    @SerialName("alarm")
    data class Alarm(
        override val durationSec: Int? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val until: EndEvent? = null,
    ) : InstantCondition

    @Serializable
    @SerialName("bt_disconnected")
    data class BtDisconnected(
        val mac: String? = null,
        override val durationSec: Int? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val until: EndEvent? = null,
    ) : InstantCondition

    @Serializable
    @SerialName("notification")
    data class NotificationFrom(
        @SerialName("package") val packageName: String,
        val contains: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val extract: List<ExtractRule> = emptyList(),
        override val durationSec: Int? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val until: EndEvent? = null,
    ) : InstantCondition

    @Serializable
    @SerialName("ha_event")
    data class HaEvent(
        val code: String,
        override val durationSec: Int? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val until: EndEvent? = null,
    ) : InstantCondition

    @Serializable
    @SerialName("notification_ended")
    data class NotificationEnded(
        @SerialName("package") val packageName: String,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val ongoing: Boolean = false,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val minLastedSec: Int? = null,
        override val durationSec: Int? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val until: EndEvent? = null,
    ) : InstantCondition

    @Serializable
    @SerialName("place_saved")
    data class PlaceSaved(
        override val durationSec: Int? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        override val until: EndEvent? = null,
    ) : InstantCondition
}

fun Condition.StateCondition.withNegate(negate: Boolean): Condition.StateCondition = when (this) {
    is Condition.TimeWindow -> copy(negate = negate)
    is Condition.BtConnected -> copy(negate = negate)
    is Condition.Wifi -> copy(negate = negate)
    is Condition.Charging -> copy(negate = negate)
    is Condition.AudioOutput -> copy(negate = negate)
    is Condition.MediaPlaying -> copy(negate = negate)
    is Condition.RingerMode -> copy(negate = negate)
    is Condition.CalendarEvent -> copy(negate = negate)
    is Condition.HaState -> copy(negate = negate)
    is Condition.MacNearby -> copy(negate = negate)
    is Condition.MacLocked -> copy(negate = negate)
}

fun Condition.InstantCondition.withUntil(until: EndEvent?): Condition.InstantCondition = when (this) {
    is Condition.TimeInstant -> copy(until = until)
    is Condition.Alarm -> copy(until = until)
    is Condition.BtDisconnected -> copy(until = until)
    is Condition.NotificationFrom -> copy(until = until)
    is Condition.HaEvent -> copy(until = until)
    is Condition.NotificationEnded -> copy(until = until)
    is Condition.PlaceSaved -> copy(until = until)
}

enum class ConditionType(val jsonName: String, val isInstant: Boolean) {
    TIME_WINDOW("time_window", isInstant = false),
    BT_CONNECTED("bt_connected", isInstant = false),
    WIFI("wifi", isInstant = false),
    CHARGING("charging", isInstant = false),
    AUDIO_OUTPUT("audio_output", isInstant = false),
    MEDIA_PLAYING("media_playing", isInstant = false),
    RINGER_MODE("ringer_mode", isInstant = false),
    CALENDAR_EVENT("calendar_event", isInstant = false),
    HA_STATE("ha_state", isInstant = false),
    MAC_NEARBY("mac_nearby", isInstant = false),
    MAC_LOCKED("mac_locked", isInstant = false),
    TIME_INSTANT("time_instant", isInstant = true),
    ALARM("alarm", isInstant = true),
    BT_DISCONNECTED("bt_disconnected", isInstant = true),
    NOTIFICATION("notification", isInstant = true),
    HA_EVENT("ha_event", isInstant = true),
    NOTIFICATION_ENDED("notification_ended", isInstant = true),
    PLACE_SAVED("place_saved", isInstant = true),
    ;

    companion object {
        fun fromJsonName(jsonName: String): ConditionType? = entries.firstOrNull { it.jsonName == jsonName }
    }
}

@Serializable
enum class WifiMode {
    @SerialName("any")
    ANY,

    @SerialName("ssid")
    SSID,
}

@Serializable
enum class RingerModeState {
    @SerialName("silent")
    SILENT,

    @SerialName("vibrate")
    VIBRATE,

    @SerialName("normal")
    NORMAL,

    @SerialName("dnd")
    DND,
}

data class Activation(
    val scenarioId: String,
    val since: Long,
    val expiresAt: Long? = null,
    val payload: TriggerPayload = TriggerPayload.EMPTY,
) {
    fun isExpired(now: Long): Boolean = expiresAt != null && expiresAt <= now

    // An end event may carry an activation with no expiry at all.
    val isEventHeld: Boolean get() = expiresAt != null || payload.until != null
}

fun tidyPackages(packages: List<String>): List<String> =
    packages.map(String::trim).filter(String::isNotEmpty).distinct()

fun tidyHaStates(states: List<String>): List<String> =
    states.map { it.trim().lowercase() }.filter(String::isNotEmpty).distinct()

// A placeholder is left as it is, for the user to fill in in Cue:.
fun tidyHaEntity(entity: String): String = haEntityIdOrNull(entity) ?: entity.trim()

// A hand-written file may carry padded or repeated package names; a match is exact, so they are
// tidied on the way in rather than never matching. Extract rules lose what they do not read, so
// nothing stored carries a marker its kind ignores, whatever its length.
internal fun Scenario.tidied(): Scenario = copy(
    conditions = conditions.map { condition ->
        val tidied = when (condition) {
            is Condition.MediaPlaying -> condition.copy(packages = tidyPackages(condition.packages))
            is Condition.NotificationFrom -> condition.copy(extract = tidyExtractRules(condition.extract))
            is Condition.NotificationEnded -> condition.copy(packageName = condition.packageName.trim())
            is Condition.HaState -> condition.copy(
                entity = tidyHaEntity(condition.entity),
                states = tidyHaStates(condition.states),
            )

            else -> condition
        }
        val until = (tidied as? Condition.InstantCondition)?.until
        if (tidied is Condition.InstantCondition && until is EndEvent.Notification) {
            tidied.withUntil(until.copy(packageName = until.packageName.trim()))
        } else {
            tidied
        }
    },
)
