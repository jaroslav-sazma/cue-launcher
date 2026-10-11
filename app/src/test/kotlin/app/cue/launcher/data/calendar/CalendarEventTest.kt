package app.cue.launcher.data.calendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarEventTest {

    private val prague = ZoneId.of("Europe/Prague")

    private val now = millis("2026-09-17T10:00")

    @Test
    fun `the two nearest are taken and in order`() {
        val afternoon = timed("Afternoon", "2026-09-17T15:00", "2026-09-17T16:00")
        val tomorrow = timed("Tomorrow", "2026-09-18T09:00", "2026-09-18T10:00")
        val nextWeek = timed("Next week", "2026-09-24T09:00", "2026-09-24T10:00")

        assertEquals(
            listOf(afternoon, tomorrow),
            upcomingCalendarEvents(listOf(nextWeek, tomorrow, afternoon), now, prague, limit = 2),
        )
    }

    @Test
    fun `an ongoing or finished event is not shown`() {
        val inProgress = timed("In progress", "2026-09-17T09:30", "2026-09-17T11:00")
        val ended = timed("Ended", "2026-09-17T08:00", "2026-09-17T09:00")
        val upcoming = timed("Starting soon", "2026-09-17T11:00", "2026-09-17T12:00")

        assertEquals(
            listOf(upcoming),
            upcomingCalendarEvents(listOf(inProgress, ended, upcoming), now, prague, limit = 2),
        )
    }

    @Test
    fun `an all-day event starts at local midnight`() {
        val today = allDay("Holiday", "2026-09-17")
        val tomorrow = allDay("Vacation", "2026-09-18")

        assertEquals(
            listOf(tomorrow),
            upcomingCalendarEvents(listOf(today, tomorrow), now, prague, limit = 2),
        )
    }

    @Test
    fun `the order is determined by the start instant, not the reader's order`() {
        val tomorrowAllDay = allDay("Vacation", "2026-09-18")
        val dinner = timed("Dinner", "2026-09-17T19:00", "2026-09-17T21:00")

        assertEquals(
            listOf(dinner, tomorrowAllDay),
            upcomingCalendarEvents(listOf(tomorrowAllDay, dinner), now, prague, limit = 2),
        )
    }

    @Test
    fun `when the start ties, duration decides, then the name`() {
        val longEvent = timed("Long", "2026-09-17T15:00", "2026-09-17T17:00")
        val shortB = timed("B Short", "2026-09-17T15:00", "2026-09-17T16:00")
        val shortA = timed("A Short", "2026-09-17T15:00", "2026-09-17T16:00")

        assertEquals(
            listOf(shortA, shortB, longEvent),
            upcomingCalendarEvents(listOf(longEvent, shortB, shortA), now, prague, limit = 3),
        )
    }

    @Test
    fun `with no events nothing remains`() {
        assertEquals(emptyList<CalendarEvent>(), upcomingCalendarEvents(emptyList(), now, prague, limit = 2))
    }

    private fun timed(title: String, from: String, to: String) = CalendarEvent(
        eventId = title.hashCode().toLong(),
        title = title,
        location = null,
        startMillis = millis(from),
        endMillis = millis(to),
        allDay = false,
    )

    private fun allDay(title: String, day: String): CalendarEvent {
        val start = LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return CalendarEvent(
            eventId = title.hashCode().toLong(),
            title = title,
            location = null,
            startMillis = start,
            endMillis = start + DAY_MILLIS,
            allDay = true,
        )
    }

    private fun millis(local: String): Long =
        LocalDateTime.parse(local).atZone(prague).toInstant().toEpochMilli()

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
