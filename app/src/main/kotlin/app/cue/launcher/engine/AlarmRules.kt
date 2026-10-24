package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.Scenario
import java.time.ZoneId

fun alarmTrigger(scenario: Scenario): Condition.Alarm? =
    scenario.instantConditions.filterIsInstance<Condition.Alarm>().firstOrNull()

fun alarmListeners(scenarios: List<Scenario>): List<Scenario> =
    scenarios.filter { scenario -> scenario.enabled && alarmTrigger(scenario) != null }

// As with the disconnect receiver, a definition ending on the alarm counts without its activation.
fun wantsAlarmReceiver(scenarios: List<Scenario>, activations: List<Activation>): Boolean =
    alarmListeners(scenarios).isNotEmpty() ||
        scenarios.any { scenario -> scenario.endsBy { it == EndEvent.Alarm } } ||
        activations.any { it.payload.until == EndEvent.Alarm }

// The system drops an alarm from the next-alarm-clock slot the moment it rings. A remembered alarm
// that left the slot while still in the future was deleted or moved, not rung.
fun rungAlarm(remembered: Long?, upcoming: Long?, now: Long): Long? =
    remembered?.takeIf { it != upcoming && it <= now }

// The expiry runs from the ring, so a ring noticed late — after a reboot — gets only what is left.
fun dueAlarms(
    scenarios: List<Scenario>,
    state: SystemState,
    rangAt: Long,
    now: Long,
    zone: ZoneId,
): List<Scenario> = alarmListeners(scenarios).filter { scenario ->
    val trigger = alarmTrigger(scenario) ?: return@filter false
    val expiresAt = instantEndOf(trigger, rangAt, zone).expiresAt
    (expiresAt == null || now < expiresAt) && instantTriggerAllowed(scenario, state)
}
