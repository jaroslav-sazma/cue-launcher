package app.cue.launcher.data.calendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.YearMonth
import java.util.Locale

class CalendarMonthTest {

    private val czech = Locale.forLanguageTag("cs-CZ")

    @Test
    fun `the grid offsets the first day of the month`() {
        val cells = calendarMonthGrid(YearMonth.of(2026, 9), DayOfWeek.MONDAY)

        assertEquals(CALENDAR_GRID_EMPTY, cells[0])
        assertEquals(1, cells[1])
        assertEquals(30, cells[30])
        assertEquals(CALENDAR_GRID_EMPTY, cells[31])
    }

    @Test
    fun `a Sunday week start shifts the grid`() {
        val cells = calendarMonthGrid(YearMonth.of(2026, 9), DayOfWeek.SUNDAY)

        assertEquals(1, cells[2])
        assertEquals(DayOfWeek.SUNDAY, calendarFirstDayOfWeek(Locale.US))
        assertEquals(DayOfWeek.MONDAY, calendarFirstDayOfWeek(czech))
    }

    @Test
    fun `a six-week month wraps into the first week`() {
        val cells = calendarMonthGrid(YearMonth.of(2026, 3), DayOfWeek.MONDAY)

        assertEquals(listOf(30, 31, 0, 0, 0, 0, 1), cells.take(CALENDAR_GRID_COLUMNS))
        assertEquals(29, cells.last())
    }

    @Test
    fun `no month loses or overwrites a day`() {
        for (offset in 0 until MONTHS_CHECKED) {
            val month = YearMonth.of(2024, 1).plusMonths(offset.toLong())
            for (firstDay in DayOfWeek.entries) {
                val cells = calendarMonthGrid(month, firstDay)
                val days = cells.filter { it != CALENDAR_GRID_EMPTY }

                assertEquals(CALENDAR_GRID_CELLS, cells.size, "$month od $firstDay")
                assertEquals((1..month.lengthOfMonth()).toList(), days.sorted(), "$month od $firstDay")
            }
        }
    }

    @Test
    fun `the day letters start with the first day of the week`() {
        val labels = calendarWeekdayLabels(DayOfWeek.MONDAY, czech)

        assertEquals(CALENDAR_GRID_COLUMNS, labels.size)
        assertEquals(listOf("P", "Ú", "S", "Č", "P", "S", "N"), labels)
        assertEquals("N", calendarWeekdayLabels(DayOfWeek.SUNDAY, czech).first())
    }

    @Test
    fun `the month name is in sentence case`() {
        assertEquals("Září", calendarMonthLabel(YearMonth.of(2026, 9), czech))
        assertEquals("September", calendarMonthLabel(YearMonth.of(2026, 9), Locale.US))
    }

    private companion object {

        const val MONTHS_CHECKED = 12 * 8
    }
}
