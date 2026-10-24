package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.TriggerPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZoneOffset

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

        assertEquals(listOf(wake), dueAlarms(listOf(wake), SystemState(), rangAt = now - 599_000L, now = now, UTC))
        assertEquals(emptyList<Any>(), dueAlarms(listOf(wake), SystemState(), rangAt = now - 600_000L, now = now, UTC))
    }

    @Test
    fun `a ring with an end event and no duration is due however late it is noticed`() {
        val wake = scenario("wake", conditions = listOf(Condition.Alarm(until = EndEvent.HaEvent(code = "left"))))

        assertEquals(listOf(wake), dueAlarms(listOf(wake), SystemState(), rangAt = now - DAY, now = now, UTC))
    }

    @Test
    fun `a ring whose end time has passed by the time it is noticed is not due`() {
        // 2023-11-14 22:13:20 UTC; the next 23:00 comes before now.
        val wake = scenario("wake", conditions = listOf(Condition.Alarm(until = EndEvent.AtTime(at = "23:00"))))

        assertEquals(emptyList<Any>(), dueAlarms(listOf(wake), SystemState(), rangAt = now, now = now + 2 * HOUR, UTC))
        assertEquals(listOf(wake), dueAlarms(listOf(wake), SystemState(), rangAt = now, now = now + HOUR / 2, UTC))
    }

    @Test
    fun `the alarm receiver is also wanted while a running activation waits for the alarm`() {
        val ending = Activation("tea", since = now, payload = TriggerPayload(until = EndEvent.Alarm))
        val other = Activation("tea", since = now, payload = TriggerPayload(until = EndEvent.HaEvent(code = "x")))
        val charging = listOf(scenario("charging", conditions = listOf(Condition.Charging())))

        assertTrue(wantsAlarmReceiver(charging, listOf(ending)))
        assertFalse(wantsAlarmReceiver(charging, listOf(other)))
        assertFalse(wantsAlarmReceiver(charging, emptyList()))
        assertTrue(wantsAlarmReceiver(listOf(scenario("wake", conditions = listOf(Condition.Alarm()))), emptyList()))
    }

    @Test
    fun `a scenario ending on the alarm wants the receiver before anything runs`() {
        val ending = scenario("tea", conditions = listOf(Condition.HaEvent(code = "tea", until = EndEvent.Alarm)))
        val otherEnd = scenario(
            "drive",
            conditions = listOf(Condition.HaEvent(code = "drive", until = EndEvent.BtDisconnected())),
        )

        assertTrue(wantsAlarmReceiver(listOf(ending), emptyList()))
        assertFalse(wantsAlarmReceiver(listOf(ending.copy(enabled = false)), emptyList()))
        assertFalse(wantsAlarmReceiver(listOf(otherEnd), emptyList()))
    }

    @Test
    fun `an alarm ends only the activations that started before it rang`() {
        val before = Activation("before", since = now - 1, payload = TriggerPayload(until = EndEvent.Alarm))
        val after = Activation("after", since = now + 1, payload = TriggerPayload(until = EndEvent.Alarm))
        val countdown = Activation("countdown", since = now - 1, expiresAt = now + HOUR)

        val ended = endedByAlarm(listOf(before, after, countdown), rangAt = now)

        assertEquals(listOf("before"), ended.map(Activation::scenarioId))
    }

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
        val UTC: ZoneId = ZoneOffset.UTC
    }
}
