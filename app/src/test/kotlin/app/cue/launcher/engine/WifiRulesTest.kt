package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.WifiMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WifiRulesTest {

    private val home = Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma-5G")

    @Test
    fun `a negated network without a name stays uncertain`() {
        val away = home.copy(negate = true)

        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(away, SystemState(wifi = WifiState(connected = true, ssid = "Hotel"))),
        )
        assertEquals(
            ConditionOutcome.UNKNOWN,
            stateConditionOutcome(away, SystemState(wifi = WifiState(connected = true, ssid = null))),
        )
    }

    @Test
    fun `only a condition on a specific network asks for location`() {
        assertTrue(usesWifiSsid(listOf(scenario("a", conditions = listOf(home)))))
        assertFalse(
            usesWifiSsid(listOf(scenario("b", conditions = listOf(Condition.Wifi(mode = WifiMode.ANY))))),
        )
        assertFalse(usesWifiSsid(listOf(scenario("c", conditions = listOf(Condition.Charging())))))
    }

    @Test
    fun `a negated network needs location too`() {
        assertTrue(usesWifiSsid(listOf(scenario("a", conditions = listOf(home.copy(negate = true))))))
    }

    @Test
    fun `a disabled scenario asks for location too`() {
        assertTrue(usesWifiSsid(listOf(scenario("a", conditions = listOf(home)).copy(enabled = false))))
    }
}
