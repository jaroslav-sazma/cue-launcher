package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.ExtractKind
import app.cue.launcher.data.scenario.ExtractRule
import app.cue.launcher.data.scenario.TriggerPayload
import app.cue.launcher.data.scenario.WifiMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ConditionMonitorTest {

    private val scenarios = FakeScenarioSource()

    private val activations = FakeActivationStore()

    private val system = FakeSystemStateSource()

    private val localClock = FakeLocalClock(start = ClockState(isoDayOfWeek = 1, minuteOfDay = 16 * 60 + 59))

    private val haEventLog = FakeHaEventLog()

    private fun TestScope.monitor(zone: ZoneId = ZoneId.systemDefault()): ConditionMonitor {
        val wall = object : WallClock {
            override fun nowMillis(): Long = testScheduler.currentTime

            override fun zone(): ZoneId = zone
        }
        val engine = ScenarioEngine(
            scenarios = scenarios,
            activations = activations,
            clock = wall,
            haStates = HaStateSource.NONE,
        )
        localClock.elapsedMillis = { testScheduler.currentTime }
        return ConditionMonitor(
            scenarios = scenarios,
            activations = activations,
            systemState = system,
            localClock = localClock,
            engine = engine,
            haEventLog = haEventLog,
            clock = wall,
        )
    }

    private fun runningIds() = activations.rows().map(Activation::scenarioId)

    @Test
    fun `the foreground activates a scenario based on system state`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("charging", conditions = listOf(Condition.Charging())))
        system.emit(SystemState(charging = true))

        backgroundScope.launch { monitor.runForeground() }
        runCurrent()

        assertEquals(listOf("charging"), runningIds())
    }

    @Test
    fun `the foreground follows only what enabled scenarios read`() = runTest {
        val monitor = monitor()
        scenarios.emit(
            scenario("charging", conditions = listOf(Condition.Charging())),
            scenario("wifi", conditions = listOf(Condition.Wifi(mode = WifiMode.ANY))).copy(enabled = false),
        )

        backgroundScope.launch { monitor.runForeground() }
        runCurrent()

        assertEquals(listOf(setOf(StateAspect.POWER)), system.asked)
    }

    @Test
    fun `the foreground clears an activation the state no longer confirms`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("charging", conditions = listOf(Condition.Charging())))
        activations.put(Activation(scenarioId = "charging", since = 0L))
        system.emit(SystemState(charging = false))

        backgroundScope.launch { monitor.runForeground() }
        runCurrent()

        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `each foreground pass measures state again`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("charging", conditions = listOf(Condition.Charging())))
        system.emit(SystemState(charging = true))

        val first = backgroundScope.launch { monitor.runForeground() }
        runCurrent()
        assertEquals(1, system.measurements)

        first.cancel()
        system.emit(SystemState(charging = false))
        backgroundScope.launch { monitor.runForeground() }
        runCurrent()

        assertEquals(2, system.measurements)
        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `a state change while running toggles the activation`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("headphones", conditions = listOf(Condition.AudioOutput())))
        system.emit(SystemState(audioOutput = false))
        backgroundScope.launch { monitor.runForeground() }
        runCurrent()
        assertTrue(runningIds().isEmpty())

        system.emit(SystemState(audioOutput = true))
        runCurrent()
        assertEquals(listOf("headphones"), runningIds())

        system.emit(SystemState(audioOutput = false))
        runCurrent()
        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `enabling a scenario activates it without a state change`() = runTest {
        val monitor = monitor()
        val disabledScenario = scenario("charging", conditions = listOf(Condition.Charging())).copy(enabled = false)
        scenarios.emit(disabledScenario)
        system.emit(SystemState(charging = true))
        backgroundScope.launch { monitor.runForeground() }
        runCurrent()
        assertTrue(runningIds().isEmpty())

        scenarios.emit(disabledScenario.copy(enabled = true))
        runCurrent()

        assertEquals(listOf("charging"), runningIds())
    }

    @Test
    fun `a state-based activation has no expiry`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("charging", conditions = listOf(Condition.Charging())))
        system.emit(SystemState(charging = true))

        backgroundScope.launch { monitor.runForeground() }
        runCurrent()

        assertEquals(listOf(null), activations.rows().map(Activation::expiresAt))
    }

    @Test
    fun `an instant-in-time activates a scenario in its own minute`() = runTest {
        val monitor = monitor()
        scenarios.emit(
            scenario("at-five", conditions = listOf(Condition.TimeInstant(at = "17:00", durationSec = 30))),
        )
        system.emit(SystemState())

        backgroundScope.launch { monitor.runForeground() }
        runCurrent()
        assertTrue(runningIds().isEmpty())

        advanceTimeBy(MINUTE)
        runCurrent()

        assertEquals(listOf("at-five"), runningIds())
        assertEquals(listOf(MINUTE + 30_000L), activations.rows().map(Activation::expiresAt))
    }

    @Test
    fun `a time end at the trigger time restarts the tile the next day before the prune runs`() = runTest {
        // Shifted so the wall clock reads 16:59 at the start, as the local one does.
        val monitor = monitor(zone = ZoneOffset.ofHoursMinutes(16, 59))
        val daily = Condition.TimeInstant(at = "17:00", until = EndEvent.AtTime(at = "17:00"))
        scenarios.emit(scenario("daily", conditions = listOf(daily)))
        system.emit(SystemState())
        backgroundScope.launch { monitor.runForeground() }

        advanceTimeBy(MINUTE)
        runCurrent()
        assertEquals(listOf(MINUTE + DAY), activations.rows().map(Activation::expiresAt))

        advanceTimeBy(DAY)
        runCurrent()

        val restarted = activations.rows().single()
        assertEquals(MINUTE + 2 * DAY, restarted.expiresAt)
        assertEquals(MINUTE + DAY, restarted.since)
    }

    @Test
    fun `an instant-in-time respects the state gate`() = runTest {
        val monitor = monitor()
        scenarios.emit(
            scenario(
                "at-five",
                conditions = listOf(Condition.TimeInstant(at = "17:00"), Condition.Charging()),
            ),
        )
        system.emit(SystemState(charging = false))
        backgroundScope.launch { monitor.runForeground() }

        advanceTimeBy(MINUTE)
        runCurrent()

        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `an instant-in-time does not repeat after its minute`() = runTest {
        val monitor = monitor()
        scenarios.emit(
            scenario("at-five", conditions = listOf(Condition.TimeInstant(at = "17:00", durationSec = 30))),
        )
        system.emit(SystemState())
        backgroundScope.launch { monitor.runForeground() }

        advanceTimeBy(MINUTE)
        runCurrent()
        activations.deactivate("at-five")

        advanceTimeBy(2 * MINUTE)
        system.emit(SystemState(charging = true))
        runCurrent()

        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `a bluetooth disconnect activates a scenario even without foreground`() = runTest {
        val monitor = monitor()
        scenarios.emit(
            scenario("parking", conditions = listOf(Condition.BtDisconnected(mac = CAR, durationSec = 600))),
        )

        monitor.onBluetoothDisconnected(CAR)

        assertEquals(listOf("parking"), runningIds())
        assertEquals(listOf(600_000L), activations.rows().map(Activation::expiresAt))
    }

    @Test
    fun `a disconnect without a listener does not measure state`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("charging", conditions = listOf(Condition.Charging())))

        monitor.onBluetoothDisconnected(CAR)

        assertEquals(0, system.measurements)
        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `an HA event activates a scenario even without foreground`() = runTest {
        val monitor = monitor()
        scenarios.emit(
            scenario("doorbell", conditions = listOf(Condition.HaEvent(code = "doorbell", durationSec = 60))),
        )

        monitor.onHaEvent(HaEvent(code = "doorbell"))

        assertEquals(listOf("doorbell"), runningIds())
        assertEquals(listOf(60_000L), activations.rows().map(Activation::expiresAt))
    }

    @Test
    fun `an event without a state condition does not measure state`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("doorbell", conditions = listOf(Condition.HaEvent(code = "doorbell"))))

        monitor.onHaEvent(HaEvent(code = "doorbell"))

        assertEquals(0, system.measurements)
        assertEquals(listOf("doorbell"), runningIds())
    }

    @Test
    fun `an unknown event code activates nothing`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("doorbell", conditions = listOf(Condition.HaEvent(code = "doorbell"))))

        monitor.onHaEvent(HaEvent(code = "washer"))

        assertEquals(0, system.measurements)
        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `an event that matched nobody still goes into the log`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("doorbell", conditions = listOf(Condition.HaEvent(code = "doorbell"))))

        monitor.onHaEvent(HaEvent(code = "washer"))
        monitor.onHaEvent(HaEvent(code = "doorbell"))
        monitor.onHaEvent(HaEvent(code = "doorbell", op = HaEventOp.END))

        assertEquals(
            listOf("washer" to 0, "doorbell" to 1, "doorbell" to 1),
            haEventLog.recorded.map { (event, matched) -> event.code to matched },
        )
    }

    @Test
    fun `an event with a state condition measures the gate`() = runTest {
        val monitor = monitor()
        scenarios.emit(
            scenario(
                "doorbell",
                conditions = listOf(Condition.HaEvent(code = "doorbell"), Condition.Charging()),
            ),
        )
        system.emit(SystemState(charging = false))

        monitor.onHaEvent(HaEvent(code = "doorbell"))

        assertEquals(1, system.measurements)
        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `an event reads only the Home Assistant entities its own listeners are gated by`() = runTest {
        val monitor = monitor()
        scenarios.emit(
            scenario(
                "doorbell",
                conditions = listOf(Condition.HaEvent(code = "doorbell"), Condition.HaState(TV, listOf("on"))),
            ),
            scenario(
                "washer",
                conditions = listOf(Condition.HaEvent(code = "washer"), Condition.HaState(DOOR, listOf("on"))),
            ),
            scenario("door", conditions = listOf(Condition.HaState(DOOR, listOf("off")))),
        )

        monitor.onHaEvent(HaEvent(code = "doorbell"))

        assertEquals(listOf(StateNeeds(setOf(StateAspect.HA), setOf(TV))), system.needed)
    }

    @Test
    fun `an end code does not activate`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("doorbell", conditions = listOf(Condition.HaEvent(code = "doorbell"))))

        monitor.onHaEvent(HaEvent(code = "doorbell", op = HaEventOp.END))

        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `an end code deletes a running activation`() = runTest {
        val monitor = monitor()
        scenarios.emit(
            scenario("doorbell", conditions = listOf(Condition.HaEvent(code = "doorbell", durationSec = 600))),
        )
        monitor.onHaEvent(HaEvent(code = "doorbell"))
        assertEquals(listOf("doorbell"), runningIds())

        monitor.onHaEvent(HaEvent(code = "doorbell", op = HaEventOp.END))

        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `an end code leaves activations of other codes alone`() = runTest {
        val monitor = monitor()
        scenarios.emit(
            scenario("doorbell", conditions = listOf(Condition.HaEvent(code = "doorbell"))),
            scenario("washer", conditions = listOf(Condition.HaEvent(code = "washer"))),
        )
        monitor.onHaEvent(HaEvent(code = "doorbell"))
        monitor.onHaEvent(HaEvent(code = "washer"))

        monitor.onHaEvent(HaEvent(code = "washer", op = HaEventOp.END))

        assertEquals(listOf("doorbell"), runningIds())
    }

    @Test
    fun `an end code does not consult the state gate`() = runTest {
        val monitor = monitor()
        scenarios.emit(
            scenario(
                "doorbell",
                conditions = listOf(Condition.HaEvent(code = "doorbell"), Condition.Charging()),
            ),
        )
        activations.put(Activation(scenarioId = "doorbell", since = 0L, expiresAt = MINUTE))
        system.emit(SystemState(charging = false))

        monitor.onHaEvent(HaEvent(code = "doorbell", op = HaEventOp.END))

        assertTrue(runningIds().isEmpty())
        assertEquals(0, system.measurements)
    }

    @Test
    fun `an event payload is stored with the activation`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("washer", conditions = listOf(Condition.HaEvent(code = "washer"))))

        monitor.onHaEvent(
            HaEvent(code = "washer", icon = "mdi:washing-machine", fields = mapOf("Temperature" to "38")),
        )

        val payload = activations.rows().single().payload
        assertEquals("mdi:washing-machine", payload.icon)
        assertEquals(mapOf("ha:temperature" to "38"), payload.fields)
    }

    @Test
    fun `a repeated event overwrites the payload and moves the window`() = runTest {
        val monitor = monitor()
        scenarios.emit(
            scenario("washer", conditions = listOf(Condition.HaEvent(code = "washer", durationSec = 60))),
        )
        monitor.onHaEvent(HaEvent(code = "washer", fields = mapOf("temperature" to "38")))
        val first = activations.rows().single()

        advanceTimeBy(MINUTE / 2)
        monitor.onHaEvent(HaEvent(code = "washer", fields = mapOf("temperature" to "21")))

        val second = activations.rows().single()
        assertEquals(first.since, second.since)
        assertEquals(first.expiresAt!! + MINUTE / 2, second.expiresAt)
        assertEquals(mapOf("ha:temperature" to "21"), second.payload.fields)
    }

    @Test
    fun `a notification activates a scenario even without foreground`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parcel", conditions = listOf(parcel(durationSec = 300))))

        monitor.onNotificationPosted(parcelNotification())

        assertEquals(listOf("parcel"), runningIds())
        assertEquals(listOf(300_000L), activations.rows().map(Activation::expiresAt))
    }

    @Test
    fun `a notification without a listener does not measure state`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("charging", conditions = listOf(Condition.Charging())))

        monitor.onNotificationPosted(parcelNotification())

        assertEquals(0, system.measurements)
        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `a notification without a state condition does not measure state`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parcel", conditions = listOf(parcel())))

        monitor.onNotificationPosted(parcelNotification())

        assertEquals(0, system.measurements)
        assertEquals(listOf("parcel"), runningIds())
    }

    @Test
    fun `a notification with a state condition measures the gate`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parcel", conditions = listOf(parcel(), Condition.Charging())))
        system.emit(SystemState(charging = false))

        monitor.onNotificationPosted(parcelNotification())

        assertEquals(1, system.measurements)
        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `a notification payload is stored with the activation`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parcel", conditions = listOf(parcel())))

        monitor.onNotificationPosted(parcelNotification())

        assertEquals(
            mapOf("notif:title" to "Zásilkovna", "notif:text" to "Tvůj výdejní kód: 1234"),
            activations.rows().single().payload.fields,
        )
    }

    @Test
    fun `an updated notification overwrites the payload and moves the window`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parcel", conditions = listOf(parcel(durationSec = 60))))
        monitor.onNotificationPosted(parcelNotification())
        val first = activations.rows().single()

        advanceTimeBy(MINUTE / 2)
        monitor.onNotificationPosted(parcelNotification(text = "Balík je připraven"))

        val second = activations.rows().single()
        assertEquals(first.since, second.since)
        assertEquals(first.expiresAt!! + MINUTE / 2, second.expiresAt)
        assertEquals("Balík je připraven", second.payload["notif:text"])
    }

    @Test
    fun `extracted values go into the activation payload`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parcel", conditions = listOf(parcel(extract = listOf(CODE_RULE)))))

        monitor.onNotificationPosted(parcelNotification())

        assertEquals("1234", activations.rows().single().payload["notif:kod"])
    }

    @Test
    fun `each scenario gets a payload per its own rule`() = runTest {
        val monitor = monitor()
        val line = ExtractRule(name = "vse", after = "Zásilkovna", kind = ExtractKind.LINE)
        scenarios.emit(
            scenario("code", conditions = listOf(parcel(extract = listOf(CODE_RULE)))),
            scenario("whole", conditions = listOf(parcel(extract = listOf(line)))),
        )

        monitor.onNotificationPosted(parcelNotification())

        val payloads = activations.rows().associate { it.scenarioId to it.payload }
        assertEquals("1234", payloads.getValue("code")["notif:kod"])
        assertEquals("Tvůj výdejní kód: 1234", payloads.getValue("whole")["notif:vse"])
        assertEquals(null, payloads.getValue("code")["notif:vse"])
    }

    @Test
    fun `a replayed post runs its countdown from when it was posted`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parcel", conditions = listOf(parcel(600, extract = listOf(CODE_RULE)))))
        advanceTimeBy(5 * MINUTE)
        val enriched = mutableListOf<String>()

        monitor.reconcileNotifications(since = MINUTE) {
            NotificationSnapshot(listOf(parcelNotification(key = "a").copy(postTime = 2 * MINUTE))) { posted ->
                posted.also { enriched += it.key }
            }
        }

        val activation = activations.rows().single()
        assertEquals(12 * MINUTE, activation.expiresAt)
        assertEquals("1234", activation.payload["notif:kod"])
        assertEquals("a", activation.payload.notificationKey)
        assertEquals(listOf("a"), enriched)
    }

    @Test
    fun `a replay ends an activation whose notification was dismissed while away`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parcel", conditions = listOf(parcel(durationSec = 600))))
        monitor.onNotificationPosted(parcelNotification(key = "a"))

        monitor.reconcileNotifications(since = MINUTE) { NotificationSnapshot(emptyList()) }

        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `a snapshot that cannot be read ends nothing`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parcel", conditions = listOf(parcel(durationSec = 600))))
        monitor.onNotificationPosted(parcelNotification(key = "a"))

        monitor.reconcileNotifications(since = MINUTE) { null }

        assertEquals(listOf("parcel"), runningIds())
    }

    @Test
    fun `a replay judges a state gate with one measurement`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parcel", conditions = listOf(parcel(durationSec = 600), Condition.Charging())))
        system.emit(SystemState(charging = true))
        advanceTimeBy(5 * MINUTE)

        monitor.reconcileNotifications(since = MINUTE) {
            NotificationSnapshot(listOf(parcelNotification(key = "a").copy(postTime = 2 * MINUTE)))
        }

        assertEquals(1, system.measurements)
        assertEquals(listOf("parcel"), runningIds())
    }

    @Test
    fun `a replay without a notification scenario reads nothing`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("charging", conditions = listOf(Condition.Charging())))

        monitor.reconcileNotifications(since = 0L) { error("not read") }

        assertEquals(0, system.measurements)
        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `an event measures only what the fired scenario's gate reads`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parcel", conditions = listOf(parcel(), Condition.Charging())))

        monitor.onNotificationPosted(parcelNotification())

        assertEquals(listOf(setOf(StateAspect.POWER)), system.asked)
    }

    @Test
    fun `a replay with nothing new to fire measures no state`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parcel", conditions = listOf(parcel(durationSec = 600), Condition.Charging())))
        advanceTimeBy(5 * MINUTE)

        monitor.reconcileNotifications(since = 3 * MINUTE) {
            NotificationSnapshot(listOf(parcelNotification(key = "a").copy(postTime = 2 * MINUTE)))
        }

        assertEquals(0, system.measurements)
        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `a post with the scenarios the listener holds reads none from the store`() = runTest {
        val monitor = monitor()
        val parcel = scenario("parcel", conditions = listOf(parcel()))
        scenarios.emit(parcel)
        val before = scenarios.reads

        monitor.onNotificationPosted(parcelNotification(), known = listOf(parcel))

        assertEquals(before, scenarios.reads)
        assertEquals(listOf("parcel"), runningIds())
    }

    @Test
    fun `cancelling a notification ends the activation`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parcel", conditions = listOf(parcel(durationSec = 600))))
        monitor.onNotificationPosted(parcelNotification())
        assertEquals(listOf("parcel"), runningIds())

        monitor.onNotificationRemoved(parcelNotification(), active = emptyList())

        assertTrue(runningIds().isEmpty())
    }

    @Test
    fun `cancelling a notification does not consult the state gate`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parcel", conditions = listOf(parcel(), Condition.Charging())))
        val fired = TriggerPayload(notificationKey = "first")
        activations.put(Activation(scenarioId = "parcel", since = 0L, expiresAt = MINUTE, payload = fired))
        system.emit(SystemState(charging = false))

        monitor.onNotificationRemoved(parcelNotification(), active = emptyList())

        assertTrue(runningIds().isEmpty())
        assertEquals(0, system.measurements)
    }

    @Test
    fun `a remaining notification leaves the activation running`() = runTest {
        val monitor = monitor()
        scenarios.emit(scenario("parcel", conditions = listOf(parcel(durationSec = 600))))
        monitor.onNotificationPosted(parcelNotification())

        monitor.onNotificationRemoved(
            parcelNotification(),
            active = listOf(parcelNotification(key = "second", title = "Druhá zásilka")),
        )

        assertEquals(listOf("parcel"), runningIds())
    }

    @Test
    fun `a repeated re-evaluation keeps the since timestamp`() = runTest {
        val monitor = monitor()
        val scenario = scenario("charging", conditions = listOf(Condition.Charging()))
        scenarios.emit(scenario)
        val state = SystemState(charging = true)

        monitor.apply(listOf(scenario), state)
        val since = activations.rows().single().since
        monitor.apply(listOf(scenario), state)

        assertEquals(since, activations.rows().single().since)
    }
}

