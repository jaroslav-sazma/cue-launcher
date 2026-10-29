package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.EndEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ConditionMonitorEndEventTest {

    private val scenarios = FakeScenarioSource()

    private val activations = FakeActivationStore()

    private val system = FakeSystemStateSource()

    private val haEventLog = FakeHaEventLog()

    private fun TestScope.monitor(): ConditionMonitor {
        val clock = object : WallClock {
            override fun nowMillis(): Long = testScheduler.currentTime

            override fun zone(): ZoneId = ZoneOffset.UTC
        }
        return ConditionMonitor(
            scenarios = scenarios,
            activations = activations,
            systemState = system,
            localClock = LocalClock { LocalClockReading(ClockState(isoDayOfWeek = 1, minuteOfDay = 0), 0) },
            engine = ScenarioEngine(scenarios, activations, clock, HaStateSource.NONE, MacLockSource.NONE),
            haEventLog = haEventLog,
            clock = clock,
        )
    }

    private fun runningIds() = activations.rows().map(Activation::scenarioId)

    @Test
    fun `an event with an end waits for it instead of the countdown`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("washer", conditions = listOf(Condition.HaEvent(code = "washer", until = DELIVERED))))

        monitor.onHaEvent(HaEvent(code = "washer"))

        val activation = activations.rows().single()
        assertNull(activation.expiresAt)
        assertEquals(DELIVERED, activation.payload.until)
    }

    @Test
    fun `a time end expires the activation at that time`() = runTest {
        val monitor = monitor()
        val ending = Condition.HaEvent(code = "washer", until = EndEvent.AtTime(at = "00:10"))
        scenarios.emit(scenario("washer", conditions = listOf(ending)))

        monitor.onHaEvent(HaEvent(code = "washer"))

        assertEquals(10 * MINUTE, activations.rows().single().expiresAt)
        assertNull(activations.rows().single().payload.until)
    }

    @Test
    fun `a notification ends the activation waiting for it without any notification scenario`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("washer", conditions = listOf(Condition.HaEvent(code = "washer", until = DELIVERED))))
        monitor.onHaEvent(HaEvent(code = "washer"))

        monitor.onNotificationPosted(post(text = "Still on its way"))
        assertEquals(listOf("washer"), runningIds())

        monitor.onNotificationPosted(post(text = "Delivered to the door"))
        assertTrue(runningIds().isEmpty())
        assertEquals(0, system.measurements)
    }

    @Test
    fun `an end does not consult the state gate`() = runTest {
        val monitor = monitor()
        val gated = scenario(
            "washer",
            conditions = listOf(Condition.HaEvent(code = "washer", until = DELIVERED), Condition.Charging()),
        )
        scenarios.emit(gated)
        system.emit(SystemState(charging = true))
        monitor.onHaEvent(HaEvent(code = "washer"))
        val measured = system.needed.size

        monitor.onNotificationPosted(post(text = "Delivered"))

        assertTrue(runningIds().isEmpty())
        assertEquals(measured, system.needed.size)
    }

    @Test
    fun `an end that is also the trigger restarts the activation`() = runTest {
        val monitor = monitor()
        val restarting = Condition.NotificationFrom(packageName = PARCEL_APP, until = EndEvent.Notification(PARCEL_APP))
        scenarios.emit(scenario("parcel", conditions = listOf(restarting)))
        monitor.onNotificationPosted(post(text = "first"))
        assertEquals(0L, activations.rows().single().since)

        advanceTimeBy(MINUTE)
        monitor.onNotificationPosted(post(text = "second"))

        val restarted = activations.rows().single()
        assertEquals(MINUTE, restarted.since)
        assertEquals(EndEvent.Notification(PARCEL_APP), restarted.payload.until)
    }

    @Test
    fun `a disconnect ends the activation waiting for that device`() = runTest {
        val monitor = monitor()
        val ending = Condition.HaEvent(code = "drive", until = EndEvent.BtDisconnected(mac = CAR))
        scenarios.emit(scenario("drive", conditions = listOf(ending)))
        monitor.onHaEvent(HaEvent(code = "drive"))

        monitor.onBluetoothDisconnected("11:22:33:44:55:66")
        assertEquals(listOf("drive"), runningIds())

        monitor.onBluetoothDisconnected(CAR.lowercase())
        assertTrue(runningIds().isEmpty())
        assertEquals(0, system.measurements)
    }

    @Test
    fun `a disconnect leaves alone an activation that fired again after it was read`() = runTest {
        val monitor = monitor()
        val ending = Condition.HaEvent(code = "drive", until = EndEvent.BtDisconnected(mac = CAR))
        scenarios.emit(scenario("drive", conditions = listOf(ending)))
        monitor.onHaEvent(HaEvent(code = "drive"))
        val first = activations.rows().single()
        val refired = first.copy(payload = first.payload.copy(firedAt = MINUTE))
        activations.afterNextRead = { activations.put(refired) }

        monitor.onBluetoothDisconnected(CAR)

        assertEquals(listOf(refired), activations.rows())
    }

    @Test
    fun `a Home Assistant end leaves alone an activation a state took over after it was read`() = runTest {
        val monitor = monitor()
        val ending = Condition.BtDisconnected(mac = CAR, until = EndEvent.HaEvent(code = "home"))
        scenarios.emit(scenario("parking", conditions = listOf(ending)))
        monitor.onBluetoothDisconnected(CAR)
        val takenOver = Activation("parking", since = activations.rows().single().since)
        activations.afterNextRead = { activations.put(takenOver) }

        monitor.onHaEvent(HaEvent(code = "home"))

        assertEquals(listOf(takenOver), activations.rows())
    }

    @Test
    fun `a replay keeps an activation that fired again after the end post`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("washer", conditions = listOf(Condition.HaEvent(code = "washer", until = DELIVERED))))
        advanceTimeBy(MINUTE)
        monitor.onHaEvent(HaEvent(code = "washer"))
        advanceTimeBy(2 * MINUTE)
        monitor.onHaEvent(HaEvent(code = "washer"))

        monitor.reconcileNotifications(since = MINUTE) {
            NotificationSnapshot(listOf(post(text = "Delivered").copy(postTime = 2 * MINUTE)))
        }

        assertEquals(listOf("washer"), runningIds())
        assertEquals(MINUTE, activations.rows().single().since)
        assertEquals(3 * MINUTE, activations.rows().single().payload.firedAt)
    }

    @Test
    fun `a Home Assistant event ends the activation waiting for it and counts in the log`() = runTest {
        val monitor = monitor()
        val ending = Condition.BtDisconnected(mac = CAR, until = EndEvent.HaEvent(code = "home"))
        scenarios.emit(scenario("parking", conditions = listOf(ending)))
        monitor.onBluetoothDisconnected(CAR)
        assertEquals(listOf("parking"), runningIds())

        monitor.onHaEvent(HaEvent(code = "HOME"))

        assertTrue(runningIds().isEmpty())
        assertEquals(listOf(1), haEventLog.recorded.map { it.second })
    }

    @Test
    fun `an end code ends its listeners and the activations waiting for the code alike`() = runTest {
        val monitor = monitor()
        scenarios.emit(
            scenario("door", conditions = listOf(Condition.HaEvent(code = "door"))),
            scenario("parking", conditions = listOf(Condition.BtDisconnected(until = EndEvent.HaEvent(code = "door")))),
        )
        monitor.onHaEvent(HaEvent(code = "door"))
        monitor.onBluetoothDisconnected(CAR)
        assertEquals(setOf("door", "parking"), runningIds().toSet())

        monitor.onHaEvent(HaEvent(code = "door", op = HaEventOp.END))

        assertTrue(runningIds().isEmpty())
        assertEquals(2, haEventLog.recorded.last().second)
    }

    @Test
    fun `a state taking over drops the end event`() = runTest {
        val monitor = monitor()
        val any = scenario(
            "washer",
            conditions = listOf(Condition.HaEvent(code = "washer", until = DELIVERED), Condition.Charging()),
        ).copy(match = ConditionMatch.ANY)
        scenarios.emit(any)
        system.emit(SystemState(charging = false))
        monitor.onHaEvent(HaEvent(code = "washer"))
        assertEquals(DELIVERED, activations.rows().single().payload.until)

        monitor.apply(listOf(any), SystemState(charging = true))
        assertNull(activations.rows().single().payload.until)
        assertNull(activations.rows().single().expiresAt)

        monitor.onNotificationPosted(post(text = "Delivered"))
        assertEquals(listOf("washer"), runningIds())
    }

    @Test
    fun `a replay ends an activation waiting for a notification posted while away`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("washer", conditions = listOf(Condition.HaEvent(code = "washer", until = DELIVERED))))
        advanceTimeBy(MINUTE)
        monitor.onHaEvent(HaEvent(code = "washer"))

        monitor.reconcileNotifications(since = MINUTE) {
            NotificationSnapshot(listOf(post(text = "Delivered").copy(postTime = 2 * MINUTE)))
        }

        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `a replay ends nothing with a post older than the activation`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("washer", conditions = listOf(Condition.HaEvent(code = "washer", until = DELIVERED))))
        advanceTimeBy(3 * MINUTE)
        monitor.onHaEvent(HaEvent(code = "washer"))

        monitor.reconcileNotifications(since = MINUTE) {
            NotificationSnapshot(listOf(post(text = "Delivered").copy(postTime = 2 * MINUTE)))
        }

        assertEquals(listOf("washer"), runningIds())
    }

    private fun post(text: String) =
        PostedNotification(key = "k", packageName = PARCEL_APP, title = "Parcel", text = text)

    private companion object {
        const val MINUTE = 60_000L
        const val CAR = "AA:BB:CC:DD:EE:FF"
        const val PARCEL_APP = "com.example.parcel"
        val DELIVERED = EndEvent.Notification(packageName = PARCEL_APP, contains = "delivered")
    }
}
