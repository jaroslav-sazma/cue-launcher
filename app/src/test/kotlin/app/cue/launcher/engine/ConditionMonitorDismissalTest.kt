package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.EndEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConditionMonitorDismissalTest {

    private val scenarios = FakeScenarioSource()

    private val activations = FakeActivationStore()

    private val system = FakeSystemStateSource()

    private fun TestScope.monitor(): ConditionMonitor {
        val clock = WallClock { testScheduler.currentTime }
        return ConditionMonitor(
            scenarios = scenarios,
            activations = activations,
            systemState = system,
            localClock = LocalClock { LocalClockReading(ClockState(isoDayOfWeek = 1, minuteOfDay = 0), 0) },
            engine = ScenarioEngine(scenarios, activations, clock, HaStateSource.NONE),
            haEventLog = FakeHaEventLog(),
            clock = clock,
        )
    }

    private fun runningIds() = activations.rows().map(Activation::scenarioId)

    @Test
    fun `dismissing the notification that fired a tile with an end leaves the tile`() = runTest {
        val monitor = monitor()
        val ends = listOf(
            EndEvent.Notification(packageName = PARCEL_APP, contains = "vydána"),
            EndEvent.AtTime(at = "23:59"),
        )
        ends.forEach { until ->
            scenarios.emit(scenario("parcel", conditions = listOf(parcel().copy(until = until))))
            monitor.onNotificationPosted(parcelNotification())

            monitor.onNotificationRemoved(parcelNotification(), active = emptyList())

            assertEquals(listOf("parcel"), runningIds(), "$until")
            activations.deactivate("parcel")
        }
    }

    @Test
    fun `under ANY a dismissal ends only a tile its own plain condition fired`() = runTest {
        val monitor = monitor()
        val impulse = Condition.NotificationFrom(packageName = WATCH_APP, until = EndEvent.Alarm)
        scenarios.emit(scenario("parcel", conditions = listOf(impulse, parcel())).copy(match = ConditionMatch.ANY))

        monitor.onNotificationPosted(parcelNotification())
        monitor.onNotificationRemoved(watchConnected(), active = listOf(parcelNotification()))
        assertEquals(listOf("parcel"), runningIds(), "the impulse's post is no end")

        monitor.onNotificationRemoved(parcelNotification(), active = listOf(watchConnected()))
        assertTrue(runningIds().isEmpty(), "the impulse's post does not keep the plain tile")

        monitor.onNotificationPosted(watchConnected())
        monitor.onNotificationRemoved(parcelNotification(), active = emptyList())
        assertEquals(listOf("parcel"), runningIds(), "a tile the impulse fired waits for its end")
    }

    @Test
    fun `under ANY a tile an end condition fired while a state holds carries no key`() = runTest {
        val monitor = monitor()
        val impulse = Condition.NotificationFrom(packageName = WATCH_APP, until = WATCH_OFFLINE)
        val any = scenario("watch", conditions = listOf(impulse, parcel(), Condition.Charging()))
        scenarios.emit(any.copy(match = ConditionMatch.ANY))
        system.emit(SystemState(charging = true))

        monitor.onNotificationPosted(watchConnected())
        assertNull(activations.rows().single().payload.notificationKey)

        monitor.onNotificationRemoved(watchConnected(), active = emptyList())
        assertEquals(listOf("watch"), runningIds(), "its own dismissal")
        monitor.onNotificationRemoved(parcelNotification(), active = emptyList())
        assertEquals(listOf("watch"), runningIds(), "a dismissal the plain condition matches")
        monitor.reconcileNotifications(since = MINUTE) { NotificationSnapshot(emptyList()) }
        assertEquals(listOf("watch"), runningIds(), "the replay with the notification gone")
    }

    @Test
    fun `under ANY a plain notification firing while a state holds carries no key`() = runTest {
        val monitor = monitor()
        val any = parcelOrCharging()
        scenarios.emit(any)
        system.emit(SystemState(charging = true))
        monitor.apply(listOf(any), SystemState(charging = true))

        monitor.onNotificationPosted(parcelNotification())
        assertNull(activations.rows().single().payload.notificationKey)

        monitor.onNotificationRemoved(parcelNotification(), active = emptyList())
        assertEquals(listOf("parcel"), runningIds(), "the live dismissal")
        monitor.reconcileNotifications(since = MINUTE) { NotificationSnapshot(emptyList()) }
        assertEquals(listOf("parcel"), runningIds(), "the replay with the notification gone")

        monitor.apply(listOf(any), SystemState(charging = false))
        assertTrue(runningIds().isEmpty(), "the state stops holding")
    }

    @Test
    fun `a plain notification firing with no state holding ends on its dismissal`() = runTest {
        val monitor = monitor()
        val any = parcelOrCharging()
        scenarios.emit(any)
        system.emit(SystemState(charging = false))

        monitor.onNotificationPosted(parcelNotification())
        assertEquals("first", activations.rows().single().payload.notificationKey)

        monitor.onNotificationRemoved(parcelNotification(), active = emptyList())
        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `a tile without a key outlives a dismissal its plain condition matches`() = runTest {
        val monitor = monitor()
        val haFired = scenario("doorbell", conditions = listOf(Condition.HaEvent(code = "doorbell"), parcel()))
        scenarios.emit(haFired.copy(match = ConditionMatch.ANY))
        monitor.onHaEvent(HaEvent(code = "doorbell"))

        monitor.onNotificationRemoved(parcelNotification(), active = emptyList())
        assertEquals(listOf("doorbell"), runningIds(), "fired by an event")

        val takenOver = scenario("parcel", conditions = listOf(parcel(durationSec = 600), Condition.Charging()))
            .copy(match = ConditionMatch.ANY)
        scenarios.emit(takenOver)
        system.emit(SystemState(charging = false))
        monitor.onNotificationPosted(parcelNotification())
        monitor.apply(listOf(takenOver), SystemState(charging = true))
        assertNull(activations.rows().single().payload.notificationKey)

        monitor.onNotificationRemoved(parcelNotification(), active = emptyList())
        assertEquals(listOf("parcel"), runningIds(), "taken over by a state")
    }

    @Test
    fun `a connected tile outlives its dismissal and ends on offline`() = runTest {
        val monitor = monitor()
        val connected = Condition.NotificationFrom(
            packageName = WATCH_APP,
            contains = "connected",
            until = WATCH_OFFLINE,
        )
        scenarios.emit(scenario("watch", conditions = listOf(connected)))

        monitor.onNotificationPosted(watchConnected())
        assertEquals(listOf("watch"), runningIds())

        monitor.onNotificationRemoved(watchConnected(), active = emptyList())
        assertEquals(listOf("watch"), runningIds(), "dismissing connected")

        monitor.onNotificationPosted(watchConnected().copy(title = "Watch offline"))
        assertTrue(runningIds().isEmpty(), "offline")
    }
}

private const val MINUTE = 60_000L

private const val PARCEL_APP = "cz.zasilkovna.app"

private const val WATCH_APP = "com.google.android.apps.wear.companion"

private val WATCH_OFFLINE = EndEvent.Notification(packageName = WATCH_APP, contains = "offline")

private fun parcel(durationSec: Int? = null) =
    Condition.NotificationFrom(packageName = PARCEL_APP, durationSec = durationSec)

private fun parcelOrCharging() =
    scenario("parcel", conditions = listOf(parcel(), Condition.Charging())).copy(match = ConditionMatch.ANY)

private fun parcelNotification() = PostedNotification(
    key = "first",
    packageName = PARCEL_APP,
    title = "Zásilkovna",
    text = "Tvůj výdejní kód: 1234",
)

private fun watchConnected() = PostedNotification(key = "watch", packageName = WATCH_APP, title = "Watch connected")
