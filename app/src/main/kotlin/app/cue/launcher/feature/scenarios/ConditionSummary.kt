package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.scenario.ALL_WEEK_DAYS
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.RingerModeState
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.WEEK_DAYS
import app.cue.launcher.data.scenario.WifiMode

data class ConditionPhrase(
    val key: PhraseKey,

    val argument: String? = null,

    val days: DaysPhrase = DaysPhrase.EveryDay,

    val apps: List<String> = emptyList(),

    val states: List<String> = emptyList(),

    val until: EndPhrase? = null,
)

data class EndPhrase(
    val key: EndPhraseKey,

    val argument: String? = null,

    val days: DaysPhrase = DaysPhrase.EveryDay,
)

enum class EndPhraseKey {
    TIME,
    ALARM,
    BT_ANY,
    BT_DEVICE,
    NOTIFICATION,
    HA_EVENT,
}

enum class PhraseKey {
    TIME_WINDOW,
    TIME_WINDOW_NEGATED,
    BT_CONNECTED_ANY,
    BT_CONNECTED_ANY_NEGATED,
    BT_CONNECTED_DEVICE,
    BT_CONNECTED_DEVICE_NEGATED,
    WIFI_ANY,
    WIFI_ANY_NEGATED,
    WIFI_SSID,
    WIFI_SSID_NEGATED,
    CHARGING,
    CHARGING_NEGATED,
    AUDIO_OUTPUT,
    AUDIO_OUTPUT_NEGATED,
    MEDIA_PLAYING,
    MEDIA_PLAYING_NEGATED,
    MEDIA_APPS,
    MEDIA_APPS_NEGATED,

    // "None of several", which a plain negation of "A or B" would not say.
    MEDIA_APPS_NONE,
    RINGER_SILENT,
    RINGER_SILENT_NEGATED,
    RINGER_VIBRATE,
    RINGER_VIBRATE_NEGATED,
    RINGER_NORMAL,
    RINGER_NORMAL_NEGATED,
    RINGER_DND,
    RINGER_DND_NEGATED,
    CALENDAR_EVENT,
    CALENDAR_EVENT_NEGATED,
    HA_STATE,
    HA_STATE_NEGATED,

    // "In none of several states", as with the apps.
    HA_STATE_NONE,
    TIME_INSTANT,
    ALARM,
    BT_DISCONNECTED_ANY,
    BT_DISCONNECTED_DEVICE,
    NOTIFICATION,
    HA_EVENT,
    NOTIFICATION_ENDED,
    PLACE_SAVED,
}

sealed interface DaysPhrase {

    data object EveryDay : DaysPhrase

    data object Workdays : DaysPhrase

    data object Weekend : DaysPhrase

    data class Selected(val days: List<Int>) : DaysPhrase
}

fun conditionPhrases(scenario: Scenario): List<ConditionPhrase> =
    scenario.conditions.map(::conditionPhrase)

fun conditionPhrase(condition: Condition): ConditionPhrase =
    triggerPhrase(condition).copy(until = (condition as? Condition.InstantCondition)?.until?.let(::endPhrase))

fun endPhrase(end: EndEvent): EndPhrase = when (end) {
    is EndEvent.AtTime -> EndPhrase(EndPhraseKey.TIME, shortTime(end.at), daysPhraseOf(end.days))
    EndEvent.Alarm -> EndPhrase(EndPhraseKey.ALARM)
    is EndEvent.BtDisconnected -> when (end.mac) {
        null -> EndPhrase(EndPhraseKey.BT_ANY)
        else -> EndPhrase(EndPhraseKey.BT_DEVICE, end.mac)
    }

    is EndEvent.Notification -> EndPhrase(EndPhraseKey.NOTIFICATION, end.packageName)
    is EndEvent.HaEvent -> EndPhrase(EndPhraseKey.HA_EVENT, end.code)
}

