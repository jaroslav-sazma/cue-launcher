package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario

fun alarmTrigger(scenario: Scenario): Condition.Alarm? =
    scenario.instantConditions.filterIsInstance<Condition.Alarm>().firstOrNull()

fun alarmListeners(scenarios: List<Scenario>): List<Scenario> =
    scenarios.filter { scenario -> scenario.enabled && alarmTrigger(scenario) != null }

fun wantsAlarmReceiver(scenarios: List<Scenario>): Boolean = alarmListeners(scenarios).isNotEmpty()

// The system drops an alarm from the next-alarm-clock slot the moment it rings. A remembered alarm
// that left the slot while still in the future was deleted or moved, not rung.
fun rungAlarm(remembered: Long?, upcoming: Long?, now: Long): Long? =
    remembered?.takeIf { it != upcoming && it <= now }

// The countdown runs from the ring, so a ring noticed late — after a reboot — gets only what is left.
fun dueAlarms(scenarios: List<Scenario>, state: SystemState, rangAt: Long, now: Long): List<Scenario> =
    alarmListeners(scenarios).filter { scenario ->
        val trigger = alarmTrigger(scenario) ?: return@filter false
        now < rangAt + durationMillis(trigger.durationSec) && instantTriggerAllowed(scenario, state)
    }
