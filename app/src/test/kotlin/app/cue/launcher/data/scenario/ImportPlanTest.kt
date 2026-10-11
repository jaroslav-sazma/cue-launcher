package app.cue.launcher.data.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImportPlanTest {

    private fun scenario(id: String, name: String = "Scénář") = Scenario(
        id = id,
        name = name,
        size = TileSize.GRID_1X1,
        content = TileContent.Template(
            layout = TemplateLayout.ICON_TEXT,
            items = listOf(TemplateItem(actions = listOf(TapAction.App(packageName = "com.example")))),
        ),
    )

    @Test
    fun `without a collision, neither id nor state changes`() {
        val plan = importPlanOf(
            read = ScenarioRead.Ok(accepted = listOf(ImportedScenario(scenario("a")))),
            existingIds = setOf("jiny"),
        )

        val entry = plan.entries.single()
        assertEquals("a", entry.scenario.id)
        assertFalse(entry.isCollision)
        assertTrue(entry.scenario.enabled)
        assertTrue(plan.collisions.isEmpty())
        assertTrue(plan.incomplete.isEmpty())
    }

    @Test
    fun `a collision gets a new id and the name stays`() {
        val plan = importPlanOf(
            read = ScenarioRead.Ok(accepted = listOf(ImportedScenario(scenario("a", name = "Ráno")))),
            existingIds = setOf("a"),
        )

        val entry = plan.entries.single()
        assertEquals("a-2", entry.scenario.id)
        assertEquals("a", entry.sourceId)
        assertEquals("Ráno", entry.scenario.name)
        assertTrue(entry.isCollision)
        assertEquals(listOf(entry), plan.collisions)
    }

    @Test
    fun `taken ids are collected even within a single file`() {
        val plan = importPlanOf(
            read = ScenarioRead.Ok(
                accepted = listOf(
                    ImportedScenario(scenario("a")),
                    ImportedScenario(scenario("a")),
                    ImportedScenario(scenario("a")),
                ),
            ),
            existingIds = emptySet(),
        )

        assertEquals(listOf("a", "a-2", "a-3"), plan.scenarios.map(Scenario::id))
    }

    @Test
    fun `a taken sequence number is skipped`() {
        assertEquals("a", freeScenarioId("a", emptySet()))
        assertEquals("a-2", freeScenarioId("a", setOf("a")))
        assertEquals("a-4", freeScenarioId("a", setOf("a", "a-2", "a-3")))
    }

    @Test
    fun `an unfilled placeholder disables the scenario`() {
        val plan = importPlanOf(
            read = ScenarioRead.Ok(
                accepted = listOf(
                    ImportedScenario(scenario("a"), placeholders = setOf("wifi_1")),
                    ImportedScenario(scenario("b")),
                ),
            ),
            existingIds = emptySet(),
        )

        val incomplete = plan.incomplete.single()
        assertEquals("a", incomplete.scenario.id)
        assertFalse(incomplete.scenario.enabled)
        assertEquals(setOf("wifi_1"), incomplete.placeholders)
        assertTrue(plan.entries.first { it.scenario.id == "b" }.scenario.enabled)
    }

    @Test
    fun `rejected scenarios pass into the preview and add nothing`() {
        val rejected = RejectedScenario(id = "x", name = "Vadný", defects = listOf(ScenarioDefect.OverlayUnsupported))
        val plan = importPlanOf(
            read = ScenarioRead.Ok(setName = "Sada", rejected = listOf(rejected)),
            existingIds = emptySet(),
        )

        assertTrue(plan.isEmpty)
        assertEquals(listOf(rejected), plan.rejected)
        assertEquals("Sada", plan.setName)
    }

    @Test
    fun `scenarios from a full config are imported too`() {
        val config = LauncherConfig(drawer = DrawerConfig(contacts = true))
        val read = ScenarioFormat.read(ScenarioFormat.encodeConfig(listOf(scenario("a")), config))

        val plan = importPlanOf(read as ScenarioRead.Ok, existingIds = emptySet())
        assertEquals(listOf("a"), plan.scenarios.map(Scenario::id))
        assertNull(plan.setName)
        assertTrue(plan.hasConfig)
        assertEquals(config, plan.config)
    }

    @Test
    fun `a backup without scenarios is not an empty plan`() {
        val read = ScenarioFormat.read(ScenarioFormat.encodeConfig(emptyList(), LauncherConfig()))

        val plan = importPlanOf(read as ScenarioRead.Ok, existingIds = emptySet())

        assertTrue(plan.scenarios.isEmpty())
        assertFalse(plan.isEmpty)
    }

    @Test
    fun `a scenario set does not carry a config`() {
        val read = ScenarioFormat.read(ScenarioFormat.encodeSet("Sada", listOf(scenario("a"))))

        val plan = importPlanOf(read as ScenarioRead.Ok, existingIds = emptySet())

        assertFalse(plan.hasConfig)
        assertNull(plan.config)
    }
}
