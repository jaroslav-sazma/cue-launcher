package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.engine.ConditionOutcome
import app.cue.launcher.engine.SystemState
import app.cue.launcher.engine.stateConditionOutcome

sealed interface ScenarioStatus {

    data object Disabled : ScenarioStatus

    data object Fallback : ScenarioStatus

    data object Holds : ScenarioStatus

    data class WaitsForEvent(val condition: Condition.InstantCondition) : ScenarioStatus

    data class ConditionFails(val condition: Condition.StateCondition) : ScenarioStatus

    data class NotMeasured(val condition: Condition.StateCondition) : ScenarioStatus

    data object NoConditionHolds : ScenarioStatus
}

fun scenarioStatusOf(scenario: Scenario, state: SystemState): ScenarioStatus = when {
    !scenario.enabled -> ScenarioStatus.Disabled
    scenario.isFallback -> ScenarioStatus.Fallback
    scenario.match == ConditionMatch.ANY -> anyStatusOf(scenario, state)
    else -> allStatusOf(scenario, state)
}

@Suppress("ReturnCount")
private fun allStatusOf(scenario: Scenario, state: SystemState): ScenarioStatus {
    val outcomes = scenario.stateConditions.map { it to stateConditionOutcome(it, state) }
    outcomes.firstOrNull { (_, outcome) -> outcome == ConditionOutcome.FAILS }
        ?.let { (condition, _) -> return ScenarioStatus.ConditionFails(condition) }
    outcomes.firstOrNull { (_, outcome) -> outcome == ConditionOutcome.UNKNOWN }
        ?.let { (condition, _) -> return ScenarioStatus.NotMeasured(condition) }

    val instant = scenario.instantCondition
    return if (instant == null) ScenarioStatus.Holds else ScenarioStatus.WaitsForEvent(instant)
}

// Under ANY no single condition is to blame for the scenario being idle — they all have to miss.
private fun anyStatusOf(scenario: Scenario, state: SystemState): ScenarioStatus {
    val outcomes = scenario.stateConditions.map { it to stateConditionOutcome(it, state) }
    val instant = scenario.instantCondition
    return when {
        outcomes.any { (_, outcome) -> outcome == ConditionOutcome.HOLDS } -> ScenarioStatus.Holds
        instant != null -> ScenarioStatus.WaitsForEvent(instant)
        else -> outcomes.firstOrNull { (_, outcome) -> outcome == ConditionOutcome.UNKNOWN }
            ?.let { (condition, _) -> ScenarioStatus.NotMeasured(condition) }
            ?: ScenarioStatus.NoConditionHolds
    }
}
