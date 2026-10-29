package app.cue.launcher.feature.scenarios

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.Scenario

@Composable
fun conditionSummary(scenario: Scenario): String {
    if (scenario.conditions.isEmpty()) {

        return stringResource(
            R.string.scenarios_fallback_summary,
            scenario.size.jsonName,
            stringResource(R.string.scenarios_no_conditions),
        )
    }
    return conditionsText(scenario.conditions, scenario.match)
}

@Composable
fun conditionsText(
    conditions: List<Condition>,
    match: ConditionMatch,
    phrase: (Condition) -> ConditionPhrase = ::conditionPhrase,
): String {
    val separator = stringResource(
        if (match == ConditionMatch.ANY) {
            R.string.scenarios_condition_separator_any
        } else {
            R.string.scenarios_condition_separator
        },
    )

    val summary = StringBuilder()
    conditions.forEachIndexed { index, condition ->
        if (index > 0) summary.append(separator)
        summary.append(conditionPhraseText(phrase(condition)))
    }
    return summary.toString()
}

@Composable
fun conditionPhraseText(phrase: ConditionPhrase): String {
    val none = phrase.key == PhraseKey.MEDIA_APPS_NONE || phrase.key == PhraseKey.HA_STATE_NONE
    val joiner = if (none) AppJoiner.NOR else AppJoiner.OR
    val argument = if (phrase.apps.isEmpty()) phrase.argument else appListText(phrase.apps, joiner)
    val base = when {
        phrase.states.isEmpty() -> stringResource(phraseResource(phrase.key), argument.orEmpty())
        else -> stringResource(phraseResource(phrase.key), argument.orEmpty(), listText(phrase.states, joiner))
    }
    val days = daysText(phrase.days)
    val trigger = if (days.isEmpty()) base else "$days $base"
    return phrase.until?.let { stringResource(R.string.phrase_with_until, trigger, endPhraseText(it)) } ?: trigger
}

@Composable
private fun endPhraseText(phrase: EndPhrase): String {
    val days = daysText(phrase.days)
    return when {
        phrase.key == EndPhraseKey.TIME && days.isNotEmpty() ->
            stringResource(R.string.phrase_until_time_days, phrase.argument.orEmpty(), days)

        else -> stringResource(
            when (phrase.key) {
                EndPhraseKey.TIME -> R.string.phrase_until_time
                EndPhraseKey.ALARM -> R.string.phrase_until_alarm
                EndPhraseKey.BT_ANY -> R.string.phrase_until_bt_any
                EndPhraseKey.BT_DEVICE -> R.string.phrase_until_bt_device
                EndPhraseKey.NOTIFICATION -> R.string.phrase_until_notification
                EndPhraseKey.HA_EVENT -> R.string.phrase_until_ha_event
            },
            phrase.argument.orEmpty(),
        )
    }
}

@Composable
fun scenarioStatusText(status: ScenarioStatus): String = when (status) {
    ScenarioStatus.Disabled -> stringResource(R.string.scenario_status_disabled)
    ScenarioStatus.Fallback -> stringResource(R.string.scenario_status_fallback)
    ScenarioStatus.Holds -> stringResource(R.string.scenario_status_holds)
    ScenarioStatus.NoConditionHolds -> stringResource(R.string.scenario_status_none_holds)
    is ScenarioStatus.WaitsForEvent ->
        stringResource(R.string.scenario_status_waiting, conditionText(status.condition))

    is ScenarioStatus.ConditionFails ->
        stringResource(R.string.scenario_status_fails, conditionText(status.condition))

    is ScenarioStatus.NotMeasured ->
        stringResource(R.string.scenario_status_unknown, conditionText(status.condition))
}

@Composable
private fun conditionText(condition: Condition): String = conditionPhraseText(conditionPhrase(condition))

@Composable
private fun daysText(days: DaysPhrase): String = when (days) {
    DaysPhrase.EveryDay -> ""
    DaysPhrase.Workdays -> stringResource(R.string.days_workdays)
    DaysPhrase.Weekend -> stringResource(R.string.days_weekend)
    is DaysPhrase.Selected -> {
        val names = stringArrayResource(R.array.days_short)
        days.days.joinToString(stringResource(R.string.days_separator)) { day -> names[day - 1] }
    }
}

@Composable
fun scenarioGroupTitle(group: ScenarioGroup): String = stringResource(
    when (group) {
        ScenarioGroup.HERO_4X2 -> R.string.scenarios_group_hero
        ScenarioGroup.MEDIUM_2X2 -> R.string.scenarios_group_medium
        ScenarioGroup.SMALL_1X1 -> R.string.scenarios_group_small
        ScenarioGroup.FALLBACK -> R.string.scenarios_group_fallback
    },
)

