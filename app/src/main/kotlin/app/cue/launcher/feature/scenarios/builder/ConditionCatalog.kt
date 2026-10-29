package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionType
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.EndEventType
import app.cue.launcher.data.scenario.RingerModeState
import app.cue.launcher.data.scenario.WifiMode
import app.cue.launcher.engine.DEFAULT_INSTANT_DURATION_SEC
import app.cue.launcher.engine.MacBridgeState
import app.cue.launcher.engine.SystemState

enum class ConditionSection {

    STATE,

    INSTANT,
}

// A set time is not listed on its own: it is the other mode of the alarm entry, see MomentForm.
val CONDITION_CATALOG: List<ConditionType> = listOf(
    ConditionType.TIME_WINDOW,
    ConditionType.BT_CONNECTED,
    ConditionType.WIFI,
    ConditionType.CHARGING,
    ConditionType.AUDIO_OUTPUT,
    ConditionType.MEDIA_PLAYING,
    ConditionType.RINGER_MODE,
    ConditionType.CALENDAR_EVENT,
    ConditionType.HA_STATE,
    ConditionType.MAC_NEARBY,
    ConditionType.MAC_LOCKED,
    ConditionType.ALARM,
    ConditionType.BT_DISCONNECTED,
    ConditionType.NOTIFICATION,
    ConditionType.NOTIFICATION_ENDED,
    ConditionType.PLACE_SAVED,
    ConditionType.HA_EVENT,
)

// Mac nearby predates this and stays listed with its set-up note; the lock is offered only once a Mac
// is paired.
private val PAIRED_MAC_CONDITIONS: Set<ConditionType> = setOf(ConditionType.MAC_LOCKED)

fun conditionCatalog(bridgePaired: Boolean): List<ConditionType> =
    if (bridgePaired) CONDITION_CATALOG else CONDITION_CATALOG.filterNot { it in PAIRED_MAC_CONDITIONS }

// Not read yet counts as not paired: the entries appear a moment later rather than vanish under a tap.
val SystemState.bridgePaired: Boolean
    get() = macBridge != null && macBridge != MacBridgeState.OFF

fun conditionSection(type: ConditionType): ConditionSection =
    if (type.isInstant) ConditionSection.INSTANT else ConditionSection.STATE

data class CatalogSection(val section: ConditionSection, val types: List<ConditionType>)

data class CatalogEntry(
    val type: ConditionType,

    val label: String,

    val examples: String = "",
)

fun catalogSections(types: List<ConditionType>): List<CatalogSection> =
    ConditionSection.entries
        .map { section -> CatalogSection(section, types.filter { conditionSection(it) == section }) }
        .filter { it.types.isNotEmpty() }

@Suppress("CyclomaticComplexMethod")
fun newCondition(type: ConditionType): Condition = when (type) {
    ConditionType.TIME_WINDOW -> Condition.TimeWindow(from = DEFAULT_WINDOW_FROM, to = DEFAULT_WINDOW_TO)
    ConditionType.BT_CONNECTED -> Condition.BtConnected()
    ConditionType.WIFI -> Condition.Wifi(mode = WifiMode.ANY)
    ConditionType.CHARGING -> Condition.Charging()
    ConditionType.AUDIO_OUTPUT -> Condition.AudioOutput()
    ConditionType.MEDIA_PLAYING -> Condition.MediaPlaying()
    ConditionType.RINGER_MODE -> Condition.RingerMode(RingerModeState.SILENT)
    ConditionType.CALENDAR_EVENT -> Condition.CalendarEvent()
    ConditionType.HA_STATE -> Condition.HaState(entity = "", states = emptyList())
    ConditionType.MAC_NEARBY -> Condition.MacNearby()
    ConditionType.MAC_LOCKED -> Condition.MacLocked()
    ConditionType.TIME_INSTANT -> Condition.TimeInstant(
        at = DEFAULT_INSTANT_AT,
        durationSec = DEFAULT_INSTANT_DURATION_SEC,
    )

    ConditionType.ALARM -> Condition.Alarm(durationSec = DEFAULT_INSTANT_DURATION_SEC)
    ConditionType.BT_DISCONNECTED -> Condition.BtDisconnected(durationSec = DEFAULT_INSTANT_DURATION_SEC)
    ConditionType.NOTIFICATION -> Condition.NotificationFrom(
        packageName = "",
        durationSec = DEFAULT_INSTANT_DURATION_SEC,
    )

    ConditionType.HA_EVENT -> Condition.HaEvent(code = "", durationSec = DEFAULT_INSTANT_DURATION_SEC)

    // Shaped for the case it exists for: navigation ending, with the start-up blink left out.
    ConditionType.NOTIFICATION_ENDED -> Condition.NotificationEnded(
        packageName = "",
        ongoing = true,
        minLastedSec = DEFAULT_MIN_LASTED_SEC,
        durationSec = DEFAULT_NOTIFICATION_ENDED_DURATION_SEC,
    )

    ConditionType.PLACE_SAVED -> Condition.PlaceSaved(durationSec = DEFAULT_PLACE_DURATION_SEC)
}

