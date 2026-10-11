package app.cue.launcher.data.calendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class CalendarEventFormatTest {

    private val prague = ZoneId.of("Europe/Prague")

    private val today = LocalDate.parse("2026-09-17")

    private val dayFormat = DateTimeFormatter.ofPattern("d. M.")

    @Test
    fun `today's event has no date`() {
        val lines = lines(timed(from = "2026-09-17T09:00", to = "2026-09-17T10:30"))

        assertEquals("09:00", lines.start)
        assertEquals("10:30", lines.end)
        assertEquals("Zoom", lines.detail)
    }

    @Test
    fun `a different day adds a date to the detail`() {
        val lines = lines(timed(from = "2026-09-18T09:00", to = "2026-09-18T10:30"))

        assertEquals("09:00", lines.start)
        assertEquals("10:30", lines.end)
        assertEquals("18. 9. · Zoom", lines.detail)
    }

    @Test
    fun `crossing midnight has both an end time and a date`() {
        val lines = lines(timed(from = "2026-09-17T23:00", to = "2026-09-18T01:00"))

        assertEquals("23:00", lines.start)
        assertEquals("18. 9. 01:00", lines.end)
    }

    @Test
    fun `a one-day all-day event has no times`() {
        val lines = lines(allDay(from = "2026-09-18", days = 1))

        assertNull(lines.start)
        assertNull(lines.end)
        assertEquals("18. 9.", lines.detail)
    }

    @Test
    fun `an all-day event spanning multiple days is a date range`() {
        val lines = lines(allDay(from = "2026-09-17", days = 3))

        assertNull(lines.start)
        assertEquals("17. 9. – 19. 9.", lines.detail)
    }

    @Test
    fun `an all-day event starting today keeps its range`() {
        val lines = lines(allDay(from = "2026-09-17", days = 2))

        assertEquals("17. 9. – 18. 9.", lines.detail)
    }

    @Test
    fun `an all-day date does not depend on the time zone`() {
        val event = allDay(from = "2026-09-18", days = 1)

        assertEquals("18. 9.", calendarEventLines(event, ZoneId.of("America/Los_Angeles"), today, dayFormat).detail)
        assertEquals("18. 9.", calendarEventLines(event, ZoneId.of("Pacific/Auckland"), today, dayFormat).detail)
    }

    @Test
    fun `the time is converted to the user's time zone`() {
        val lines = calendarEventLines(timed("2026-09-17T09:00", "2026-09-17T10:30"), ZoneOffset.UTC, today, dayFormat)

        assertEquals("07:00", lines.start)
        assertEquals("08:30", lines.end)
    }

    @Test
    fun `a daylight saving change does not break the range`() {
        val lines = lines(timed(from = "2026-10-25T01:30", to = "2026-10-25T03:30"))

        assertEquals("01:30", lines.start)
        assertEquals("03:30", lines.end)
        assertEquals("25. 10. · Zoom", lines.detail)
    }

    @Test
    fun `an end before the start does not produce a reversed range`() {
        val broken = timed(from = "2026-09-17T09:00", to = "2026-09-17T09:00").copy(endMillis = 0)

        assertEquals("09:00", lines(broken).start)
        assertEquals("09:00", lines(broken).end)

        assertEquals("18. 9.", lines(allDay("2026-09-18", days = 0)).detail)
    }

    @Test
    fun `today's event without a location has no detail`() {
        val event = timed(from = "2026-09-17T09:00", to = "2026-09-17T10:30").copy(location = null)

        assertNull(lines(event).detail)
    }

    private fun lines(event: CalendarEvent): CalendarEventLines = calendarEventLines(event, prague, today, dayFormat)

    private fun allDay(from: String, days: Int): CalendarEvent {
        val start = LocalDate.parse(from).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = LocalDate.parse(from).plusDays(days.toLong())
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return CalendarEvent(
            eventId = 1,
            title = "Dovolená",
            location = null,
            startMillis = start,
            endMillis = end,
            allDay = true,
        )
    }

    private fun timed(from: String, to: String): CalendarEvent = CalendarEvent(
        eventId = 2,
        title = "Stand-up",
        location = "Zoom",
        startMillis = millis(from),
        endMillis = millis(to),
        allDay = false,
    )

    private fun millis(local: String): Long =
        LocalDateTime.parse(local).atZone(prague).toInstant().toEpochMilli()
}
