package app.cue.launcher.data.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class CalendarEvent(
    val eventId: Long,

    val calendarId: Long = 0,

    val calendarColor: Int = 0,
    val title: String?,
    val location: String?,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
)

fun calendarEventDays(event: CalendarEvent, zone: ZoneId): Pair<LocalDate, LocalDate> {
    val eventZone = if (event.allDay) ZoneOffset.UTC else zone
    val first = LocalDate.ofInstant(Instant.ofEpochMilli(event.startMillis), eventZone)
    val lastMillis = if (event.allDay) event.endMillis - 1 else event.endMillis
    if (lastMillis <= event.startMillis) return first to first
    val last = LocalDate.ofInstant(Instant.ofEpochMilli(lastMillis), eventZone)
    return first to maxOf(first, last)
}

fun calendarEventStartMillis(event: CalendarEvent, zone: ZoneId): Long = when {
    event.allDay -> calendarEventDays(event, zone).first.atStartOfDay(zone).toInstant().toEpochMilli()
    else -> event.startMillis
}

const val CALENDAR_TILE_EVENTS: Int = 3

fun upcomingCalendarEvents(
    events: List<CalendarEvent>,
    nowMillis: Long,
    zone: ZoneId,
    limit: Int,
): List<CalendarEvent> = events
    .filter { calendarEventStartMillis(it, zone) > nowMillis }
    .sortedWith(
        compareBy(
            { calendarEventStartMillis(it, zone) },
            CalendarEvent::endMillis,
            { it.title.orEmpty() },
        ),
    )
    .take(limit)