@Suppress("CyclomaticComplexMethod")
private fun triggerPhrase(condition: Condition): ConditionPhrase = when (condition) {
    is Condition.TimeWindow -> ConditionPhrase(
        key = pick(condition.negate, PhraseKey.TIME_WINDOW, PhraseKey.TIME_WINDOW_NEGATED),
        argument = "${shortTime(condition.from)}–${shortTime(condition.to)}",
        days = daysPhraseOf(condition.days),
    )

    is Condition.BtConnected -> when (condition.mac) {
        null -> ConditionPhrase(
            pick(condition.negate, PhraseKey.BT_CONNECTED_ANY, PhraseKey.BT_CONNECTED_ANY_NEGATED),
        )

        else -> ConditionPhrase(
            pick(condition.negate, PhraseKey.BT_CONNECTED_DEVICE, PhraseKey.BT_CONNECTED_DEVICE_NEGATED),
            condition.mac,
        )
    }

    is Condition.Wifi -> wifiPhrase(condition)

    is Condition.Charging -> ConditionPhrase(pick(condition.negate, PhraseKey.CHARGING, PhraseKey.CHARGING_NEGATED))
    is Condition.AudioOutput ->
        ConditionPhrase(pick(condition.negate, PhraseKey.AUDIO_OUTPUT, PhraseKey.AUDIO_OUTPUT_NEGATED))

    is Condition.MediaPlaying -> mediaPhrase(condition)

    is Condition.CalendarEvent ->
        ConditionPhrase(pick(condition.negate, PhraseKey.CALENDAR_EVENT, PhraseKey.CALENDAR_EVENT_NEGATED))

    is Condition.HaState -> ConditionPhrase(
        key = when {
            !condition.negate -> PhraseKey.HA_STATE
            condition.states.size < 2 -> PhraseKey.HA_STATE_NEGATED
            else -> PhraseKey.HA_STATE_NONE
        },
        argument = condition.entity,
        states = condition.states,
    )

    is Condition.RingerMode -> ConditionPhrase(
        when (condition.mode) {
            RingerModeState.SILENT -> pick(condition.negate, PhraseKey.RINGER_SILENT, PhraseKey.RINGER_SILENT_NEGATED)
            RingerModeState.VIBRATE ->
                pick(condition.negate, PhraseKey.RINGER_VIBRATE, PhraseKey.RINGER_VIBRATE_NEGATED)

            RingerModeState.NORMAL -> pick(condition.negate, PhraseKey.RINGER_NORMAL, PhraseKey.RINGER_NORMAL_NEGATED)
            RingerModeState.DND -> pick(condition.negate, PhraseKey.RINGER_DND, PhraseKey.RINGER_DND_NEGATED)
        },
    )

    is Condition.TimeInstant -> ConditionPhrase(
        key = PhraseKey.TIME_INSTANT,
        argument = shortTime(condition.at),
        days = daysPhraseOf(condition.days),
    )

    is Condition.Alarm -> ConditionPhrase(PhraseKey.ALARM)

    is Condition.BtDisconnected -> when (condition.mac) {
        null -> ConditionPhrase(PhraseKey.BT_DISCONNECTED_ANY)
        else -> ConditionPhrase(PhraseKey.BT_DISCONNECTED_DEVICE, condition.mac)
    }

    is Condition.NotificationFrom -> ConditionPhrase(PhraseKey.NOTIFICATION, condition.packageName)

    is Condition.HaEvent -> ConditionPhrase(PhraseKey.HA_EVENT, condition.code)

    is Condition.NotificationEnded -> ConditionPhrase(PhraseKey.NOTIFICATION_ENDED, condition.packageName)

    is Condition.PlaceSaved -> ConditionPhrase(PhraseKey.PLACE_SAVED)
}

private fun pick(negate: Boolean, plain: PhraseKey, negated: PhraseKey): PhraseKey = if (negate) negated else plain

private fun mediaPhrase(condition: Condition.MediaPlaying): ConditionPhrase = when {
    condition.packages.isEmpty() ->
        ConditionPhrase(pick(condition.negate, PhraseKey.MEDIA_PLAYING, PhraseKey.MEDIA_PLAYING_NEGATED))

    else -> ConditionPhrase(
        key = when {
            !condition.negate -> PhraseKey.MEDIA_APPS
            condition.packages.size == 1 -> PhraseKey.MEDIA_APPS_NEGATED
            else -> PhraseKey.MEDIA_APPS_NONE
        },
        apps = condition.packages,
    )
}

private fun wifiPhrase(condition: Condition.Wifi): ConditionPhrase = when {
    condition.mode == WifiMode.ANY && condition.negate -> ConditionPhrase(PhraseKey.WIFI_ANY_NEGATED)
    condition.mode == WifiMode.ANY -> ConditionPhrase(PhraseKey.WIFI_ANY)
    condition.negate -> ConditionPhrase(PhraseKey.WIFI_SSID_NEGATED, condition.ssid.orEmpty())
    else -> ConditionPhrase(PhraseKey.WIFI_SSID, condition.ssid.orEmpty())
}

private val WORKDAYS = listOf(1, 2, 3, 4, 5)
private val WEEKEND = listOf(6, 7)

fun daysPhraseOf(days: List<Int>): DaysPhrase {
    val valid = days.filter { it in WEEK_DAYS }.distinct().sorted()
    return when (valid) {
        ALL_WEEK_DAYS -> DaysPhrase.EveryDay
        WORKDAYS -> DaysPhrase.Workdays
        WEEKEND -> DaysPhrase.Weekend
        else -> DaysPhrase.Selected(valid)
    }
}

fun shortTime(value: String): String {
    val (hour, minute) = parseHourMinute(value) ?: return value
    return if (minute == 0) "$hour" else "%d:%02d".format(hour, minute)
}
