package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TileSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScenarioFilterTest {

    private val charging = listOf(Condition.Charging())

    private val scenarios = listOf(
        scenario(id = "a", name = "Večer doma", size = TileSize.GRID_2X2, conditions = charging),
        scenario(id = "b", name = "Zvonek", size = TileSize.GRID_1X1, conditions = charging),
        scenario(id = "c", name = "Počasí ráno", size = TileSize.GRID_2X2, conditions = charging)
            .copy(enabled = false),
        scenario(id = "d", name = "Hodiny", size = TileSize.HERO_4X2),
    )

    @Test
    fun `only the All chip carries a count`() {
        assertEquals(4, filterCount(ScenarioFilter.ALL, scenarios))
        assertNull(filterCount(ScenarioFilter.MEDIUM_2X2, scenarios))
        assertNull(filterCount(ScenarioFilter.DISABLED, scenarios))
    }

    @Test
    fun `the All chip lets everything through, including disabled`() {
        val visible = filterScenarios(scenarios, ScenarioFilter.ALL, query = "")

        assertEquals(listOf("a", "b", "c", "d"), visible.map(Scenario::id))
    }

    @Test
    fun `a size chip is not affected by a scenario being disabled`() {
        val visible = filterScenarios(scenarios, ScenarioFilter.MEDIUM_2X2, query = "")

        assertEquals(listOf("a", "c"), visible.map(Scenario::id))
    }

    @Test
    fun `the Disabled chip lets through only disabled ones`() {
        val visible = filterScenarios(scenarios, ScenarioFilter.DISABLED, query = "")

        assertEquals(listOf("c"), visible.map(Scenario::id))
    }

    @Test
    fun `search ignores diacritics and case`() {
        val visible = filterScenarios(scenarios, ScenarioFilter.ALL, query = "VECER")

        assertEquals(listOf("a"), visible.map(Scenario::id))
    }

    @Test
    fun `search matches even part of a word in the middle of the name`() {
        assertTrue(matchesQuery(scenarios[2], "rano"))
    }

    @Test
    fun `an empty query does not filter`() {
        assertTrue(matchesQuery(scenarios[0], "   "))
    }

    @Test
    fun `the filter and search apply at the same time`() {
        val visible = filterScenarios(scenarios, ScenarioFilter.MEDIUM_2X2, query = "pocasi")

        assertEquals(listOf("c"), visible.map(Scenario::id))
    }
}
