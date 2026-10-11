package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.TriggerPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ActivationRulesTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `a state condition has no expiresAt`() {
        val activation = activationOf(scenario("stav", conditions = listOf(Condition.Charging())), now)

        assertEquals(Activation(scenarioId = "stav", since = now, expiresAt = null), activation)
    }

    @Test
    fun `an instant condition expires after its own duration`() {
        val pulse = Condition.HaEvent(code = "brana", durationSec = 30)
        val activation = activationOf(scenario("pulz", conditions = listOf(pulse)), now, trigger = pulse)

        assertEquals(now, activation?.since)
        assertEquals(now + 30_000L, activation?.expiresAt)
    }

    @Test
    fun `without a duration the default window applies`() {
        val pulse = Condition.HaEvent(code = "x")
        val activation = activationOf(scenario("pulz", conditions = listOf(pulse)), now, trigger = pulse)

        assertEquals(now + DEFAULT_INSTANT_DURATION_SEC * 1_000L, activation?.expiresAt)
    }

    @Test
    fun `a non-positive duration falls back to the default window`() {
        val pulse = Condition.HaEvent(code = "x", durationSec = 0)
        val activation = activationOf(scenario("pulz", conditions = listOf(pulse)), now, trigger = pulse)

        assertEquals(now + DEFAULT_INSTANT_DURATION_SEC * 1_000L, activation?.expiresAt)
    }

    @Test
    fun `a repeated trigger only shifts expiresAt`() {
        val pulse = Condition.HaEvent(code = "x", durationSec = 60)
        val scenario = scenario("pulz", conditions = listOf(pulse))
        val first = activationOf(scenario, now, trigger = pulse)
        val repeated = activationOf(scenario, now + 20_000L, previous = first, trigger = pulse)

        assertEquals(now, repeated?.since)
        assertEquals(now + 20_000L + 60_000L, repeated?.expiresAt)
    }

    @Test
    fun `a state and an instant condition together produce expiresAt`() {
        val pulse = Condition.HaEvent(code = "x", durationSec = 15)
        val activation = activationOf(
            scenario("kombinace", conditions = listOf(Condition.Charging(), pulse)),
            now,
            trigger = pulse,
        )

        assertEquals(now + 15_000L, activation?.expiresAt)
    }

    @Test
    fun `a repeated trigger overwrites the payload`() {
        val scenario = scenario("pulz", conditions = listOf(Condition.HaEvent(code = "x", durationSec = 60)))
        val first = activationOf(scenario, now, payload = TriggerPayload(fields = mapOf("ha:teplota" to "38")))
        val repeated = activationOf(
            scenario,
            now + 20_000L,
            previous = first,
            payload = TriggerPayload(fields = mapOf("ha:teplota" to "21")),
        )

        assertEquals(now, repeated?.since)
        assertEquals(mapOf("ha:teplota" to "21"), repeated?.payload?.fields)
    }

    @Test
    fun `a pulse without a payload has an empty payload`() {
        val activation = activationOf(scenario("stav", conditions = listOf(Condition.Charging())), now)

        assertEquals(TriggerPayload.EMPTY, activation?.payload)
    }

    @Test
    fun `a fallback does not activate`() {
        assertNull(activationOf(fallback("trvaly"), now))
    }

    @Test
    fun `a disabled scenario does not activate`() {
        assertNull(activationOf(scenario("vypnuty").copy(enabled = false), now))
    }
}
