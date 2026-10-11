package app.cue.launcher.feature.scenarios.builder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.ConditionType
import app.cue.launcher.feature.scenarios.LiveReading
import app.cue.launcher.feature.scenarios.LiveVerdict
import app.cue.launcher.feature.scenarios.NowKey

@Composable
@Suppress("CyclomaticComplexMethod")
internal fun conditionTypeLabel(type: ConditionType): String = stringResource(
    when (type) {
        ConditionType.TIME_WINDOW -> R.string.condition_time_window
        ConditionType.BT_CONNECTED -> R.string.condition_bt_connected
        ConditionType.WIFI -> R.string.condition_wifi
        ConditionType.CHARGING -> R.string.condition_charging
        ConditionType.AUDIO_OUTPUT -> R.string.condition_audio_output
        ConditionType.MEDIA_PLAYING -> R.string.condition_media_playing
        ConditionType.RINGER_MODE -> R.string.condition_ringer_mode
        ConditionType.CALENDAR_EVENT -> R.string.condition_calendar_event
        ConditionType.HA_STATE -> R.string.condition_ha_state
        ConditionType.TIME_INSTANT, ConditionType.ALARM -> R.string.condition_time_instant
        ConditionType.BT_DISCONNECTED -> R.string.condition_bt_disconnected
        ConditionType.NOTIFICATION -> R.string.condition_notification
        ConditionType.HA_EVENT -> R.string.condition_ha_event
        ConditionType.NOTIFICATION_ENDED -> R.string.condition_notification_ended
        ConditionType.PLACE_SAVED -> R.string.condition_place_saved
    },
)

@Composable
@Suppress("CyclomaticComplexMethod")
internal fun conditionTypeExamples(type: ConditionType): String = stringResource(
    when (type) {
        ConditionType.TIME_WINDOW -> R.string.condition_example_time_window
        ConditionType.BT_CONNECTED -> R.string.condition_example_bt_connected
        ConditionType.WIFI -> R.string.condition_example_wifi
        ConditionType.CHARGING -> R.string.condition_example_charging
        ConditionType.AUDIO_OUTPUT -> R.string.condition_example_audio_output
        ConditionType.MEDIA_PLAYING -> R.string.condition_example_media_playing
        ConditionType.RINGER_MODE -> R.string.condition_example_ringer_mode
        ConditionType.CALENDAR_EVENT -> R.string.condition_example_calendar_event
        ConditionType.HA_STATE -> R.string.condition_example_ha_state
        ConditionType.TIME_INSTANT, ConditionType.ALARM -> R.string.condition_example_time_instant
        ConditionType.BT_DISCONNECTED -> R.string.condition_example_bt_disconnected
        ConditionType.NOTIFICATION -> R.string.condition_example_notification
        ConditionType.HA_EVENT -> R.string.condition_example_ha_event
        ConditionType.NOTIFICATION_ENDED -> R.string.condition_example_notification_ended
        ConditionType.PLACE_SAVED -> R.string.condition_example_place_saved
    },
)

@Composable
internal fun rememberCatalogEntries(): List<CatalogEntry> = CONDITION_CATALOG.map { type ->
    CatalogEntry(
        type = type,
        label = conditionTypeLabel(type),
        examples = conditionTypeExamples(type),
    )
}

@Composable
internal fun catalogSectionTitle(section: ConditionSection): String = stringResource(
    when (section) {
        ConditionSection.STATE -> R.string.builder_catalog_state
        ConditionSection.INSTANT -> R.string.builder_catalog_instant
    },
)

@Composable
internal fun liveReadingText(reading: LiveReading): String {
    val now = stringResource(nowResource(reading.now), reading.argument.orEmpty())
    return stringResource(
        when (reading.verdict) {
            LiveVerdict.HOLDS -> R.string.live_state_holds
            LiveVerdict.FAILS -> R.string.live_state_fails
            LiveVerdict.UNKNOWN -> R.string.live_state_unknown
            LiveVerdict.AWAITS -> R.string.live_state_awaits
        },
        now,
    )
}

@Composable
internal fun blockingReasonText(reading: LiveReading): String = stringResource(
    R.string.live_state_blocks,
    stringResource(nowResource(reading.now), reading.argument.orEmpty()),
)

@Composable
internal fun liveVerdictShort(verdict: LiveVerdict): String = stringResource(
    when (verdict) {
        LiveVerdict.HOLDS -> R.string.live_short_holds
        LiveVerdict.FAILS -> R.string.live_short_fails
        LiveVerdict.UNKNOWN -> R.string.live_short_unknown
        LiveVerdict.AWAITS -> R.string.live_short_awaits
    },
)

