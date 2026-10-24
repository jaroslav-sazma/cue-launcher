package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.TriggerPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ActivationRulesTest {

    private val now = 1_700_000_000_000L

    private val zone: ZoneId = ZoneOffset.UTC

    @Test
    fun `a state condition has no expiresAt`() {
        val activation = activationOf(scenario("stav", conditions = listOf(Condition.Charging())), at(now))

        assertEquals(Activation(scenarioId = "stav", since = now, expiresAt = null), activation)
    }

    @Test
    fun `an instant condition expires after its own duration`() {
        val pulse = Condition.HaEvent(code = "brana", durationSec = 30)
        val activation = activationOf(scenario("pulz", conditions = listOf(pulse)), at(now), trigger = pulse)

        assertEquals(now, activation?.since)
        assertEquals(now + 30_000L, activation?.expiresAt)
    }

    @Test
    fun `without a duration the default window applies`() {
        val pulse = Condition.HaEvent(code = "x")
        val activation = activationOf(scenario("pulz", conditions = listOf(pulse)), at(now), trigger = pulse)

        assertEquals(now + DEFAULT_INSTANT_DURATION_SEC * 1_000L, activation?.expiresAt)
    }

    @Test
    fun `a non-positive duration falls back to the default window`() {
        val pulse = Condition.HaEvent(code = "x", durationSec = 0)
        val activation = activationOf(scenario("pulz", conditions = listOf(pulse)), at(now), trigger = pulse)

        assertEquals(now + DEFAULT_INSTANT_DURATION_SEC * 1_000L, activation?.expiresAt)
    }

    @Test
    fun `a repeated trigger only shifts expiresAt`() {
        val pulse = Condition.HaEvent(code = "x", durationSec = 60)
        val scenario = scenario("pulz", conditions = listOf(pulse))
        val first = activationOf(scenario, at(now), trigger = pulse)
        val repeated = activationOf(scenario, at(now + 20_000L), previous = first, trigger = pulse)

        assertEquals(now, repeated?.since)
        assertEquals(now + 20_000L + 60_000L, repeated?.expiresAt)
    }

    @Test
    fun `a state and an instant condition together produce expiresAt`() {
        val pulse = Condition.HaEvent(code = "x", durationSec = 15)
        val activation = activationOf(
            scenario("kombinace", conditions = listOf(Condition.Charging(), pulse)),
            at(now),
            trigger = pulse,
        )

        assertEquals(now + 15_000L, activation?.expiresAt)
    }

    @Test
    fun `a repeated trigger overwrites the payload`() {
        val scenario = scenario("pulz", conditions = listOf(Condition.HaEvent(code = "x", durationSec = 60)))
        val first = activationOf(scenario, at(now), payload = TriggerPayload(fields = mapOf("ha:teplota" to "38")))
        val repeated = activationOf(
            scenario,
            at(now + 20_000L),
            previous = first,
            payload = TriggerPayload(fields = mapOf("ha:teplota" to "21")),
        )

        assertEquals(now, repeated?.since)
        assertEquals(mapOf("ha:teplota" to "21"), repeated?.payload?.fields)
    }

    @Test
    fun `a pulse without a payload has an empty payload`() {
        val activation = activationOf(scenario("stav", conditions = listOf(Condition.Charging())), at(now))

        assertEquals(TriggerPayload.EMPTY, activation?.payload)
    }

    @Test
    fun `a fallback does not activate`() {
        assertNull(activationOf(fallback("trvaly"), at(now)))
    }

    @Test
    fun `a disabled scenario does not activate`() {
        assertNull(activationOf(scenario("vypnuty").copy(enabled = false), at(now)))
    }

    @Test
    fun `an event end without a duration has no expiry and is carried in the payload`() {
        val ends = listOf(
            EndEvent.Alarm,
            EndEvent.BtDisconnected(mac = "AA:BB:CC:DD:EE:FF"),
            EndEvent.Notification(packageName = "com.example", contains = "delivered"),
            EndEvent.HaEvent(code = "washer_done"),
        )

        ends.forEach { until ->
            val pulse = Condition.HaEvent(code = "x", until = until)
            val activation = activationOf(scenario("pulz", conditions = listOf(pulse)), at(now), trigger = pulse)

            assertNull(activation?.expiresAt, "$until must not take the default countdown")
            assertEquals(until, activation?.payload?.until)
        }
    }

    @Test
    fun `a duration beside an event end caps it`() {
        val pulse = Condition.HaEvent(code = "x", durationSec = 600, until = EndEvent.Alarm)
        val activation = activationOf(scenario("pulz", conditions = listOf(pulse)), at(now), trigger = pulse)

        assertEquals(now + 600_000L, activation?.expiresAt)
        assertEquals(EndEvent.Alarm, activation?.payload?.until)
    }

    @Test
    fun `a time end becomes the expiry and is not carried`() {
        val tenAm = ZonedDateTime.of(2026, 10, 19, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val pulse = Condition.HaEvent(code = "x", until = EndEvent.AtTime(at = "17:00"))
        val activation = activationOf(scenario("pulz", conditions = listOf(pulse)), at(tenAm), trigger = pulse)

        assertEquals(tenAm + 7 * HOUR, activation?.expiresAt)
        assertNull(activation?.payload?.until)
    }

    @Test
    fun `the earlier of a time end and the duration wins`() {
        val tenAm = ZonedDateTime.of(2026, 10, 19, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val shortCap = Condition.HaEvent(code = "x", durationSec = 3_600, until = EndEvent.AtTime(at = "17:00"))
        val longCap = Condition.HaEvent(code = "x", durationSec = 86_400, until = EndEvent.AtTime(at = "17:00"))

        val capped = activationOf(scenario("a", conditions = listOf(shortCap)), at(tenAm), trigger = shortCap)
        val timed = activationOf(scenario("b", conditions = listOf(longCap)), at(tenAm), trigger = longCap)

        assertEquals(tenAm + HOUR, capped?.expiresAt)
        assertEquals(tenAm + 7 * HOUR, timed?.expiresAt)
    }

    @Test
    fun `an unreadable time end falls back to the countdown`() {
        val unreadable = Condition.HaEvent(code = "x", until = EndEvent.AtTime(at = "7 pm"))
        val noDays = Condition.HaEvent(
            code = "x",
            durationSec = 30,
            until = EndEvent.AtTime(at = "17:00", days = emptyList()),
        )

        val first = activationOf(scenario("a", conditions = listOf(unreadable)), at(now), trigger = unreadable)
        val second = activationOf(scenario("b", conditions = listOf(noDays)), at(now), trigger = noDays)

        assertEquals(now + DEFAULT_INSTANT_DURATION_SEC * 1_000L, first?.expiresAt)
        assertEquals(now + 30_000L, second?.expiresAt)
        assertNull(first?.payload?.until)
    }

    @Test
    fun `an end that could never arrive falls back to the countdown`() {
        val pulse = Condition.HaEvent(code = "x", until = EndEvent.HaEvent(code = " "))
        val activation = activationOf(scenario("pulz", conditions = listOf(pulse)), at(now), trigger = pulse)

        assertEquals(now + DEFAULT_INSTANT_DURATION_SEC * 1_000L, activation?.expiresAt)
        assertNull(activation?.payload?.until)
    }

    @Test
    fun `an activation no event carries drops an end from the payload it was given`() {
        val stale = TriggerPayload(fields = mapOf("ha:x" to "1"), until = EndEvent.Alarm)
        val state = scenario("stav", conditions = listOf(Condition.Charging()))
        val activation = activationOf(state, at(now), payload = stale)

        assertNull(activation?.payload?.until)
        assertNull(activation?.expiresAt)
        assertEquals(mapOf("ha:x" to "1"), activation?.payload?.fields)
    }

    @Test
    fun `a re-fire keeps since but moves the fire time`() {
        val pulse = Condition.HaEvent(code = "x", until = EndEvent.Alarm)
        val scenario = scenario("pulz", conditions = listOf(pulse))
        val first = activationOf(scenario, at(now), trigger = pulse)
        val repeated = activationOf(scenario, at(now + 20_000L), previous = first, trigger = pulse)

        assertEquals(now, first?.payload?.firedAt)
        assertEquals(now, repeated?.since)
        assertEquals(now + 20_000L, repeated?.payload?.firedAt)
    }

    @Test
    fun `an activation no event carries has no fire time`() {
        val pulse = Condition.HaEvent(code = "x", until = EndEvent.Alarm)
        val scenario = scenario("pulz", conditions = listOf(pulse, Condition.Charging()))
        val fired = requireNotNull(activationOf(scenario, at(now), trigger = pulse))
        val takenOver = activationOf(scenario, at(now + 20_000L), previous = fired, payload = fired.payload)

        assertEquals(now, fired.payload.firedAt)
        assertNull(takenOver?.payload?.firedAt)
        assertNull(takenOver?.payload?.until)
    }

    private fun at(millis: Long): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(zone)

    private companion object {
        const val HOUR = 3_600_000L
    }
}
