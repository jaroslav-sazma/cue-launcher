package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConditionMonitorPlaceTest {

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
            engine = ScenarioEngine(scenarios, activations, clock, HaStateSource.NONE, MacLockSource.NONE),
            haEventLog = FakeHaEventLog(),
            clock = clock,
        )
    }

    private fun runningIds() = activations.rows().map(Activation::scenarioId)

    @Test
    fun `the end of navigation activates a scenario for its own duration`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parking", conditions = listOf(navigationEnded())))
        advanceTimeBy(10 * MINUTE)

        monitor.onNotificationEnded(navigation(), endedAt = 10 * MINUTE)

        assertEquals(listOf("parking"), runningIds())
        assertEquals(listOf(10 * MINUTE + 30 * MINUTE), activations.rows().map(Activation::expiresAt))
        assertNull(activations.rows().single().payload.notificationKey)
    }

    @Test
    fun `a navigation blink activates nothing`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parking", conditions = listOf(navigationEnded())))

        monitor.onNotificationEnded(navigation(), endedAt = 90L)

        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `a blink stays a blink however long it waited in the queue`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parking", conditions = listOf(navigationEnded())))
        advanceTimeBy(10 * MINUTE)

        monitor.onNotificationEnded(navigation(), endedAt = 90L)

        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `the end of navigation respects the state gate`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parking", conditions = listOf(navigationEnded(), Condition.Charging())))
        system.emit(SystemState(charging = false))

        monitor.onNotificationEnded(navigation(), endedAt = 10 * MINUTE)

        assertTrue(runningIds().isEmpty())
        assertEquals(1, system.measurements)
    }

    @Test
    fun `a saved place activates its scenario and closes the tile that asked for it`() = runTest {
        val monitor = monitor()
        scenarios.emit(
            scenario("save", conditions = listOf(navigationEnded()), content = rememberPlace()),
            scenario("where", conditions = listOf(Condition.PlaceSaved(durationSec = 3600))),
        )
        activations.put(Activation(scenarioId = "save", since = 0L, expiresAt = 30 * MINUTE))
        assertTrue(monitor.awaitsPlace())

        assertTrue(monitor.onPlaceSaved(PRAGUE))

        assertEquals(listOf("where"), runningIds())
        assertEquals(PRAGUE, savedPlaceOf(activations.rows().single().payload))
        assertEquals(60 * MINUTE, activations.rows().single().expiresAt)
    }

    @Test
    fun `saving another place replaces it and starts the countdown again`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("where", conditions = listOf(Condition.PlaceSaved(durationSec = 3600))))
        monitor.onPlaceSaved(PRAGUE)

        advanceTimeBy(10 * MINUTE)
        monitor.onPlaceSaved(SavedPlace(48.2, 16.37, savedAt = 10 * MINUTE))

        val row = activations.rows().single()
        assertEquals(48.2, savedPlaceOf(row.payload)?.latitude)
        assertEquals(70 * MINUTE, row.expiresAt)
    }

    @Test
    fun `with nothing waiting for a place the tile asking for one stays`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("save", conditions = listOf(navigationEnded()), content = rememberPlace()))
        activations.put(Activation(scenarioId = "save", since = 0L, expiresAt = 30 * MINUTE))

        assertFalse(monitor.awaitsPlace())
        assertFalse(monitor.onPlaceSaved(PRAGUE))

        assertEquals(listOf("save"), runningIds())
    }

    @Test
    fun `a place its scenario will not take leaves the tile that asked for it`() = runTest {
        val monitor = monitor()
        scenarios.emit(
            scenario("save", conditions = listOf(navigationEnded()), content = rememberPlace()),
            scenario("where", conditions = listOf(Condition.PlaceSaved(durationSec = 3600), Condition.Charging())),
        )
        system.emit(SystemState(charging = false))
        activations.put(Activation(scenarioId = "save", since = 0L, expiresAt = 30 * MINUTE))

        assertFalse(monitor.onPlaceSaved(PRAGUE))

        assertEquals(listOf("save"), runningIds())
    }

    @Test
    fun `a state taking over a remembered place keeps the place`() = runTest {
        val monitor = monitor()
        val where = scenario(
            "where",
            conditions = listOf(Condition.PlaceSaved(durationSec = 3600), Condition.Charging()),
        ).copy(match = ConditionMatch.ANY)
        scenarios.emit(where)
        system.emit(SystemState(charging = false))
        monitor.onPlaceSaved(PRAGUE)

        monitor.apply(listOf(where), SystemState(charging = true))

        val row = activations.rows().single()
        assertNull(row.expiresAt)
        assertEquals(PRAGUE, savedPlaceOf(row.payload))
    }

    @Test
    fun `a state taking over a notification tile drops the notification key`() = runTest {
        val monitor = monitor()
        val parcel = scenario(
            "parcel",
            conditions = listOf(
                Condition.NotificationFrom(packageName = PARCEL_APP, durationSec = 600),
                Condition.Charging(),
            ),
        ).copy(match = ConditionMatch.ANY)
        scenarios.emit(parcel)
        system.emit(SystemState(charging = false))
        monitor.onNotificationPosted(
            PostedNotification(key = "first", packageName = PARCEL_APP, title = "Zásilkovna", text = "Kód 1234"),
        )

        monitor.apply(listOf(parcel), SystemState(charging = true))

        val row = activations.rows().single()
        assertNull(row.payload.notificationKey)
        assertEquals("Zásilkovna", row.payload["notif:title"])
    }
}

private const val MINUTE = 60_000L

private const val MAPS = "com.google.android.apps.maps"

private const val PARCEL_APP = "cz.zasilkovna.app"

private val PRAGUE = SavedPlace(50.08, 14.42, savedAt = 0L)

private fun navigationEnded(): Condition.NotificationEnded = Condition.NotificationEnded(
    packageName = MAPS,
    ongoing = true,
    minLastedSec = 120,
    durationSec = 1800,
)

private fun navigation(): PostedNotification = PostedNotification(
    key = "0|$MAPS|1|null|10172",
    packageName = MAPS,
    title = "Arriving",
    text = "at Taco Bell",
    ongoing = true,
)

private fun rememberPlace(): TileContent = TileContent.Template(
    layout = TemplateLayout.ICON_TEXT,
    items = listOf(TemplateItem(actions = listOf(TapAction.SystemAction(SystemActionType.REMEMBER_PLACE)))),
)
