package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Condition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AlarmRulesTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `an alarm that left the slot in the past has rung`() {
        assertEquals(now - 1_000L, rungAlarm(remembered = now - 1_000L, upcoming = now + DAY, now = now))
        assertEquals(now - 1_000L, rungAlarm(remembered = now - 1_000L, upcoming = null, now = now))
    }

    @Test
    fun `an alarm that left the slot in the future was removed, not rung`() {
        assertNull(rungAlarm(remembered = now + 1_000L, upcoming = now + DAY, now = now))
        assertNull(rungAlarm(remembered = now + 1_000L, upcoming = null, now = now))
    }

    @Test
    fun `no change in the slot is no ring`() {
        assertNull(rungAlarm(remembered = now - 1_000L, upcoming = now - 1_000L, now = now))
        assertNull(rungAlarm(remembered = null, upcoming = now + DAY, now = now))
    }

    @Test
    fun `only enabled scenarios with an alarm listen to it`() {
        val wake = scenario("wake", conditions = listOf(Condition.Alarm()))
        val off = scenario("off", conditions = listOf(Condition.Alarm())).copy(enabled = false)
        val timed = scenario("timed", conditions = listOf(Condition.TimeInstant(at = "07:00")))

        assertEquals(listOf("wake"), alarmListeners(listOf(wake, off, timed)).map { it.id })
    }

    @Test
    fun `a ring is due only within its own duration`() {
        val wake = scenario("wake", conditions = listOf(Condition.Alarm(durationSec = 600)))

        assertEquals(listOf(wake), dueAlarms(listOf(wake), SystemState(), rangAt = now - 599_000L, now = now))
        assertEquals(emptyList<Any>(), dueAlarms(listOf(wake), SystemState(), rangAt = now - 600_000L, now = now))
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
