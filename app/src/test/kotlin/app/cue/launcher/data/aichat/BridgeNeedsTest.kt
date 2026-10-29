package app.cue.launcher.data.aichat

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BridgeNeedsTest {

    private fun scenario(vararg conditions: Condition, actions: List<TapAction> = emptyList()) = Scenario(
        id = "draft",
        name = "Draft",
        size = TileSize.GRID_1X1,
        conditions = conditions.toList(),
        content = TileContent.Template(TemplateLayout.ICON_TEXT, items = listOf(TemplateItem(actions = actions))),
    )

    @Test
    fun `a draft without the Mac condition needs no bridge`() {
        assertFalse(bridgeNeededBy(scenario(Condition.Charging()), bridgePaired = false))
    }

    @Test
    fun `a Mac condition needs the bridge while none is paired, turned around or not`() {
        assertTrue(bridgeNeededBy(scenario(Condition.MacNearby()), bridgePaired = false))
        val turnedAround = scenario(Condition.Charging(), Condition.MacNearby(negate = true))
        assertTrue(bridgeNeededBy(turnedAround, bridgePaired = false))
        assertTrue(bridgeNeededBy(scenario(Condition.MacLocked(negate = true)), bridgePaired = false))
    }

    @Test
    fun `a Lock Mac action needs the bridge while none is paired`() {
        val lock = listOf(SystemActionType.FLASHLIGHT, SystemActionType.MAC_LOCK).map(TapAction::SystemAction)

        assertTrue(bridgeNeededBy(scenario(Condition.Charging(), actions = lock), bridgePaired = false))
        assertFalse(bridgeNeededBy(scenario(Condition.Charging(), actions = lock), bridgePaired = true))
    }

    @Test
    fun `a paired bridge leaves nothing to set up`() {
        assertFalse(bridgeNeededBy(scenario(Condition.MacNearby()), bridgePaired = true))
    }
}
