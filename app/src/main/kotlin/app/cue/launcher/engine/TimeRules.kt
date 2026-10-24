package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.WEEK_DAYS

private const val HOURS_PER_DAY = 24

internal const val MINUTES_PER_HOUR = 60
internal const val MINUTES_PER_DAY = HOURS_PER_DAY * MINUTES_PER_HOUR

private const val HOURS_AND_MINUTES = 2

fun minuteOfDayOf(value: String): Int? {
    val parts = value.split(':')
    val hours = parts.getOrNull(0)?.toIntOrNull()
    val minutes = parts.getOrNull(1)?.toIntOrNull()
    if (parts.size != HOURS_AND_MINUTES || hours == null || minutes == null) return null
    return when {
        hours in 0..<HOURS_PER_DAY && minutes in 0..<MINUTES_PER_HOUR -> hours * MINUTES_PER_HOUR + minutes
        else -> null
    }
}

fun timeWindowOutcome(condition: Condition.TimeWindow, clock: ClockState?): ConditionOutcome {
    val span = timeSpanOf(condition)
    return when {
        span == null || clock == null -> ConditionOutcome.UNKNOWN
        span.contains(clock, condition.days) -> ConditionOutcome.HOLDS
        else -> ConditionOutcome.FAILS
    }
}

fun timeInstantDue(condition: Condition.TimeInstant, clock: ClockState?): Boolean =
    clock != null && minuteOfDayOf(condition.at) == clock.minuteOfDay && clock.isoDayOfWeek in condition.days

// An expired activation the prune has not reached yet is not running: a time end equal to the
// trigger time would otherwise skip the day it ends on.
fun dueTimeInstants(
    scenarios: List<Scenario>,
    activations: List<Activation>,
    state: SystemState,
    now: Long,
): List<Scenario> {
    val running = activations.filterNot { it.isExpired(now) }.map(Activation::scenarioId).toSet()
    return scenarios.filter { scenario ->
        scenario.enabled &&
            scenario.id !in running &&
            timeInstantTrigger(scenario, state.clock) != null &&
            instantTriggerAllowed(scenario, state)
    }
}

fun timeInstantTrigger(scenario: Scenario, clock: ClockState?): Condition.TimeInstant? = scenario.instantConditions
    .filterIsInstance<Condition.TimeInstant>()
    .firstOrNull { timeInstantDue(it, clock) }

internal data class TimeSpan(val from: Int, val length: Int) {

    fun contains(clock: ClockState, days: List<Int>): Boolean {
        val sinceStartToday = clock.minuteOfDay - from
        if (sinceStartToday in 0..<length && clock.isoDayOfWeek in days) return true

        val sinceStartYesterday = sinceStartToday + MINUTES_PER_DAY
        return sinceStartYesterday in 0..<length && previousIsoDay(clock.isoDayOfWeek) in days
    }
}

internal fun timeSpanOf(condition: Condition.TimeWindow): TimeSpan? {
    val from = minuteOfDayOf(condition.from)
    val to = minuteOfDayOf(condition.to)
    if (from == null || to == null) return null
    val length = if (to > from) to - from else to - from + MINUTES_PER_DAY
    return TimeSpan(from = from, length = length)
}

private fun previousIsoDay(isoDayOfWeek: Int): Int =
    if (isoDayOfWeek == WEEK_DAYS.first) WEEK_DAYS.last else isoDayOfWeek - 1
