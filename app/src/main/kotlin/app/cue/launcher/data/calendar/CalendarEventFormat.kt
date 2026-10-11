package app.cue.launcher.data.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

private const val RANGE_SEPARATOR = " – "

private const val DETAIL_SEPARATOR = " · "

data class CalendarEventLines(
    val start: String?,
    val end: String?,
    val detail: String?,
)

fun calendarEventLines(
    event: CalendarEvent,
    zone: ZoneId,
    today: LocalDate,
    dayFormat: DateTimeFormatter,
): CalendarEventLines {
    val (first, last) = calendarEventDays(event, zone)
    val date = when {

        event.allDay && last != first -> dayFormat.format(first) + RANGE_SEPARATOR + dayFormat.format(last)
        else -> dayFormat.format(first).takeUnless { first == today }
    }
    val detail = listOfNotNull(date, event.location).joinToString(DETAIL_SEPARATOR).takeIf(String::isNotEmpty)
    if (event.allDay) return CalendarEventLines(start = null, end = null, detail = detail)
    val start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.startMillis), zone)

    val endMillis = maxOf(event.endMillis, event.startMillis)
    val end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(endMillis), zone)
    return CalendarEventLines(
        start = TIME_FORMAT.format(start),
        end = when (last) {
            first -> TIME_FORMAT.format(end)
            else -> dayFormat.format(end) + " " + TIME_FORMAT.format(end)
        },
        detail = detail,
    )
}
