package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TileSize

enum class ScenarioGroup {
    HERO_4X2,
    MEDIUM_2X2,
    SMALL_1X1,
    FALLBACK,
}

data class ScenarioSection(val group: ScenarioGroup, val scenarios: List<Scenario>)

fun scenarioGroupOf(scenario: Scenario): ScenarioGroup = when {
    scenario.isFallback -> ScenarioGroup.FALLBACK
    scenario.size == TileSize.HERO_4X2 -> ScenarioGroup.HERO_4X2
    scenario.size == TileSize.GRID_2X2 -> ScenarioGroup.MEDIUM_2X2
    else -> ScenarioGroup.SMALL_1X1
}

fun scenarioSections(scenarios: List<Scenario>): List<ScenarioSection> =
    ScenarioGroup.entries.mapNotNull { group ->
        val members = scenarios.filter { scenarioGroupOf(it) == group }
        if (members.isEmpty()) null else ScenarioSection(group, members.sortedWith(SCENARIO_ORDER))
    }

private val SCENARIO_ORDER = compareByDescending<Scenario> { it.priority }
    .thenBy { it.name.lowercase() }
    .thenBy { it.id }
