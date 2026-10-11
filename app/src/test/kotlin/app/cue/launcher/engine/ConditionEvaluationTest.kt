package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.RingerModeState
import app.cue.launcher.data.scenario.WifiMode
import app.cue.launcher.data.scenario.withNegate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConditionEvaluationTest {

    @Test
    fun `an unmeasured state is unknown`() {
        assertEquals(
            ConditionOutcome.UNKNOWN,
            stateConditionOutcome(Condition.Charging(), SystemState()),
        )
    }

    @Test
    fun `charging holds and fails depending on the system`() {
        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(Condition.Charging(), SystemState(charging = true)),
        )
        assertEquals(
            ConditionOutcome.FAILS,
            stateConditionOutcome(Condition.Charging(), SystemState(charging = false)),
        )
    }

    @Test
    fun `headphones read the audio output`() {
        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(Condition.AudioOutput(), SystemState(audioOutput = true)),
        )
    }

    @Test
    fun `bt without an address holds for any connected device`() {
        val state = SystemState(bluetooth = BluetoothState(setOf("AA:BB:CC:DD:EE:FF")))

        assertEquals(ConditionOutcome.HOLDS, stateConditionOutcome(Condition.BtConnected(), state))
        assertEquals(
            ConditionOutcome.FAILS,
            stateConditionOutcome(Condition.BtConnected(), SystemState(bluetooth = BluetoothState())),
        )
    }

    @Test
    fun `bt address comparison ignores case`() {
        val state = SystemState(bluetooth = BluetoothState(setOf("AA:BB:CC:DD:EE:FF")))

        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(Condition.BtConnected(mac = "aa:bb:cc:dd:ee:ff"), state),
        )
        assertEquals(
            ConditionOutcome.FAILS,
            stateConditionOutcome(Condition.BtConnected(mac = "11:22:33:44:55:66"), state),
        )
    }

    @Test
    fun `wifi any mode reads only the connection`() {
        val condition = Condition.Wifi(mode = WifiMode.ANY)

        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(condition, SystemState(wifi = WifiState(connected = true))),
        )
        assertEquals(
            ConditionOutcome.FAILS,
            stateConditionOutcome(condition, SystemState(wifi = WifiState(connected = false))),
        )
    }

    @Test
    fun `negation swaps holds and fails`() {
        val condition = Condition.Wifi(mode = WifiMode.ANY, negate = true)

        assertEquals(
            ConditionOutcome.FAILS,
            stateConditionOutcome(condition, SystemState(wifi = WifiState(connected = true))),
        )
        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(condition, SystemState(wifi = WifiState(connected = false))),
        )
    }

    @Test
    fun `negation swaps the outcome of every state condition`() {
        val state = SystemState(
            charging = true,
            audioOutput = true,
            media = MediaActivity(setOf("com.spotify.music")),
            calendarEvent = true,
            doNotDisturb = true,
            ringerMode = RingerModeState.SILENT,
            bluetooth = BluetoothState(setOf("AA:BB:CC:DD:EE:FF")),
            wifi = WifiState(connected = true, ssid = "Doma-5G"),
            clock = ClockState(minuteOfDay = 19 * MINUTES_PER_HOUR, isoDayOfWeek = 1),
        )

        STATE_CONDITIONS.forEach { condition ->
            val plain = stateConditionOutcome(condition, state)
            val negated = stateConditionOutcome(condition.withNegate(true), state)

            assertEquals(ConditionOutcome.HOLDS, plain) { "condition $condition" }
            assertEquals(ConditionOutcome.FAILS, negated) { "negated condition $condition" }
        }
    }

    @Test
    fun `negation keeps an unmeasured signal unmeasured`() {
        STATE_CONDITIONS.forEach { condition ->
            assertEquals(
                ConditionOutcome.UNKNOWN,
                stateConditionOutcome(condition.withNegate(true), SystemState()),
            ) { "negated condition $condition" }
        }
    }

    @Test
    fun `wifi ssid compares the network name`() {
        val condition = Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma-5G")

        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(condition, SystemState(wifi = WifiState(connected = true, ssid = "Doma-5G"))),
        )
        assertEquals(
            ConditionOutcome.FAILS,
            stateConditionOutcome(condition, SystemState(wifi = WifiState(connected = true, ssid = "Hotel"))),
        )
    }

    @Test
    fun `wifi ssid without permission is unknown`() {
        val condition = Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma-5G")
        val state = SystemState(wifi = WifiState(connected = true, ssid = null))

        assertEquals(ConditionOutcome.UNKNOWN, stateConditionOutcome(condition, state))
    }

    @Test
    fun `wifi ssid without a connection fails even without a network name`() {
        val condition = Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma-5G")
        val state = SystemState(wifi = WifiState(connected = false, ssid = null))

        assertEquals(ConditionOutcome.FAILS, stateConditionOutcome(condition, state))
    }

    @Test
    fun `ringer mode compares the ringing state`() {
        val state = SystemState(ringerMode = RingerModeState.VIBRATE)

        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(Condition.RingerMode(RingerModeState.VIBRATE), state),
        )
        assertEquals(
            ConditionOutcome.FAILS,
            stateConditionOutcome(Condition.RingerMode(RingerModeState.NORMAL), state),
        )
    }

    @Test
    fun `do not disturb is independent of the ringer mode`() {
        val state = SystemState(ringerMode = RingerModeState.VIBRATE, doNotDisturb = true)

        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(Condition.RingerMode(RingerModeState.DND), state),
        )
        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(Condition.RingerMode(RingerModeState.VIBRATE), state),
        )
    }

    @Test
    fun `do not disturb without a measurement is unknown`() {
        val state = SystemState(ringerMode = RingerModeState.NORMAL)

        assertEquals(
            ConditionOutcome.UNKNOWN,
            stateConditionOutcome(Condition.RingerMode(RingerModeState.DND), state),
        )
    }

    @Test
    fun `states without a reader are unknown`() {
        val state = SystemState(charging = true)

        assertEquals(ConditionOutcome.UNKNOWN, stateConditionOutcome(Condition.MediaPlaying(), state))
        assertEquals(ConditionOutcome.UNKNOWN, stateConditionOutcome(Condition.CalendarEvent(), state))
    }

    @Test
    fun `time window reads the hour from the snapshot`() {
        val morning = Condition.TimeWindow(from = "06:00", to = "09:00")
        val monday = { hours: Int -> ClockState(isoDayOfWeek = 1, minuteOfDay = hours * 60) }

        assertEquals(ConditionOutcome.UNKNOWN, stateConditionOutcome(morning, SystemState()))
        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(morning, SystemState(clock = monday(7))),
        )
        assertEquals(
            ConditionOutcome.FAILS,
            stateConditionOutcome(morning, SystemState(clock = monday(10))),
        )
    }

    @Test
    fun `a scenario without state conditions has the gate open`() {
        val scenario = scenario("pulz", conditions = listOf(Condition.HaEvent(code = "x")))

        assertEquals(ConditionOutcome.HOLDS, stateGateOutcome(scenario, SystemState()))
        assertEquals(ConditionOutcome.HOLDS, stateGateOutcome(fallback("trvaly"), SystemState()))
    }

    @Test
    fun `state conditions are combined with and`() {
        val scenario = scenario(
            "and",
            conditions = listOf(Condition.Charging(), Condition.AudioOutput()),
        )

        assertEquals(
            ConditionOutcome.HOLDS,
            stateGateOutcome(scenario, SystemState(charging = true, audioOutput = true)),
        )
        assertEquals(
            ConditionOutcome.FAILS,
            stateGateOutcome(scenario, SystemState(charging = true, audioOutput = false)),
        )
    }

    @Test
    fun `a failing condition overrides an unmeasured one`() {
        val scenario = scenario(
            "and",
            conditions = listOf(Condition.MediaPlaying(), Condition.Charging()),
        )

        assertEquals(ConditionOutcome.FAILS, stateGateOutcome(scenario, SystemState(charging = false)))
        assertEquals(ConditionOutcome.UNKNOWN, stateGateOutcome(scenario, SystemState(charging = true)))
    }

    @Test
    fun `any holds as soon as one condition holds`() {
        val scenario = scenario(
            "or",
            conditions = listOf(Condition.Charging(), Condition.AudioOutput()),
        ).copy(match = ConditionMatch.ANY)

        assertEquals(
            ConditionOutcome.HOLDS,
            stateGateOutcome(scenario, SystemState(charging = true, audioOutput = false)),
        )
        assertEquals(
            ConditionOutcome.FAILS,
            stateGateOutcome(scenario, SystemState(charging = false, audioOutput = false)),
        )
    }

    @Test
    fun `a holding condition outranks an unmeasured one under any`() {
        val scenario = scenario(
            "or",
            conditions = listOf(Condition.MediaPlaying(), Condition.Charging()),
        ).copy(match = ConditionMatch.ANY)

        assertEquals(ConditionOutcome.HOLDS, stateGateOutcome(scenario, SystemState(charging = true)))
        assertEquals(ConditionOutcome.UNKNOWN, stateGateOutcome(scenario, SystemState(charging = false)))
    }

    @Test
    fun `an empty list is the identity of its operator`() {
        assertEquals(ConditionOutcome.HOLDS, conditionMatchOutcome(emptyList(), ConditionMatch.ALL))
        assertEquals(ConditionOutcome.FAILS, conditionMatchOutcome(emptyList(), ConditionMatch.ANY))
    }

    @Test
    fun `under any a failing state condition does not gate the event`() {
        val conditions = listOf(Condition.Charging(), Condition.HaEvent(code = "zvonek"))
        val cold = SystemState(charging = false)

        val any = scenario("or", conditions = conditions).copy(match = ConditionMatch.ANY)

        assertFalse(instantTriggerAllowed(scenario("and", conditions = conditions), cold))
        assertTrue(instantTriggerAllowed(any, cold))
    }

    @Test
    fun `under any a holding state condition strips the expiry off an event`() {
        val fired = Condition.HaEvent(code = "zvonek")
        val conditions = listOf(Condition.Charging(), fired)
        val any = scenario("or", conditions = conditions).copy(match = ConditionMatch.ANY)

        assertNull(activationTrigger(any, SystemState(charging = true), fired))
        assertEquals(fired, activationTrigger(any, SystemState(charging = false), fired))
        assertEquals(fired, activationTrigger(any, SystemState(), fired))
    }

    @Test
    fun `under all the event always brings its own expiry`() {
        val fired = Condition.HaEvent(code = "zvonek")
        val all = scenario("and", conditions = listOf(Condition.Charging(), fired))

        assertEquals(fired, activationTrigger(all, SystemState(charging = true), fired))
    }

    private companion object {

        val STATE_CONDITIONS = listOf(
            Condition.TimeWindow(from = "18:00", to = "23:00"),
            Condition.BtConnected(mac = "AA:BB:CC:DD:EE:FF"),
            Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma-5G"),
            Condition.Charging(),
            Condition.AudioOutput(),
            Condition.MediaPlaying(),
            Condition.RingerMode(RingerModeState.SILENT),
            Condition.RingerMode(RingerModeState.DND),
            Condition.CalendarEvent(),
        )
    }
}
