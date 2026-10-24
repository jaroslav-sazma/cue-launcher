package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimeRulesTest {

    private val monday = 1
    private val friday = 5
    private val saturday = 6
    private val sunday = 7

    private fun clock(day: Int, hours: Int, minutes: Int = 0) =
        ClockState(isoDayOfWeek = day, minuteOfDay = hours * 60 + minutes)

    private fun reading(day: Int, hours: Int, minutes: Int = 0, millisIntoMinute: Int = 0) =
        LocalClockReading(clock(day, hours, minutes), millisIntoMinute)

    @Test
    fun `time is only parsed in HH mm format`() {
        assertEquals(0, minuteOfDayOf("00:00"))
        assertEquals(18 * 60 + 30, minuteOfDayOf("18:30"))
        assertEquals(23 * 60 + 59, minuteOfDayOf("23:59"))
        assertNull(minuteOfDayOf("24:00"))
        assertNull(minuteOfDayOf("18:60"))
        assertNull(minuteOfDayOf("18"))
        assertNull(minuteOfDayOf("18:00:00"))
        assertNull(minuteOfDayOf("večer"))
    }

    @Test
    fun `a window holds between its start and end`() {
        val evening = Condition.TimeWindow(from = "18:00", to = "23:00")

        assertEquals(ConditionOutcome.HOLDS, timeWindowOutcome(evening, clock(monday, 18)))
        assertEquals(ConditionOutcome.HOLDS, timeWindowOutcome(evening, clock(monday, 22, 59)))
        assertEquals(ConditionOutcome.FAILS, timeWindowOutcome(evening, clock(monday, 17, 59)))
    }

    @Test
    fun `the end of the window no longer belongs to it`() {
        val evening = Condition.TimeWindow(from = "18:00", to = "23:00")

        assertEquals(ConditionOutcome.FAILS, timeWindowOutcome(evening, clock(monday, 23)))
    }

    @Test
    fun `a window holds only on selected days`() {
        val workday = Condition.TimeWindow(from = "06:00", to = "09:00", days = listOf(1, 2, 3, 4, 5))

        assertEquals(ConditionOutcome.HOLDS, timeWindowOutcome(workday, clock(friday, 7)))
        assertEquals(ConditionOutcome.FAILS, timeWindowOutcome(workday, clock(saturday, 7)))
    }

    @Test
    fun `a night window continues into the next day`() {
        val night = Condition.TimeWindow(from = "22:00", to = "06:00", days = listOf(friday))

        assertEquals(ConditionOutcome.HOLDS, timeWindowOutcome(night, clock(friday, 23)))
        assertEquals(ConditionOutcome.HOLDS, timeWindowOutcome(night, clock(saturday, 1)))
        assertEquals(ConditionOutcome.FAILS, timeWindowOutcome(night, clock(saturday, 6)))

        assertEquals(ConditionOutcome.FAILS, timeWindowOutcome(night, clock(saturday, 23)))

        val weekend = Condition.TimeWindow(from = "22:00", to = "06:00", days = listOf(sunday))
        assertEquals(ConditionOutcome.HOLDS, timeWindowOutcome(weekend, clock(monday, 1)))
    }

    @Test
    fun `the same start and end means the whole day`() {
        val allDay = Condition.TimeWindow(from = "08:00", to = "08:00", days = listOf(monday))

        assertEquals(ConditionOutcome.HOLDS, timeWindowOutcome(allDay, clock(monday, 8)))
        assertEquals(ConditionOutcome.HOLDS, timeWindowOutcome(allDay, clock(monday, 23, 59)))
        assertEquals(ConditionOutcome.HOLDS, timeWindowOutcome(allDay, clock(2, 7, 59)))
        assertEquals(ConditionOutcome.FAILS, timeWindowOutcome(allDay, clock(2, 8)))
    }

    @Test
    fun `without a clock and with a broken time the window is unmeasured`() {
        val evening = Condition.TimeWindow(from = "18:00", to = "23:00")

        assertEquals(ConditionOutcome.UNKNOWN, timeWindowOutcome(evening, clock = null))
        assertEquals(
            ConditionOutcome.UNKNOWN,
            timeWindowOutcome(Condition.TimeWindow(from = "18", to = "23:00"), clock(monday, 20)),
        )
    }

    @Test
    fun `an instant occurs in one minute of the selected day`() {
        val instant = Condition.TimeInstant(at = "17:00", days = listOf(1, 2, 3, 4, 5))

        assertTrue(timeInstantDue(instant, clock(friday, 17)))
        assertFalse(timeInstantDue(instant, clock(friday, 17, 1)))
        assertFalse(timeInstantDue(instant, clock(friday, 16, 59)))
        assertFalse(timeInstantDue(instant, clock(saturday, 17)))
        assertFalse(timeInstantDue(instant, clock = null))
    }

    @Test
    fun `it waits for the nearest edge of any window`() {
        val windows = listOf(
            Condition.TimeWindow(from = "18:00", to = "23:00"),
            Condition.TimeWindow(from = "06:00", to = "09:00"),
        )

        assertEquals(30 * 60_000L, timeWindowWaitMillis(windows, reading(monday, 17, 30)))

        assertEquals(60 * 60_000L, timeWindowWaitMillis(windows, reading(monday, 18, 30)))

        assertEquals(30 * 60_000L, timeWindowWaitMillis(windows, reading(monday, 22, 30)))
    }

    @Test
    fun `the wait ends exactly at the edge`() {
        val windows = listOf(Condition.TimeWindow(from = "18:00", to = "23:00"))

        assertEquals(
            5 * 60_000L - 12_345L,
            timeWindowWaitMillis(windows, reading(monday, 17, 55, millisIntoMinute = 12_345)),
        )
    }

    @Test
    fun `an edge in the currently running minute does not repeat immediately`() {
        val windows = listOf(Condition.TimeWindow(from = "18:00", to = "23:00", days = listOf(monday)))

        assertEquals(MAX_TIME_WAIT_MINUTES * 60_000L, timeWindowWaitMillis(windows, reading(monday, 18)))
    }

    @Test
    fun `without a window there is nothing to wait for`() {
        assertNull(timeWindowWaitMillis(emptyList(), reading(monday, 12)))

        assertNull(
            timeWindowWaitMillis(
                listOf(Condition.TimeWindow(from = "18", to = "23:00")),
                reading(monday, 12),
            ),
        )
    }

    @Test
    fun `it waits for the nearest instant`() {
        val instants = listOf(
            Condition.TimeInstant(at = "17:00", days = listOf(1, 2, 3, 4, 5)),
            Condition.TimeInstant(at = "07:30"),
        )

        assertEquals(30 * 60_000L, timeInstantWaitMillis(instants, reading(friday, 7)))

        assertEquals(60 * 60_000L, timeInstantWaitMillis(instants, reading(friday, 17, 30)))
        assertNull(timeInstantWaitMillis(emptyList(), reading(friday, 12)))
    }

    @Test
    fun `schedules take only enabled scenarios`() {
        val on = scenario(
            "zapnuty",
            conditions = listOf(
                Condition.TimeWindow(from = "18:00", to = "23:00"),
                Condition.TimeInstant(at = "17:00"),
            ),
        )
        val off = scenario(
            "vypnuty",
            conditions = listOf(
                Condition.TimeWindow(from = "06:00", to = "09:00"),
                Condition.TimeInstant(at = "07:30"),
            ),
        ).copy(enabled = false)

        assertEquals(1, timeWindowsOf(listOf(on, off)).size)
        assertEquals(1, timeInstantsOf(listOf(on, off)).size)
    }

    @Test
    fun `an instant activates only when the state part holds`() {
        val state = SystemState(clock = clock(monday, 17))
        val pulse = scenario("pulz", conditions = listOf(Condition.TimeInstant(at = "17:00")))
        val gated = scenario(
            "pulz-doma",
            conditions = listOf(Condition.TimeInstant(at = "17:00"), Condition.Charging()),
        )

        assertEquals(listOf("pulz"), dueIds(listOf(pulse, gated), state))
        assertEquals(listOf("pulz"), dueIds(listOf(pulse, gated), state.copy(charging = false)))
        assertEquals(
            listOf("pulz", "pulz-doma"),
            dueIds(listOf(pulse, gated), state.copy(charging = true)),
        )
    }

    @Test
    fun `an instant does not renew a running activation`() {
        val state = SystemState(clock = clock(monday, 17))
        val pulse = scenario("pulz", conditions = listOf(Condition.TimeInstant(at = "17:00")))

        assertEquals(
            emptyList<String>(),
            dueTimeInstants(listOf(pulse), listOf(Activation("pulz", since = 0L)), state, now = 1L)
                .map { it.id },
        )
    }

    @Test
    fun `an expired activation the prune has not reached does not block the instant`() {
        val state = SystemState(clock = clock(monday, 17))
        val pulse = scenario("pulz", conditions = listOf(Condition.TimeInstant(at = "17:00")))
        val expired = Activation("pulz", since = 0L, expiresAt = 1_000L)

        assertEquals(listOf("pulz"), dueTimeInstants(listOf(pulse), listOf(expired), state, now = 1_000L).map { it.id })
        assertEquals(
            emptyList<String>(),
            dueTimeInstants(listOf(pulse), listOf(expired), state, now = 999L).map { it.id },
        )
    }

    @Test
    fun `outside its minute an instant does not activate`() {
        val pulse = scenario("pulz", conditions = listOf(Condition.TimeInstant(at = "17:00")))

        assertTrue(dueIds(listOf(pulse), SystemState(clock = clock(monday, 17, 1))).isEmpty())
        assertTrue(dueIds(listOf(pulse), SystemState()).isEmpty())
    }

    private fun dueIds(scenarios: List<Scenario>, state: SystemState) =
        dueTimeInstants(scenarios, activations = emptyList(), state = state, now = 0L).map { it.id }
}