@Suppress("CyclomaticComplexMethod")
fun conditionTypeOf(condition: Condition): ConditionType = when (condition) {
    is Condition.TimeWindow -> ConditionType.TIME_WINDOW
    is Condition.BtConnected -> ConditionType.BT_CONNECTED
    is Condition.Wifi -> ConditionType.WIFI
    is Condition.Charging -> ConditionType.CHARGING
    is Condition.AudioOutput -> ConditionType.AUDIO_OUTPUT
    is Condition.MediaPlaying -> ConditionType.MEDIA_PLAYING
    is Condition.RingerMode -> ConditionType.RINGER_MODE
    is Condition.CalendarEvent -> ConditionType.CALENDAR_EVENT
    is Condition.HaState -> ConditionType.HA_STATE
    is Condition.MacNearby -> ConditionType.MAC_NEARBY
    is Condition.MacLocked -> ConditionType.MAC_LOCKED
    is Condition.TimeInstant -> ConditionType.TIME_INSTANT
    is Condition.Alarm -> ConditionType.ALARM
    is Condition.BtDisconnected -> ConditionType.BT_DISCONNECTED
    is Condition.NotificationFrom -> ConditionType.NOTIFICATION
    is Condition.HaEvent -> ConditionType.HA_EVENT
    is Condition.NotificationEnded -> ConditionType.NOTIFICATION_ENDED
    is Condition.PlaceSaved -> ConditionType.PLACE_SAVED
}

// The notification comes first, as the end most tiles wait for.
val END_EVENT_CATALOG: List<EndEventType> = listOf(
    EndEventType.NOTIFICATION,
    EndEventType.TIME_INSTANT,
    EndEventType.ALARM,
    EndEventType.BT_DISCONNECTED,
    EndEventType.HA_EVENT,
)

fun newEndEvent(type: EndEventType): EndEvent = when (type) {
    EndEventType.TIME_INSTANT -> EndEvent.AtTime(at = DEFAULT_END_AT)
    EndEventType.ALARM -> EndEvent.Alarm
    EndEventType.BT_DISCONNECTED -> EndEvent.BtDisconnected()
    EndEventType.NOTIFICATION -> EndEvent.Notification(packageName = "")
    EndEventType.HA_EVENT -> EndEvent.HaEvent(code = "")
}

fun endEventTypeOf(end: EndEvent): EndEventType = when (end) {
    is EndEvent.AtTime -> EndEventType.TIME_INSTANT
    EndEvent.Alarm -> EndEventType.ALARM
    is EndEvent.BtDisconnected -> EndEventType.BT_DISCONNECTED
    is EndEvent.Notification -> EndEventType.NOTIFICATION
    is EndEvent.HaEvent -> EndEventType.HA_EVENT
}

private const val DEFAULT_WINDOW_FROM = "18:00"
private const val DEFAULT_WINDOW_TO = "23:00"

private const val DEFAULT_INSTANT_AT = "07:00"

// Not the set time's own default, which would end nothing: that moment starts the tile again.
private const val DEFAULT_END_AT = "09:00"

private const val DEFAULT_MIN_LASTED_SEC = 120
private const val DEFAULT_NOTIFICATION_ENDED_DURATION_SEC = 30 * 60
private const val DEFAULT_PLACE_DURATION_SEC = 24 * 60 * 60
