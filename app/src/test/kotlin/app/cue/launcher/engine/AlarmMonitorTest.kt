package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmMonitorTest {

    private val scenarios = FakeScenarioSource()

    private val activations = FakeActivationStore()

    private val system = FakeSystemStateSource()

    private val alarmMemory = FakeAlarmMemory()

    private fun TestScope.monitor(): AlarmMonitor {
        val clock = WallClock { testScheduler.currentTime }
        return AlarmMonitor(
            scenarios = scenarios,
            systemState = system,
            engine = ScenarioEngine(
                scenarios = scenarios,
                activations = activations,
                clock = clock,
                haStates = HaStateSource.NONE,
            ),
            memory = alarmMemory,
            clock = clock,
        )
    }

    private fun runningIds() = activations.rows().map(Activation::scenarioId)

    @Test
    fun `a rung alarm activates a scenario counted from the ring`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("wake", conditions = listOf(Condition.Alarm(durationSec = HOUR_SEC))))
        alarmMemory.upcoming = ALARM_AT

        advanceTimeBy(ALARM_AT + 5_000L)
        monitor.onNextAlarm { TOMORROW }

        assertEquals(listOf("wake"), runningIds())
        assertEquals(listOf(ALARM_AT + HOUR_SEC * 1_000L), activations.rows().map(Activation::expiresAt))
        assertEquals(TOMORROW, alarmMemory.upcoming)
    }

    @Test
    fun `an alarm removed before it rings activates nothing`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("wake", conditions = listOf(Condition.Alarm(durationSec = HOUR_SEC))))
        alarmMemory.upcoming = ALARM_AT

        monitor.onNextAlarm { null }

        assertTrue(runningIds().isEmpty())
        assertEquals(null, alarmMemory.upcoming)
    }

    @Test
    fun `a snoozed alarm restarts the countdown of the running activation`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("wake", conditions = listOf(Condition.Alarm(durationSec = HOUR_SEC))))
        alarmMemory.upcoming = ALARM_AT
        advanceTimeBy(ALARM_AT)
        monitor.onNextAlarm { null }
        val first = activations.rows().single()

        advanceTimeBy(MINUTE)
        monitor.onNextAlarm { ALARM_AT + SNOOZE }
        advanceTimeBy(SNOOZE - MINUTE)
        monitor.onNextAlarm { null }

        val restarted = activations.rows().single()
        assertEquals(first.since, restarted.since)
        assertEquals(ALARM_AT + SNOOZE + HOUR_SEC * 1_000L, restarted.expiresAt)
    }

    @Test
    fun `a ring noticed after its duration activates nothing`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("wake", conditions = listOf(Condition.Alarm(durationSec = HOUR_SEC))))
        alarmMemory.upcoming = ALARM_AT

        advanceTimeBy(ALARM_AT + HOUR_SEC * 1_000L)
        monitor.onNextAlarm { TOMORROW }

        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `the same alarm change reported twice fires once`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("wake", conditions = listOf(Condition.Alarm(durationSec = HOUR_SEC))))
        alarmMemory.upcoming = ALARM_AT
        advanceTimeBy(ALARM_AT)

        monitor.onNextAlarm { TOMORROW }
        activations.deactivate("wake")
        monitor.onNextAlarm { TOMORROW }

        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `a rung alarm respects the state gate`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("wake", conditions = listOf(Condition.Alarm(), Condition.Charging())))
        system.emit(SystemState(charging = false))
        alarmMemory.upcoming = ALARM_AT
        advanceTimeBy(ALARM_AT)

        monitor.onNextAlarm { TOMORROW }

        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `under ANY a holding state keeps a rung alarm from setting an expiry`() = runTest {
        val monitor = monitor()
        scenarios.emit(
            scenario("wake", conditions = listOf(Condition.Alarm(durationSec = HOUR_SEC), Condition.Charging()))
                .copy(match = ConditionMatch.ANY),
        )
        system.emit(SystemState(charging = true))
        alarmMemory.upcoming = ALARM_AT
        advanceTimeBy(ALARM_AT)

        monitor.onNextAlarm { TOMORROW }

        assertEquals(listOf<Long?>(null), activations.rows().map(Activation::expiresAt))
    }

    @Test
    fun `remembering the next alarm never fires the one remembered before`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("wake", conditions = listOf(Condition.Alarm(durationSec = HOUR_SEC))))
        alarmMemory.upcoming = ALARM_AT
        advanceTimeBy(ALARM_AT)

        monitor.rememberNextAlarm { TOMORROW }
        monitor.onNextAlarm { TOMORROW }

        assertTrue(runningIds().isEmpty())
        assertEquals(TOMORROW, alarmMemory.upcoming)
    }

    @Test
    fun `an alarm change without a listener does not measure state`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("charging", conditions = listOf(Condition.Charging())))
        alarmMemory.upcoming = ALARM_AT
        advanceTimeBy(ALARM_AT)

        monitor.onNextAlarm { TOMORROW }

        assertEquals(0, system.measurements)
        assertEquals(TOMORROW, alarmMemory.upcoming)
    }
}

private class FakeAlarmMemory : AlarmMemory {

    var upcoming: Long? = null

    override suspend fun rememberedAlarm(): Long? = upcoming

    override suspend fun rememberAlarm(upcoming: Long?) {
        this.upcoming = upcoming
    }
}

private const val MINUTE = 60_000L

private const val HOUR_SEC = 3_600

private const val ALARM_AT = 10 * MINUTE

private const val SNOOZE = 10 * MINUTE

private const val TOMORROW = 24 * 60 * MINUTE
