package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario

fun btDisconnectedMatches(condition: Condition.BtDisconnected, mac: String): Boolean =
    condition.mac?.equals(mac, ignoreCase = true) ?: true

fun btDisconnectTrigger(scenario: Scenario, mac: String): Condition.BtDisconnected? = scenario.instantConditions
    .filterIsInstance<Condition.BtDisconnected>()
    .firstOrNull { btDisconnectedMatches(it, mac) }

fun bluetoothDisconnectListeners(scenarios: List<Scenario>, mac: String): List<Scenario> =
    scenarios.filter { scenario -> scenario.enabled && btDisconnectTrigger(scenario, mac) != null }

fun dueBluetoothDisconnects(scenarios: List<Scenario>, state: SystemState, mac: String): List<Scenario> =
    bluetoothDisconnectListeners(scenarios, mac).filter { instantTriggerAllowed(it, state) }

fun usesBluetooth(scenarios: List<Scenario>): Boolean = scenarios.any { scenario ->
    scenario.conditions.any { it is Condition.BtConnected || it is Condition.BtDisconnected }
}

fun wantsDisconnectReceiver(scenarios: List<Scenario>): Boolean =
    scenarios.any { scenario ->
        scenario.enabled && scenario.instantConditions.any { it is Condition.BtDisconnected }
    }
