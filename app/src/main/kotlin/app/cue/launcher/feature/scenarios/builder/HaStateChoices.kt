package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.ha.haEntityIdOrNull
import app.cue.launcher.data.scenario.tidyHaStates

// The states the domain's own entities report, then the one the entity is in right now, then any the
// condition already holds that neither knows of, so a state typed by hand never drops out of sight.
internal fun haStateChoices(entity: String, current: String?, chosen: List<String>): List<String> {
    val domain = haEntityIdOrNull(entity)?.substringBefore('.')
    val known = HA_DOMAIN_STATES[domain].orEmpty()
    return tidyHaStates(known + listOfNotNull(current) + chosen)
}

internal fun toggledHaStates(states: List<String>, state: String, on: Boolean): List<String> {
    val tidy = tidyHaStates(listOf(state)).firstOrNull() ?: return states
    return if (on) tidyHaStates(states + tidy) else states.filterNot { it.equals(tidy, ignoreCase = true) }
}

private val ON_OFF = listOf("on", "off")

private val HA_DOMAIN_STATES: Map<String, List<String>> = mapOf(
    "media_player" to listOf("playing", "paused", "idle", "on", "off", "standby", "buffering"),
    "binary_sensor" to ON_OFF,
    "switch" to ON_OFF,
    "light" to ON_OFF,
    "input_boolean" to ON_OFF,
    "fan" to ON_OFF,
    "automation" to ON_OFF,
    "remote" to ON_OFF,
    "siren" to ON_OFF,
    "humidifier" to ON_OFF,
    "cover" to listOf("open", "closed", "opening", "closing"),
    "valve" to listOf("open", "closed", "opening", "closing"),
    "lock" to listOf("locked", "unlocked", "jammed"),
    "person" to listOf("home", "not_home"),
    "device_tracker" to listOf("home", "not_home"),
    "climate" to listOf("heat", "cool", "heat_cool", "auto", "dry", "fan_only", "off"),
    "vacuum" to listOf("cleaning", "docked", "returning", "idle", "paused", "error"),
    "alarm_control_panel" to listOf("disarmed", "armed_home", "armed_away", "armed_night", "triggered"),
    "timer" to listOf("active", "paused", "idle"),
    "sun" to listOf("above_horizon", "below_horizon"),
)
