package app.cue.launcher.engine

import app.cue.launcher.data.calendar.CalendarEvent
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TileContent

fun usesCalendar(scenarios: List<Scenario>): Boolean = scenarios.any { scenario ->
    scenario.conditions.any { it is Condition.CalendarEvent } || showsCalendarWidget(scenario)
}

private fun showsCalendarWidget(scenario: Scenario): Boolean {
    val content = scenario.content as? TileContent.LauncherWidget ?: return false
    return content.widget == LauncherWidgetType.CALENDAR
}

fun calendarEventOngoing(events: List<CalendarEvent>, nowMillis: Long): Boolean =
    timedEvents(events).any { nowMillis >= it.startMillis && nowMillis < it.endMillis }

fun calendarEdgeWaitMillis(events: List<CalendarEvent>, nowMillis: Long): Long? {
    val edge = timedEvents(events)
        .flatMap { listOf(it.startMillis, it.endMillis) }
        .filter { it > nowMillis }
        .minOrNull() ?: return null
    return minOf(edge - nowMillis, MAX_CALENDAR_WAIT_MILLIS)
}

private fun timedEvents(events: List<CalendarEvent>): List<CalendarEvent> =
    events.filter { !it.allDay && it.endMillis > it.startMillis }

private const val MILLIS_PER_MINUTE = 60_000L

private const val MAX_CALENDAR_WAIT_MILLIS = MAX_TIME_WAIT_MINUTES * MILLIS_PER_MINUTE
