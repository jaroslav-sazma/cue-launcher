package app.cue.launcher.data.clock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class ClockFaceTest {

    private val czech = Locale.forLanguageTag("cs")

    @Test
    fun `time in the 24-hour system has no leading zero`() {
        assertEquals("9:41", clockTimeText(LocalTime.of(9, 41), use24Hour = true, locale = czech))
        assertEquals("21:05", clockTimeText(LocalTime.of(21, 5), use24Hour = true, locale = czech))
        assertEquals("0:00", clockTimeText(LocalTime.MIDNIGHT, use24Hour = true, locale = czech))
    }

    @Test
    fun `in the 12-hour system it counts up to twelve`() {
        val odpoledne = clockTimeText(LocalTime.of(21, 5), use24Hour = false, locale = Locale.US)
        val pulnoc = clockTimeText(LocalTime.MIDNIGHT, use24Hour = false, locale = Locale.US)

        assertEquals("9:05 PM", odpoledne)
        assertEquals("12:00 AM", pulnoc)
    }

    @Test
    fun `the date is the weekday and month spelled out`() {
        val date = LocalDate.of(2026, 9, 10)

        assertEquals("čtvrtek 10. září", clockDateText(date, "EEEE d. MMMM", czech))
        assertEquals("Thursday, September 10", clockDateText(date, "EEEE, MMMM d", Locale.US))
    }

    @Test
    @DisplayName("The weekday and the month come from the locale, the weekday with a capital letter")
    fun `the date parts follow the locale`() {
        val date = LocalDate.of(2026, 11, 5)

        assertEquals(ClockDateParts("Čtvrtek", "5", "listopadu"), clockDateParts(date, czech))
        assertEquals(ClockDateParts("Thursday", "5", "November"), clockDateParts(date, Locale.US))
        assertEquals("Čt", clockShortWeekdayText(date, czech))
    }

    @Test
    @DisplayName("The hands stand where the time says")
    fun `the hands follow the time`() {
        assertEquals(305f, clockHourAngle(LocalTime.of(10, 10)))
        assertEquals(60f, clockMinuteAngle(LocalTime.of(10, 10)))
        assertEquals(0f, clockHourAngle(LocalTime.MIDNIGHT))
        assertEquals(15f, clockHourAngle(LocalTime.of(12, 30)))
        assertEquals(0f, clockMinuteAngle(LocalTime.of(12, 0)))
    }

    @Test
    fun `the nearest alarm is told by the day it rings on`() {
        val today = LocalDate.of(2026, 9, 10)

        assertEquals(ClockAlarmDay.TODAY, clockAlarmDay(today, today))
        assertEquals(ClockAlarmDay.TOMORROW, clockAlarmDay(today, today.plusDays(1)))
        assertEquals(ClockAlarmDay.LATER, clockAlarmDay(today, today.plusDays(2)))
    }

    @Test
    @DisplayName("An alarm that is already due reads as today")
    fun `a past alarm stays today`() {
        val today = LocalDate.of(2026, 9, 10)

        assertEquals(ClockAlarmDay.TODAY, clockAlarmDay(today, today.minusDays(1)))
    }
}
