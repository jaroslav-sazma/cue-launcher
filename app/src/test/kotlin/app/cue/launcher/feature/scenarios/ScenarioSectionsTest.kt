package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TileSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScenarioSectionsTest {

    private val charging = listOf(Condition.Charging())

    @Test
    fun `groups go from the largest tile and permanent content is last`() {
        val sections = scenarioSections(
            listOf(
                scenario(id = "a", size = TileSize.GRID_1X1, conditions = charging),
                scenario(id = "b", size = TileSize.GRID_2X2),
                scenario(id = "c", size = TileSize.HERO_4X2, conditions = charging),
                scenario(id = "d", size = TileSize.GRID_2X2, conditions = charging),
            ),
        )

        assertEquals(
            listOf(
                ScenarioGroup.HERO_4X2,
                ScenarioGroup.MEDIUM_2X2,
                ScenarioGroup.SMALL_1X1,
                ScenarioGroup.FALLBACK,
            ),
            sections.map(ScenarioSection::group),
        )
    }

    @Test
    fun `a scenario without conditions belongs to permanent content regardless of size`() {
        val hero = scenario(id = "h", size = TileSize.HERO_4X2)

        assertEquals(ScenarioGroup.FALLBACK, scenarioGroupOf(hero))
    }

    @Test
    fun `within a group, order is by priority descending`() {
        val sections = scenarioSections(
            listOf(
                scenario(id = "a", priority = 40, conditions = charging),
                scenario(id = "b", priority = 90, conditions = charging),
                scenario(id = "c", priority = 70, conditions = charging),
            ),
        )

        assertEquals(listOf("b", "c", "a"), sections.single().scenarios.map(Scenario::id))
    }

    @Test
    fun `on equal priority, the name decides`() {
        val sections = scenarioSections(
            listOf(
                scenario(id = "a", name = "Zvonek", priority = 50, conditions = charging),
                scenario(id = "b", name = "Auto", priority = 50, conditions = charging),
            ),
        )

        assertEquals(listOf("b", "a"), sections.single().scenarios.map(Scenario::id))
    }

    @Test
    fun `on equal priority and name, the id decides`() {
        val first = scenario(id = "a", name = "Svítilna", priority = 30, conditions = charging)
        val second = scenario(id = "b", name = "Svítilna", priority = 30, conditions = charging)

        val forward = scenarioSections(listOf(first, second)).single().scenarios
        val backward = scenarioSections(listOf(second, first)).single().scenarios

        assertEquals(listOf("a", "b"), forward.map(Scenario::id))
        assertEquals(forward, backward)
    }

    @Test
    fun `an empty group is not listed`() {
        val sections = scenarioSections(listOf(scenario(id = "a", conditions = charging)))

        assertEquals(listOf(ScenarioGroup.MEDIUM_2X2), sections.map(ScenarioSection::group))
    }

    @Test
    fun `an empty list has no group`() {
        assertEquals(emptyList<ScenarioSection>(), scenarioSections(emptyList()))
    }
}
