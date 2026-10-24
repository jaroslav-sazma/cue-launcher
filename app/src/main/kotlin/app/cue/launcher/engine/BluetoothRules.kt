package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EndEvent
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
    scenario.conditions.any { it is Condition.BtConnected || it is Condition.BtDisconnected } ||
        scenario.instantConditions.any { it.until is EndEvent.BtDisconnected }
}

// A definition ending on a disconnect counts on its own: a process woken without the home screen
// can fire it while the gate is not following the activations. A running one still counts, for a
// tile that outlives an edit of its scenario.
fun wantsDisconnectReceiver(scenarios: List<Scenario>, activations: List<Activation>): Boolean =
    scenarios.any { scenario ->
        scenario.enabled && scenario.instantConditions.any { it is Condition.BtDisconnected }
    } ||
        scenarios.any { scenario -> scenario.endsBy { it is EndEvent.BtDisconnected } } ||
        activations.any { it.payload.until is EndEvent.BtDisconnected }
