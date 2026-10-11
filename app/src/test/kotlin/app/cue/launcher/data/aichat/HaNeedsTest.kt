package app.cue.launcher.data.aichat

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class HaNeedsTest {

    private fun scenario(conditions: List<Condition> = emptyList(), vararg actions: TapAction) = Scenario(
        id = "draft",
        name = "Draft",
        size = TileSize.GRID_1X1,
        conditions = conditions,
        content = TileContent.Template(
            TemplateLayout.ICON_TEXT,
            items = listOf(TemplateItem(actions = actions.toList())),
        ),
    )

    @Test
    fun `a draft without Home Assistant needs nothing`() {
        val needs = haNeedsOf(scenario(listOf(Condition.Charging()), TapAction.App("com.maps")), haConfigured = false)

        assertFalse(needs.any)
    }

    @Test
    fun `an event needs the blueprint even with Home Assistant connected`() {
        val needs = haNeedsOf(scenario(listOf(Condition.HaEvent(code = "gate"))), haConfigured = true)

        assertEquals(HaNeeds(blueprint = true, connection = false), needs)
    }

    @Test
    fun `a state read from Home Assistant needs a connection only while there is none`() {
        val draft = scenario(listOf(Condition.HaState(entity = "media_player.tv", states = listOf("playing"))))

        assertEquals(HaNeeds(blueprint = false, connection = true), haNeedsOf(draft, haConfigured = false))
        assertFalse(haNeedsOf(draft, haConfigured = true).any)
    }

    @Test
    fun `an event alone needs no connection, since it arrives without one`() {
        val needs = haNeedsOf(scenario(listOf(Condition.HaEvent(code = "gate"))), haConfigured = false)

        assertEquals(HaNeeds(blueprint = true, connection = false), needs)
    }

    @Test
    fun `an event with an action and no connection needs both`() {
        val action = TapAction.HaAction(service = "cover.open_cover", entity = "{{PLACEHOLDER:gate}}")
        val needs = haNeedsOf(scenario(listOf(Condition.HaEvent(code = "gate")), action), haConfigured = false)

        assertEquals(HaNeeds(blueprint = true, connection = true), needs)
    }

    @Test
    fun `an action needs a connection only while there is none`() {
        val action = TapAction.HaAction(service = "cover.open_cover", entity = "cover.gate")
        val draft = scenario(actions = arrayOf(action))

        assertEquals(HaNeeds(blueprint = false, connection = true), haNeedsOf(draft, haConfigured = false))
        assertFalse(haNeedsOf(draft, haConfigured = true).any)
    }
}
