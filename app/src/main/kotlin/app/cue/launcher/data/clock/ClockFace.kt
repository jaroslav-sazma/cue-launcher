package app.cue.launcher.data.clock

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TIME_24_PATTERN = "H:mm"

private const val TIME_12_PATTERN = "h:mm a"

private const val WEEKDAY_PATTERN = "EEEE"

private const val WEEKDAY_SHORT_PATTERN = "EEE"

private const val DAY_PATTERN = "d"

// The format form, not the standalone one: the month follows a day number, so Czech needs "září",
// "listopadu" rather than "září", "listopad".
private const val MONTH_PATTERN = "MMMM"

// The dial is engraved, not written: the numerals stay Roman in every locale.
val CLOCK_DIAL_NUMERALS: List<String> = listOf("XII", "III", "VI", "IX")

val CLOCK_TICK_HOURS: List<Int> = listOf(1, 2, 4, 5, 7, 8, 10, 11)

const val CLOCK_DEGREES_PER_HOUR: Float = 30f

private const val DEGREES_PER_MINUTE = 6f

private const val MINUTES_PER_HOUR = 60f

private const val HOURS_ON_DIAL = 12

fun clockTimeText(time: LocalTime, use24Hour: Boolean, locale: Locale): String =
    DateTimeFormatter.ofPattern(if (use24Hour) TIME_24_PATTERN else TIME_12_PATTERN, locale).format(time)

fun clockDateText(date: LocalDate, pattern: String, locale: Locale): String =
    DateTimeFormatter.ofPattern(pattern, locale).format(date)

data class ClockDateParts(val weekday: String, val day: String, val month: String)

fun clockDateParts(date: LocalDate, locale: Locale): ClockDateParts = ClockDateParts(
    weekday = capitalized(clockDateText(date, WEEKDAY_PATTERN, locale), locale),
    day = clockDateText(date, DAY_PATTERN, locale),
    month = clockDateText(date, MONTH_PATTERN, locale),
)

fun clockShortWeekdayText(date: LocalDate, locale: Locale): String =
    capitalized(clockDateText(date, WEEKDAY_SHORT_PATTERN, locale), locale)

fun clockHourAngle(time: LocalTime): Float =
    ((time.hour % HOURS_ON_DIAL) + time.minute / MINUTES_PER_HOUR) * CLOCK_DEGREES_PER_HOUR

fun clockMinuteAngle(time: LocalTime): Float = time.minute * DEGREES_PER_MINUTE

enum class ClockAlarmDay {
    TODAY,
    TOMORROW,
    LATER,
}

fun clockAlarmDay(today: LocalDate, alarm: LocalDate): ClockAlarmDay = when {
    !alarm.isAfter(today) -> ClockAlarmDay.TODAY
    alarm == today.plusDays(1) -> ClockAlarmDay.TOMORROW
    else -> ClockAlarmDay.LATER
}

private fun capitalized(text: String, locale: Locale): String =
    text.replaceFirstChar { first -> first.titlecase(locale) }
