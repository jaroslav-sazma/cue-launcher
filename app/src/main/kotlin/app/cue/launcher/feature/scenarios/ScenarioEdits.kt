package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.freeScenarioId

fun duplicateOf(scenario: Scenario, takenIds: Set<String>, name: String): Scenario =
    scenario.copy(id = freeScenarioId(scenario.id, takenIds), name = name)