@Composable
fun scenarioFilterLabel(chip: ScenarioFilter, count: Int?): String = when (chip) {
    ScenarioFilter.ALL -> stringResource(R.string.scenarios_chip_all, count ?: 0)
    ScenarioFilter.HERO_4X2 -> stringResource(R.string.scenarios_chip_4x2)
    ScenarioFilter.MEDIUM_2X2 -> stringResource(R.string.scenarios_chip_2x2)
    ScenarioFilter.SMALL_1X1 -> stringResource(R.string.scenarios_chip_1x1)
    ScenarioFilter.DISABLED -> stringResource(R.string.scenarios_chip_disabled)
}

@Suppress("CyclomaticComplexMethod")
private fun phraseResource(key: PhraseKey): Int = when (key) {
    PhraseKey.TIME_WINDOW -> R.string.phrase_time_window
    PhraseKey.TIME_WINDOW_NEGATED -> R.string.phrase_time_window_negated
    PhraseKey.BT_CONNECTED_ANY -> R.string.phrase_bt_connected_any
    PhraseKey.BT_CONNECTED_ANY_NEGATED -> R.string.phrase_bt_connected_any_negated
    PhraseKey.BT_CONNECTED_DEVICE -> R.string.phrase_bt_connected_device
    PhraseKey.BT_CONNECTED_DEVICE_NEGATED -> R.string.phrase_bt_connected_device_negated
    PhraseKey.WIFI_ANY -> R.string.phrase_wifi_any
    PhraseKey.WIFI_ANY_NEGATED -> R.string.phrase_wifi_any_negated
    PhraseKey.WIFI_SSID -> R.string.phrase_wifi_ssid
    PhraseKey.WIFI_SSID_NEGATED -> R.string.phrase_wifi_ssid_negated
    PhraseKey.CHARGING -> R.string.phrase_charging
    PhraseKey.CHARGING_NEGATED -> R.string.phrase_charging_negated
    PhraseKey.AUDIO_OUTPUT -> R.string.phrase_audio_output
    PhraseKey.AUDIO_OUTPUT_NEGATED -> R.string.phrase_audio_output_negated
    PhraseKey.MEDIA_PLAYING -> R.string.phrase_media_playing
    PhraseKey.MEDIA_PLAYING_NEGATED -> R.string.phrase_media_playing_negated
    PhraseKey.MEDIA_APPS -> R.string.phrase_media_apps
    PhraseKey.MEDIA_APPS_NEGATED -> R.string.phrase_media_apps_negated
    PhraseKey.MEDIA_APPS_NONE -> R.string.phrase_media_apps_none
    PhraseKey.RINGER_SILENT -> R.string.phrase_ringer_silent
    PhraseKey.RINGER_SILENT_NEGATED -> R.string.phrase_ringer_silent_negated
    PhraseKey.RINGER_VIBRATE -> R.string.phrase_ringer_vibrate
    PhraseKey.RINGER_VIBRATE_NEGATED -> R.string.phrase_ringer_vibrate_negated
    PhraseKey.RINGER_NORMAL -> R.string.phrase_ringer_normal
    PhraseKey.RINGER_NORMAL_NEGATED -> R.string.phrase_ringer_normal_negated
    PhraseKey.RINGER_DND -> R.string.phrase_ringer_dnd
    PhraseKey.RINGER_DND_NEGATED -> R.string.phrase_ringer_dnd_negated
    PhraseKey.CALENDAR_EVENT -> R.string.phrase_calendar_event
    PhraseKey.CALENDAR_EVENT_NEGATED -> R.string.phrase_calendar_event_negated
    PhraseKey.HA_STATE -> R.string.phrase_ha_state
    PhraseKey.HA_STATE_NEGATED -> R.string.phrase_ha_state_negated
    PhraseKey.HA_STATE_NONE -> R.string.phrase_ha_state_none
    PhraseKey.MAC_NEARBY -> R.string.phrase_mac_nearby
    PhraseKey.MAC_NEARBY_NEGATED -> R.string.phrase_mac_nearby_negated
    PhraseKey.MAC_LOCKED -> R.string.phrase_mac_locked
    PhraseKey.MAC_LOCKED_NEGATED -> R.string.phrase_mac_locked_negated
    PhraseKey.TIME_INSTANT -> R.string.phrase_time_instant
    PhraseKey.ALARM -> R.string.phrase_alarm
    PhraseKey.BT_DISCONNECTED_ANY -> R.string.phrase_bt_disconnected_any
    PhraseKey.BT_DISCONNECTED_DEVICE -> R.string.phrase_bt_disconnected_device
    PhraseKey.NOTIFICATION -> R.string.phrase_notification
    PhraseKey.HA_EVENT -> R.string.phrase_ha_event
    PhraseKey.NOTIFICATION_ENDED -> R.string.phrase_notification_ended
    PhraseKey.PLACE_SAVED -> R.string.phrase_place_saved
}