@Composable
@Suppress("CyclomaticComplexMethod")
internal fun conditionFormErrorText(error: ConditionFormError): String = stringResource(
    when (error) {
        ConditionFormError.WIFI_SSID -> R.string.builder_error_wifi_ssid
        ConditionFormError.HA_CODE -> R.string.builder_error_ha_code
        ConditionFormError.HA_ENTITY -> R.string.builder_error_ha_entity
        ConditionFormError.HA_STATES -> R.string.builder_error_ha_states
        ConditionFormError.NOTIFICATION_PACKAGE -> R.string.builder_error_notification_package
        ConditionFormError.NOTIFICATION_EXTRACT_COUNT -> R.string.builder_error_notification_extract_count
        ConditionFormError.NOTIFICATION_EXTRACT_NAME -> R.string.builder_error_notification_extract_name
        ConditionFormError.NOTIFICATION_EXTRACT_RESERVED -> R.string.builder_error_notification_extract_reserved
        ConditionFormError.NOTIFICATION_EXTRACT_DUPLICATE -> R.string.builder_error_notification_extract_duplicate
        ConditionFormError.NOTIFICATION_EXTRACT_UNTIL -> R.string.builder_error_notification_extract_until
        ConditionFormError.NOTIFICATION_EXTRACT_LENGTH -> R.string.builder_error_notification_extract_length
        ConditionFormError.TIME -> R.string.builder_error_time
        ConditionFormError.DAYS -> R.string.builder_error_days
        ConditionFormError.DURATION -> R.string.builder_error_duration
    },
)

@Composable
internal fun durationChipLabel(choice: DurationChoice): String = stringResource(
    when (choice) {
        DurationChoice.SEC_30 -> R.string.builder_duration_30
        DurationChoice.SEC_60 -> R.string.builder_duration_60
        DurationChoice.MIN_5 -> R.string.builder_duration_300
        DurationChoice.CUSTOM -> R.string.builder_duration_custom
    },
)

@Suppress("CyclomaticComplexMethod")
private fun nowResource(key: NowKey): Int = when (key) {
    NowKey.TIME -> R.string.now_time
    NowKey.TIME_UNKNOWN -> R.string.now_time_unknown
    NowKey.BT_CONNECTED -> R.string.now_bt_connected
    NowKey.BT_NONE -> R.string.now_bt_none
    NowKey.BT_UNKNOWN -> R.string.now_bt_unknown
    NowKey.WIFI_SSID -> R.string.now_wifi_ssid
    NowKey.WIFI_CONNECTED -> R.string.now_wifi_connected
    NowKey.WIFI_HIDDEN -> R.string.now_wifi_hidden
    NowKey.WIFI_DISCONNECTED -> R.string.now_wifi_disconnected
    NowKey.WIFI_UNKNOWN -> R.string.now_wifi_unknown
    NowKey.CHARGING -> R.string.now_charging
    NowKey.NOT_CHARGING -> R.string.now_not_charging
    NowKey.CHARGING_UNKNOWN -> R.string.now_charging_unknown
    NowKey.AUDIO_CONNECTED -> R.string.now_audio_connected
    NowKey.AUDIO_NONE -> R.string.now_audio_none
    NowKey.AUDIO_UNKNOWN -> R.string.now_audio_unknown
    NowKey.MEDIA_PLAYING -> R.string.now_media_playing
    NowKey.MEDIA_SILENT -> R.string.now_media_silent
    NowKey.MEDIA_UNKNOWN -> R.string.now_media_unknown
    NowKey.MEDIA_APP_PLAYING -> R.string.now_media_app_playing
    NowKey.MEDIA_APP_SILENT -> R.string.now_media_app_silent
    NowKey.MEDIA_APPS_PLAYING -> R.string.now_media_apps_playing
    NowKey.MEDIA_APPS_SILENT -> R.string.now_media_apps_silent
    NowKey.RINGER_SILENT -> R.string.now_ringer_silent
    NowKey.RINGER_VIBRATE -> R.string.now_ringer_vibrate
    NowKey.RINGER_NORMAL -> R.string.now_ringer_normal
    NowKey.RINGER_UNKNOWN -> R.string.now_ringer_unknown
    NowKey.DND_ON -> R.string.now_dnd_on
    NowKey.DND_OFF -> R.string.now_dnd_off
    NowKey.DND_UNKNOWN -> R.string.now_dnd_unknown
    NowKey.CALENDAR_BUSY -> R.string.now_calendar_busy
    NowKey.CALENDAR_FREE -> R.string.now_calendar_free
    NowKey.CALENDAR_UNKNOWN -> R.string.now_calendar_unknown
    NowKey.HA_STATE -> R.string.now_ha_state
    NowKey.HA_UNKNOWN -> R.string.now_ha_unknown
    NowKey.AWAITS_EVENT -> R.string.now_awaits_event
}
