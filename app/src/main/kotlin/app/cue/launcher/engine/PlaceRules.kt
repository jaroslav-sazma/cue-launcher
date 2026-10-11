package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction

fun placeSavedTrigger(scenario: Scenario): Condition.PlaceSaved? =
    scenario.instantConditions.filterIsInstance<Condition.PlaceSaved>().firstOrNull()

fun placeSavedListeners(scenarios: List<Scenario>): List<Scenario> =
    scenarios.filter { it.enabled && placeSavedTrigger(it) != null }

fun duePlaceSaves(scenarios: List<Scenario>, state: SystemState): List<Scenario> =
    placeSavedListeners(scenarios).filter { instantTriggerAllowed(it, state) }

fun offersToRememberPlace(scenario: Scenario): Boolean = tapActionsOf(scenario).any {
    it is TapAction.SystemAction && it.action == SystemActionType.REMEMBER_PLACE
}

fun usesPlace(scenarios: List<Scenario>): Boolean = scenarios.any(::offersToRememberPlace)

// A tile that asked for the place has been answered once one is saved, so it goes; one that also
// waits for the place is the answer itself and stays.
fun answeredPlacePrompts(scenarios: List<Scenario>, activations: List<Activation>): List<String> {
    val running = activations.mapTo(mutableSetOf(), Activation::scenarioId)
    return scenarios
        .filter { it.id in running && offersToRememberPlace(it) && placeSavedTrigger(it) == null }
        .map(Scenario::id)
}
