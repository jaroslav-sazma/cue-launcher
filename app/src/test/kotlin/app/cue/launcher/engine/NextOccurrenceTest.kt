package app.cue.launcher.engine

import app.cue.launcher.data.scenario.ALL_WEEK_DAYS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NextOccurrenceTest {

    @Test
    fun `a later time today is today`() {
        val now = at(2026, 10, 19, 10, 0)

        assertEquals(at(2026, 10, 19, 17, 0), nextOccurrenceMillis("17:00", ALL_WEEK_DAYS, now, PRAGUE))
    }

    @Test
    fun `a time already past today wraps to tomorrow`() {
        val now = at(2026, 10, 19, 18, 0)

        assertEquals(at(2026, 10, 20, 17, 0), nextOccurrenceMillis("17:00", ALL_WEEK_DAYS, now, PRAGUE))
    }

    @Test
    fun `only the allowed days count`() {
        val monday = at(2026, 10, 19, 18, 0)

        assertEquals(at(2026, 10, 23, 17, 0), nextOccurrenceMillis("17:00", listOf(FRIDAY), monday, PRAGUE))
        assertEquals(at(2026, 10, 26, 17, 0), nextOccurrenceMillis("17:00", listOf(MONDAY), monday, PRAGUE))
    }

    @Test
    fun `the only allowed day passed today comes again in a week`() {
        val monday = at(2026, 10, 19, 17, 30)

        assertEquals(at(2026, 10, 26, 17, 0), nextOccurrenceMillis("17:00", listOf(MONDAY), monday, PRAGUE))
    }

    @Test
    fun `the very minute of the time is already past`() {
        val exactly = at(2026, 10, 19, 17, 0)

        assertEquals(at(2026, 10, 20, 17, 0), nextOccurrenceMillis("17:00", ALL_WEEK_DAYS, exactly, PRAGUE))
        assertEquals(exactly, nextOccurrenceMillis("17:00", ALL_WEEK_DAYS, exactly - 1, PRAGUE))
    }

    @Test
    fun `a day that loses an hour still ends at the wall clock time`() {
        // Clocks go forward at 02:00 on 2026-03-29 in Prague, so that day is 23 hours long.
        val saturday = at(2026, 3, 28, 18, 0)

        val next = nextOccurrenceMillis("17:00", ALL_WEEK_DAYS, saturday, PRAGUE)

        assertEquals(at(2026, 3, 29, 17, 0), next)
        assertEquals(22 * HOUR, next!! - saturday)
    }

    @Test
    fun `a time the clock skips is moved past the gap, never before now`() {
        val before = at(2026, 3, 29, 1, 0)

        val next = nextOccurrenceMillis("02:30", ALL_WEEK_DAYS, before, PRAGUE)

        assertEquals(at(2026, 3, 29, 3, 30), next)
        assertTrue(next!! > before)
    }

    @Test
    fun `a day that gains an hour still ends at the wall clock time`() {
        // Clocks go back at 03:00 on 2026-10-25 in Prague, so that day is 25 hours long.
        val saturday = at(2026, 10, 24, 18, 0)

        val next = nextOccurrenceMillis("17:00", ALL_WEEK_DAYS, saturday, PRAGUE)

        assertEquals(at(2026, 10, 25, 17, 0), next)
        assertEquals(24 * HOUR, next!! - saturday)
    }

    @Test
    fun `a time the clock passes twice ends at its first pass`() {
        val before = at(2026, 10, 25, 1, 0)

        val next = nextOccurrenceMillis("02:30", ALL_WEEK_DAYS, before, PRAGUE)

        assertEquals(before + 90 * MINUTE, next)
    }

    @Test
    fun `an unreadable time or no allowed day has no next occurrence`() {
        val now = at(2026, 10, 19, 10, 0)

        assertNull(nextOccurrenceMillis("25:00", ALL_WEEK_DAYS, now, PRAGUE))
        assertNull(nextOccurrenceMillis("17:00", emptyList(), now, PRAGUE))
        assertNull(nextOccurrenceMillis("17:00", listOf(0, 8), now, PRAGUE))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, PRAGUE).toInstant().toEpochMilli()

    private companion object {
        val PRAGUE: ZoneId = ZoneId.of("Europe/Prague")
        const val MONDAY = 1
        const val FRIDAY = 5
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
