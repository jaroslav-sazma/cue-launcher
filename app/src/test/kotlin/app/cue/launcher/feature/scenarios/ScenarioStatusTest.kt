package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.WifiMode
import app.cue.launcher.engine.SystemState
import app.cue.launcher.engine.WifiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScenarioStatusTest {

    private val home = Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma")

    private val onHome = SystemState(wifi = WifiState(connected = true, ssid = "Doma"))
    private val onHotel = SystemState(wifi = WifiState(connected = true, ssid = "Hotel-Guest"))
    private val unmeasured = SystemState(wifi = WifiState(connected = true, ssid = null))

    @Test
    fun `a disabled scenario reports disabled even when conditions hold`() {
        val subject = scenario(conditions = listOf(home)).copy(enabled = false)

        assertEquals(ScenarioStatus.Disabled, scenarioStatusOf(subject, onHome))
    }

    @Test
    fun `a scenario without conditions is fallback content`() {
        assertEquals(ScenarioStatus.Fallback, scenarioStatusOf(scenario(), onHome))
    }

    @Test
    fun `a failing condition is reported along with which one it is`() {
        val subject = scenario(conditions = listOf(home))

        assertEquals(
            ScenarioStatus.ConditionFails(home),
            scenarioStatusOf(subject, onHotel),
        )
    }

    @Test
    fun `an unmeasured state is reported as unverifiable`() {
        val subject = scenario(conditions = listOf(home))

        assertEquals(
            ScenarioStatus.NotMeasured(home),
            scenarioStatusOf(subject, unmeasured),
        )
    }

    @Test
    fun `a provably failing condition outranks an unmeasured state`() {
        val subject = scenario(conditions = listOf(home, Condition.Charging()))
        val chargingUnknown = onHotel.copy(charging = null)

        assertEquals(
            ScenarioStatus.ConditionFails(home),
            scenarioStatusOf(subject, chargingUnknown),
        )
    }

    @Test
    fun `a scenario with an instant condition waits for an event`() {
        val event = Condition.HaEvent(code = "zvonek")
        val subject = scenario(conditions = listOf(home, event))

        assertEquals(
            ScenarioStatus.WaitsForEvent(event),
            scenarioStatusOf(subject, onHome),
        )
    }

    @Test
    fun `an instant condition with a failing gate reports the gate`() {
        val subject = scenario(conditions = listOf(home, Condition.HaEvent(code = "zvonek")))

        assertEquals(
            ScenarioStatus.ConditionFails(home),
            scenarioStatusOf(subject, onHotel),
        )
    }

    @Test
    fun `valid conditions without an activation report that conditions hold`() {
        val subject = scenario(conditions = listOf(home))

        assertEquals(ScenarioStatus.Holds, scenarioStatusOf(subject, onHome))
    }
}
