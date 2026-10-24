package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.WEEK_DAYS
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY
private const val MILLIS_PER_MINUTE = 60_000L

const val MAX_TIME_WAIT_MINUTES = 60

fun timeWindowsOf(scenarios: List<Scenario>): List<Condition.TimeWindow> = scenarios
    .filter(Scenario::enabled)
    .flatMap { it.conditions.filterIsInstance<Condition.TimeWindow>() }

fun timeInstantsOf(scenarios: List<Scenario>): List<Condition.TimeInstant> = scenarios
    .filter(Scenario::enabled)
    .flatMap { it.conditions.filterIsInstance<Condition.TimeInstant>() }

fun timeWindowWaitMillis(windows: List<Condition.TimeWindow>, reading: LocalClockReading): Long? {
    val edges = windows.flatMap { window ->
        val span = timeSpanOf(window) ?: return@flatMap emptyList()
        window.days.filter { it in WEEK_DAYS }.flatMap { day ->
            val start = (day - 1) * MINUTES_PER_DAY + span.from
            listOf(start, start + span.length)
        }
    }
    return waitMillis(edges, reading)
}

fun timeInstantWaitMillis(instants: List<Condition.TimeInstant>, reading: LocalClockReading): Long? {
    val moments = instants.flatMap { instant ->
        val at = minuteOfDayOf(instant.at) ?: return@flatMap emptyList()
        instant.days.filter { it in WEEK_DAYS }.map { day -> (day - 1) * MINUTES_PER_DAY + at }
    }
    return waitMillis(moments, reading)
}

// A time the clock skips over is moved forward by the gap, as java.time resolves it. Null when the
// time cannot be read or no allowed day is left.
fun nextOccurrenceMillis(at: String, days: List<Int>, after: Long, zone: ZoneId): Long? {
    val minute = minuteOfDayOf(at)
    val allowed = days.filter { it in WEEK_DAYS }.toSet()
    if (minute == null || allowed.isEmpty()) return null
    val time = LocalTime.of(minute / MINUTES_PER_HOUR, minute % MINUTES_PER_HOUR)
    val today = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
    return (0..WEEK_DAYS.last).asSequence()
        .map { today.plusDays(it.toLong()) }
        .filter { it.dayOfWeek.value in allowed }
        .map { ZonedDateTime.of(it, time, zone).toInstant().toEpochMilli() }
        .firstOrNull { it > after }
}

private fun waitMillis(minutesOfWeek: List<Int>, reading: LocalClockReading): Long? {
    val now = (reading.clock.isoDayOfWeek - 1) * MINUTES_PER_DAY + reading.clock.minuteOfDay
    val soonest = minutesOfWeek.minOfOrNull { edge ->
        val delta = Math.floorMod(edge - now, MINUTES_PER_WEEK)
        if (delta == 0) MINUTES_PER_WEEK else delta
    } ?: return null
    return minOf(soonest, MAX_TIME_WAIT_MINUTES) * MILLIS_PER_MINUTE - reading.millisIntoMinute
}
