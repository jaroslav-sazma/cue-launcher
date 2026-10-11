package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.RingerModeState
import app.cue.launcher.data.scenario.WifiMode
import app.cue.launcher.engine.ConditionOutcome
import app.cue.launcher.engine.SystemState
import app.cue.launcher.engine.stateConditionOutcome

data class LiveReading(val verdict: LiveVerdict, val now: NowKey, val argument: String? = null)

enum class LiveVerdict { HOLDS, FAILS, UNKNOWN, AWAITS }

enum class NowKey {
    TIME,
    TIME_UNKNOWN,
    BT_CONNECTED,
    BT_NONE,
    BT_UNKNOWN,
    WIFI_SSID,
    WIFI_CONNECTED,
    WIFI_HIDDEN,
    WIFI_DISCONNECTED,
    WIFI_UNKNOWN,
    CHARGING,
    NOT_CHARGING,
    CHARGING_UNKNOWN,
    AUDIO_CONNECTED,
    AUDIO_NONE,
    AUDIO_UNKNOWN,
    MEDIA_PLAYING,
    MEDIA_SILENT,
    MEDIA_UNKNOWN,
    MEDIA_APP_PLAYING,
    MEDIA_APP_SILENT,
    MEDIA_APPS_PLAYING,
    MEDIA_APPS_SILENT,
    RINGER_SILENT,
    RINGER_VIBRATE,
    RINGER_NORMAL,
    RINGER_UNKNOWN,
    DND_ON,
    DND_OFF,
    DND_UNKNOWN,
    CALENDAR_BUSY,
    CALENDAR_FREE,
    CALENDAR_UNKNOWN,
    HA_STATE,
    HA_UNKNOWN,

    AWAITS_EVENT,
}

fun liveReading(condition: Condition, state: SystemState): LiveReading = when (condition) {
    is Condition.InstantCondition -> LiveReading(LiveVerdict.AWAITS, NowKey.AWAITS_EVENT)
    is Condition.StateCondition -> LiveReading(
        verdict = verdictOf(stateConditionOutcome(condition, state)),
        now = measuredNow(condition, state),
        argument = measuredArgument(condition, state),
    )
}

private fun verdictOf(outcome: ConditionOutcome): LiveVerdict = when (outcome) {
    ConditionOutcome.HOLDS -> LiveVerdict.HOLDS
    ConditionOutcome.FAILS -> LiveVerdict.FAILS
    ConditionOutcome.UNKNOWN -> LiveVerdict.UNKNOWN
}

private fun measuredNow(condition: Condition.StateCondition, state: SystemState): NowKey =
    when (condition) {
        is Condition.TimeWindow -> if (state.clock == null) NowKey.TIME_UNKNOWN else NowKey.TIME
        is Condition.BtConnected -> bluetoothNow(state)
        is Condition.Wifi -> wifiNow(condition, state)
        is Condition.Charging -> pick(state.charging, NowKey.CHARGING, NowKey.NOT_CHARGING, NowKey.CHARGING_UNKNOWN)
        is Condition.AudioOutput ->
            pick(state.audioOutput, NowKey.AUDIO_CONNECTED, NowKey.AUDIO_NONE, NowKey.AUDIO_UNKNOWN)

        is Condition.MediaPlaying -> mediaNow(condition, state)

        is Condition.CalendarEvent ->
            pick(state.calendarEvent, NowKey.CALENDAR_BUSY, NowKey.CALENDAR_FREE, NowKey.CALENDAR_UNKNOWN)

        is Condition.RingerMode -> ringerNow(condition, state)
        is Condition.HaState ->
            if (state.ha?.stateOf(condition.entity) == null) NowKey.HA_UNKNOWN else NowKey.HA_STATE
    }

private fun measuredArgument(condition: Condition.StateCondition, state: SystemState): String? =
    when (condition) {
        is Condition.TimeWindow -> state.clock?.let { formatMinuteOfDay(it.minuteOfDay) }

        is Condition.Wifi -> state.wifi?.ssid?.takeIf { condition.mode == WifiMode.SSID }
        is Condition.HaState -> state.ha?.stateOf(condition.entity)
        else -> null
    }

private fun mediaNow(condition: Condition.MediaPlaying, state: SystemState): NowKey {
    val (playing, silent) = when (condition.packages.size) {
        0 -> NowKey.MEDIA_PLAYING to NowKey.MEDIA_SILENT
        1 -> NowKey.MEDIA_APP_PLAYING to NowKey.MEDIA_APP_SILENT
        else -> NowKey.MEDIA_APPS_PLAYING to NowKey.MEDIA_APPS_SILENT
    }
    return pick(state.media?.isPlaying(condition.packages), playing, silent, NowKey.MEDIA_UNKNOWN)
}

private fun bluetoothNow(state: SystemState): NowKey = when {
    state.bluetooth == null -> NowKey.BT_UNKNOWN
    state.bluetooth.connectedMacs.isEmpty() -> NowKey.BT_NONE
    else -> NowKey.BT_CONNECTED
}

private fun wifiNow(condition: Condition.Wifi, state: SystemState): NowKey = when {
    state.wifi == null -> NowKey.WIFI_UNKNOWN
    !state.wifi.connected -> NowKey.WIFI_DISCONNECTED
    condition.mode == WifiMode.ANY -> NowKey.WIFI_CONNECTED
    state.wifi.ssid == null -> NowKey.WIFI_HIDDEN
    else -> NowKey.WIFI_SSID
}

private fun ringerNow(condition: Condition.RingerMode, state: SystemState): NowKey =
    if (condition.mode == RingerModeState.DND) {
        pick(state.doNotDisturb, NowKey.DND_ON, NowKey.DND_OFF, NowKey.DND_UNKNOWN)
    } else {
        when (state.ringerMode) {
            RingerModeState.SILENT -> NowKey.RINGER_SILENT
            RingerModeState.VIBRATE -> NowKey.RINGER_VIBRATE
            RingerModeState.NORMAL -> NowKey.RINGER_NORMAL
            else -> NowKey.RINGER_UNKNOWN
        }
    }

private fun pick(value: Boolean?, yes: NowKey, no: NowKey, unknown: NowKey): NowKey = when (value) {
    true -> yes
    false -> no
    null -> unknown
}

private fun formatMinuteOfDay(minuteOfDay: Int): String =
    "%d:%02d".format(minuteOfDay / MINUTES_IN_HOUR, minuteOfDay % MINUTES_IN_HOUR)
