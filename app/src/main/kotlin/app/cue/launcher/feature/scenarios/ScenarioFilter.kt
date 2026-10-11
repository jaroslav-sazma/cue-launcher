package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.feature.drawer.search.normalizeForSearch

// The list never asks what the home screen currently shows: that answer changes with every
// condition and would have to be recomputed constantly. Scenarios are ordered by priority instead.
enum class ScenarioFilter(val size: TileSize? = null) {
    ALL,
    HERO_4X2(TileSize.HERO_4X2),
    MEDIUM_2X2(TileSize.GRID_2X2),
    SMALL_1X1(TileSize.GRID_1X1),
    DISABLED,
}

fun filterCount(filter: ScenarioFilter, scenarios: List<Scenario>): Int? =
    if (filter == ScenarioFilter.ALL) scenarios.size else null

fun filterScenarios(
    scenarios: List<Scenario>,
    filter: ScenarioFilter,
    query: String,
): List<Scenario> = scenarios
    .filter { scenario -> matchesFilter(scenario, filter) }
    .filter { scenario -> matchesQuery(scenario, query) }

private fun matchesFilter(scenario: Scenario, filter: ScenarioFilter): Boolean =
    when (filter) {
        ScenarioFilter.ALL -> true
        ScenarioFilter.DISABLED -> !scenario.enabled
        else -> scenario.size == filter.size
    }

fun matchesQuery(scenario: Scenario, query: String): Boolean {
    val needle = normalizeForSearch(query)
    return needle.isEmpty() || normalizeForSearch(scenario.name).contains(needle)
}