private const val MINUTE = 60_000L

private const val DAY = 24 * 60 * MINUTE

private const val CAR = "AA:BB:CC:DD:EE:FF"

private const val PARCEL_APP = "cz.zasilkovna.app"

private const val TV = "media_player.living_room_tv"

private const val DOOR = "binary_sensor.door"

private val CODE_RULE = ExtractRule(name = "kod", after = "kód", kind = ExtractKind.NUMBER)

private fun parcel(durationSec: Int? = null, extract: List<ExtractRule> = emptyList()): Condition.NotificationFrom =
    Condition.NotificationFrom(packageName = PARCEL_APP, extract = extract, durationSec = durationSec)

private fun parcelNotification(
    key: String = "first",
    title: String? = "Zásilkovna",
    text: String? = "Tvůj výdejní kód: 1234",
): PostedNotification = PostedNotification(
    key = key,
    packageName = PARCEL_APP,
    title = title,
    text = text,
)

private class FakeLocalClock(private val start: ClockState) : LocalClock {

    var elapsedMillis: () -> Long = { 0L }

    override fun reading(): LocalClockReading {
        val elapsed = elapsedMillis()
        val minutes = (start.isoDayOfWeek - 1) * MINUTES_IN_DAY + start.minuteOfDay + elapsed / MINUTE
        return LocalClockReading(
            clock = ClockState(
                isoDayOfWeek = (minutes / MINUTES_IN_DAY % DAYS_IN_WEEK).toInt() + 1,
                minuteOfDay = (minutes % MINUTES_IN_DAY).toInt(),
            ),
            millisIntoMinute = (elapsed % MINUTE).toInt(),
        )
    }

    private companion object {
        const val MINUTES_IN_DAY = 24 * 60
        const val DAYS_IN_WEEK = 7
    }
}
