package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.ConditionType
import app.cue.launcher.data.scenario.ScenarioFormat
import app.cue.launcher.data.scenario.ScenarioRead
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.WifiMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScenarioDraftTest {

    @Test
    fun `the next step needs a condition or persistent content`() {
        assertFalse(ScenarioDraft().canContinue)
        assertTrue(ScenarioDraft().withCondition(Condition.Charging()).canContinue)
        assertTrue(ScenarioDraft().asFallback().canContinue)
    }

    @Test
    fun `persistent content and conditions are mutually exclusive`() {
        val fallback = ScenarioDraft().withCondition(Condition.Charging()).asFallback()
        assertTrue(fallback.conditions.isEmpty())

        val conditioned = fallback.withCondition(Condition.AudioOutput())
        assertFalse(conditioned.fallback)
        assertEquals(listOf(Condition.AudioOutput()), conditioned.conditions)
    }

    @Test
    fun `under any a second instant condition is added instead of replacing the first`() {
        val zvonek = Condition.HaEvent(code = "zvonek")
        val brana = Condition.HaEvent(code = "brana")
        val draft = ScenarioDraft(match = ConditionMatch.ANY, conditions = listOf(zvonek, Condition.Charging()))
            .withCondition(brana)

        assertEquals(listOf(zvonek, Condition.Charging(), brana), draft.conditions)
        assertEquals(CatalogAvailability.AVAILABLE, catalogAvailability(draft, ConditionType.TIME_INSTANT))
    }

    @Test
    fun `all is refused while a second instant condition is around`() {
        val draft = ScenarioDraft()
            .withCondition(Condition.HaEvent(code = "zvonek"))
            .withCondition(Condition.HaEvent(code = "brana"))
            .withCondition(Condition.Charging())

        assertFalse(draft.acceptsMatchAll)
        assertEquals(ConditionMatch.ANY, draft.withMatch(ConditionMatch.ALL).match)

        val single = draft.withoutConditionAt(1)

        assertEquals(ConditionMatch.ANY, single.match)
        assertTrue(single.acceptsMatchAll)
        assertEquals(ConditionMatch.ALL, single.withMatch(ConditionMatch.ALL).match)
    }

    @Test
    fun `a second instant condition is blocked in the catalog once all is in force`() {
        val draft = ScenarioDraft()
            .withCondition(Condition.HaEvent(code = "zvonek"))
            .withCondition(Condition.Charging())

        assertEquals(
            CatalogAvailability.BLOCKED_BY_INSTANT,
            catalogAvailability(draft, ConditionType.TIME_INSTANT),
        )
        assertEquals(
            CatalogAvailability.AVAILABLE,
            catalogAvailability(draft, ConditionType.WIFI),
        )
    }

    @Test
    fun `a lone instant condition takes a second one and turns the scenario to any`() {
        val zvonek = Condition.HaEvent(code = "zvonek")
        val brana = Condition.HaEvent(code = "brana")
        val lone = ScenarioDraft().withCondition(zvonek)

        assertEquals(CatalogAvailability.AVAILABLE, catalogAvailability(lone, ConditionType.HA_EVENT))

        val two = lone.withCondition(brana)

        assertEquals(listOf(zvonek, brana), two.conditions)
        assertEquals(ConditionMatch.ANY, two.match)
        assertFalse(two.acceptsMatchAll)
    }

    @Test
    fun `a lone state condition keeps all and blocks a second instant one`() {
        val draft = ScenarioDraft()
            .withCondition(Condition.Charging())
            .withCondition(Condition.HaEvent(code = "zvonek"))

        assertEquals(ConditionMatch.ALL, draft.match)
        assertEquals(CatalogAvailability.BLOCKED_BY_INSTANT, catalogAvailability(draft, ConditionType.HA_EVENT))
    }

    @Test
    fun `a hidden switch does not decide the next addition`() {
        val dropped = ScenarioDraft()
            .withCondition(Condition.HaEvent(code = "zvonek"))
            .withCondition(Condition.HaEvent(code = "brana"))
            .withoutConditionAt(1)
        val fromFallback = ScenarioDraft(match = ConditionMatch.ANY).asFallback()
        val loaded = ScenarioDraft(match = ConditionMatch.ANY, conditions = listOf(Condition.Wifi(mode = WifiMode.ANY)))

        listOf(dropped, fromFallback, loaded).forEach { draft ->
            assertEquals(ConditionMatch.ALL, draft.withCondition(Condition.Charging()).match)
        }
    }

    @Test
    fun `any number of state conditions can be added`() {
        val draft = ScenarioDraft()
            .withCondition(Condition.Charging())
            .withCondition(Condition.AudioOutput())
            .withCondition(Condition.MediaPlaying())

        assertEquals(3, draft.conditions.size)
    }

    @Test
    fun `replacing the instant condition keeps the order`() {
        val draft = ScenarioDraft()
            .withCondition(Condition.HaEvent(code = "zvonek"))
            .withCondition(Condition.Charging())
            .withCondition(Condition.TimeInstant(at = "07:00"))

        assertEquals(2, draft.conditions.size)
        assertEquals(Condition.TimeInstant(at = "07:00"), draft.conditions[0])
        assertEquals(Condition.Charging(), draft.conditions[1])
        assertEquals(ConditionMatch.ALL, draft.match)
    }

    @Test
    fun `editing overwrites the condition in place`() {
        val draft = ScenarioDraft()
            .withCondition(Condition.Charging())
            .withCondition(Condition.Wifi(mode = WifiMode.ANY))
            .withConditionAt(1, Condition.Wifi(mode = WifiMode.SSID, ssid = "Home"))

        assertEquals(Condition.Wifi(mode = WifiMode.SSID, ssid = "Home"), draft.conditions[1])
    }

    @Test
    fun `an out-of-range index leaves the draft unchanged`() {
        val draft = ScenarioDraft().withCondition(Condition.Charging())

        assertEquals(draft, draft.withConditionAt(5, Condition.AudioOutput()))
        assertEquals(draft, draft.withoutConditionAt(-1))
    }

    @Test
    fun `deleting the last condition returns an empty state`() {
        val draft = ScenarioDraft().withCondition(Condition.Charging()).withoutConditionAt(0)

        assertTrue(draft.conditions.isEmpty())
        assertFalse(draft.canContinue)
    }

    @Test
    fun `the name is prefilled and can be overwritten`() {
        assertEquals("18–23", draftName(ScenarioDraft(), "18–23"))
        assertEquals("Evening at home", draftName(ScenarioDraft(name = "Evening at home"), "18–23"))
        assertEquals("18–23", draftName(ScenarioDraft(name = "   "), "18–23"))
    }

    @Test
    fun `changing size discards content but repeating the choice does not`() {
        val medium = ScenarioDraft().withSize(TileSize.GRID_2X2)
        assertEquals(TemplateLayout.TEXT_ONLY, medium.content.layout)

        val edited = medium.copy(content = medium.content.copy(text = "Navigate home"))
        assertEquals(edited, edited.withSize(TileSize.GRID_2X2))

        val hero = edited.withSize(TileSize.HERO_4X2)
        assertEquals(TileSize.HERO_4X2, hero.size)
        assertEquals(defaultContentDraft(TileSize.HERO_4X2), hero.content)
    }

    @Test
    fun `a size change keeps a pin the new class can hold and drops the rest`() {
        val small = ScenarioDraft().withSize(TileSize.GRID_1X1).copy(pinnedPosition = 0)
        assertEquals(0, small.withSize(TileSize.GRID_2X2).pinnedPosition)

        val far = small.copy(pinnedPosition = 2)
        assertNull(far.withSize(TileSize.GRID_2X2).pinnedPosition)
        assertNull(small.withSize(TileSize.HERO_4X2).pinnedPosition)
    }

    @Test
    fun `a pin the size cannot hold is not written into the scenario`() {
        val draft = completeDraft().copy(pinnedPosition = 2)
        assertEquals(2, scenarioOf(draft, id = "s1", name = "Gate")?.pinnedPosition)

        val hero = completeDraft().copy(size = TileSize.HERO_4X2, pinnedPosition = 0)
        assertNull(scenarioOf(hero, id = "s1", name = "Gate")?.pinnedPosition)
    }

    @Test
    fun `a size change that is not written yet does not take the pin with it`() {
        val scenario = scenarioOf(completeDraft(), id = "s1", name = "Gate")!!.copy(pinnedPosition = 2)
        val resized = scenarioDraftOf(scenario).withSize(TileSize.HERO_4X2)

        assertNull(resized.pinnedPosition)

        val waiting = editedScenario(scenario, resized, scenario.name)
        assertEquals(TileSize.GRID_1X1, waiting.size)
        assertEquals(2, waiting.pinnedPosition)
    }

    @Test
    fun `unpinning without touching the size is written`() {
        val scenario = scenarioOf(completeDraft(), id = "s1", name = "Gate")!!.copy(pinnedPosition = 2)
        val unpinned = scenarioDraftOf(scenario).copy(pinnedPosition = null)

        assertNull(editedScenario(scenario, unpinned, scenario.name).pinnedPosition)
    }

    @Test
    fun `the pin travels from a scenario into the draft and back`() {
        val scenario = scenarioOf(completeDraft(), id = "s1", name = "Gate")!!.copy(pinnedPosition = 1)
        val draft = scenarioDraftOf(scenario)

        assertEquals(1, draft.pinnedPosition)
        assertEquals(scenario, editedScenario(scenario, draft, scenario.name))
        assertNull(editedScenario(scenario, draft.copy(pinnedPosition = null), scenario.name).pinnedPosition)
    }

    @Test
    fun `a completed draft converts into a scenario`() {
        val draft = completeDraft().copy(priorityText = "60")

        val scenario = scenarioOf(draft, id = "s1", name = "Evening at home")

        assertNotNull(scenario)
        assertEquals("s1", scenario?.id)
        assertEquals("Evening at home", scenario?.name)
        assertEquals(60, scenario?.priority)
        assertEquals(TileSize.GRID_1X1, scenario?.size)
        assertEquals(listOf(Condition.Charging()), scenario?.conditions)
        assertTrue(scenario?.enabled == true)
        assertEquals(
            TileContent.Template(
                layout = TemplateLayout.ICON_TEXT,
                items = listOf(TemplateItem(actions = listOf(TapAction.App("com.spotify.music")))),
            ),
            scenario?.content,
        )
    }

    @Test
    fun `a disabled scenario can be saved`() {
        val draft = completeDraft().copy(enabled = false)

        assertFalse(scenarioOf(draft, id = "s1", name = "Evening at home")?.enabled ?: true)
    }

    @Test
    fun `persistent content is saved without conditions`() {
        val draft = completeDraft().asFallback()

        assertEquals(emptyList<Condition>(), scenarioOf(draft, id = "s1", name = "Clock")?.conditions)
    }

    @Test
    fun `an incomplete draft does not produce a scenario`() {
        val complete = completeDraft()

        assertNull(scenarioOf(ScenarioDraft(), id = "s1", name = "New"))
        assertNull(scenarioOf(complete.copy(size = null), id = "s1", name = "New"))
        assertNull(scenarioOf(complete.copy(priorityText = "brzy"), id = "s1", name = "New"))
        assertNull(
            scenarioOf(
                complete.copy(content = complete.content.withItem(0, TemplateItemDraft())),
                id = "s1",
                name = "New",
            ),
        )
    }

    @Test
    fun `a saved scenario is also valid as a format file`() {
        val scenario = scenarioOf(completeDraft(), id = "s1", name = "Evening at home")
        assertNotNull(scenario)

        val read = ScenarioFormat.read(ScenarioFormat.encodeScenario(scenario!!))

        assertTrue(read is ScenarioRead.Ok)
        assertEquals(listOf(scenario), (read as ScenarioRead.Ok).accepted.map { it.scenario })
        assertTrue(read.rejected.isEmpty())
    }

    @Test
    fun `a scenario converted to a draft and back is the same`() {
        val scenario = scenarioOf(completeDraft(), id = "s1", name = "Evening at home")
        assertNotNull(scenario)

        val draft = scenarioDraftOf(scenario!!)

        assertEquals(scenario, editedScenario(scenario, draft, scenario.name))
        assertEquals(scenario.priority.toString(), draft.priorityText)
        assertFalse(draft.fallback)
    }

    @Test
    fun `persistent content is recognized in the draft by empty conditions`() {
        val scenario = scenarioOf(completeDraft(), id = "s1", name = "Clock")!!
            .copy(conditions = emptyList())

        assertTrue(scenarioDraftOf(scenario).fallback)
    }

    @Test
    fun `an unfinished priority leaves the saved number alone`() {
        val scenario = scenarioOf(completeDraft(), id = "s1", name = "Evening at home")!!.copy(priority = 65)
        val draft = scenarioDraftOf(scenario).copy(priorityText = "")

        assertEquals(65, editedScenario(scenario, draft, scenario.name).priority)
    }

    @Test
    fun `a size change is only written with new content`() {
        val scenario = scenarioOf(completeDraft(), id = "s1", name = "Evening at home")!!
        val resized = scenarioDraftOf(scenario).withSize(TileSize.GRID_2X2)

        val waiting = editedScenario(scenario, resized, scenario.name)
        assertEquals(TileSize.GRID_1X1, waiting.size)
        assertEquals(scenario.content, waiting.content)

        val item = resized.content.items.first().withAction(0, TapAction.App("com.example"), null)
        val filled = resized.copy(content = resized.content.withItem(0, item).copy(text = "Text"))
        assertEquals(TileSize.GRID_2X2, editedScenario(scenario, filled, scenario.name).size)
    }

    @Test
    fun `an empty name leaves the saved name alone`() {
        val scenario = scenarioOf(completeDraft(), id = "s1", name = "Evening at home")!!

        assertEquals("Evening at home", editedScenario(scenario, scenarioDraftOf(scenario), "  ").name)
    }

    private fun completeDraft(): ScenarioDraft {
        val draft = ScenarioDraft()
            .withCondition(Condition.Charging())
            .withSize(TileSize.GRID_1X1)
        val item = draft.content.items.first().withAction(0, TapAction.App("com.spotify.music"), "Spotify")
        return draft.copy(content = draft.content.withItem(0, item))
    }
}
