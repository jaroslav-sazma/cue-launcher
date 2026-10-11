package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.Scenario

data class ReevaluationPlan(
    val activate: List<Scenario> = emptyList(),
    val deactivate: List<String> = emptyList(),
) {

    val isEmpty: Boolean get() = activate.isEmpty() && deactivate.isEmpty()
}

fun reevaluationPlan(
    scenarios: List<Scenario>,
    activations: List<Activation>,
    state: SystemState,
): ReevaluationPlan {
    val activate = mutableListOf<Scenario>()
    val deactivate = mutableListOf<String>()

    for (scenario in scenarios) {
        val running = activations.firstOrNull { it.scenarioId == scenario.id }
        when (stateVerdict(scenario, running, state)) {

            // A running activation with an expiry is re-activated to drop it: under ANY a state
            // condition has taken over from the event, and it must outlast the event's countdown.
            StateVerdict.ACTIVATE -> if (running == null || running.expiresAt != null) activate += scenario
            StateVerdict.KEEP -> Unit
            StateVerdict.DEACTIVATE -> if (running != null) deactivate += scenario.id
        }
    }

    val known = scenarios.map(Scenario::id).toSet()
    deactivate += activations.map(Activation::scenarioId).filter { it !in known }

    return ReevaluationPlan(activate = activate, deactivate = deactivate)
}

private enum class StateVerdict { ACTIVATE, KEEP, DEACTIVATE }

private fun stateVerdict(scenario: Scenario, running: Activation?, state: SystemState): StateVerdict {
    if (!scenario.enabled || scenario.isFallback) return StateVerdict.DEACTIVATE
    val gate = stateGateOutcome(scenario, state)
    return when {
        scenario.match == ConditionMatch.ANY -> when {
            gate == ConditionOutcome.HOLDS -> StateVerdict.ACTIVATE

            // No state condition holds, so only an event can still be carrying the scenario.
            running?.expiresAt != null -> StateVerdict.KEEP
            else -> StateVerdict.DEACTIVATE
        }

        scenario.instantCondition != null ->
            if (running != null && gate != ConditionOutcome.FAILS) StateVerdict.KEEP else StateVerdict.DEACTIVATE

        else -> if (gate == ConditionOutcome.HOLDS) StateVerdict.ACTIVATE else StateVerdict.DEACTIVATE
    }
}
