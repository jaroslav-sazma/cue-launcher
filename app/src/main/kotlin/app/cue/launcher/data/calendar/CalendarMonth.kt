package app.cue.launcher.data.calendar

import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

const val CALENDAR_GRID_ROWS: Int = 5

const val CALENDAR_GRID_COLUMNS: Int = 7

const val CALENDAR_GRID_CELLS: Int = CALENDAR_GRID_ROWS * CALENDAR_GRID_COLUMNS

const val CALENDAR_GRID_EMPTY: Int = 0

fun calendarMonthGrid(month: YearMonth, firstDayOfWeek: DayOfWeek): List<Int> {
    val cells = MutableList(CALENDAR_GRID_CELLS) { CALENDAR_GRID_EMPTY }
    val offset = weekdayOffset(month.atDay(1).dayOfWeek, firstDayOfWeek)
    for (day in 1..month.lengthOfMonth()) {
        cells[(offset + day - 1) % CALENDAR_GRID_CELLS] = day
    }
    return cells
}

fun calendarWeekdayLabels(firstDayOfWeek: DayOfWeek, locale: Locale): List<String> =
    (0 until CALENDAR_GRID_COLUMNS).map { column ->
        firstDayOfWeek.plus(column.toLong()).getDisplayName(TextStyle.NARROW, locale).uppercase(locale)
    }

fun calendarMonthLabel(month: YearMonth, locale: Locale): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }

fun calendarFirstDayOfWeek(locale: Locale): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

private fun weekdayOffset(day: DayOfWeek, firstDayOfWeek: DayOfWeek): Int =
    Math.floorMod(day.value - firstDayOfWeek.value, CALENDAR_GRID_COLUMNS)
