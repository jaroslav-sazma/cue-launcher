package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.WifiMode

fun usesWifiSsid(scenarios: List<Scenario>): Boolean = scenarios.any { scenario ->
    scenario.conditions.any { it is Condition.Wifi && it.mode == WifiMode.SSID }
}
