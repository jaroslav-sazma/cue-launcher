package app.cue.launcher.engine

import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SystemActionRulesTest {

    @Test
    fun `do not disturb access is required by the whole ringer half of the catalog`() {
        assertTrue(needsDndAccess(SystemActionType.RINGER_SILENT))
        assertTrue(needsDndAccess(SystemActionType.RINGER_VIBRATE))
        assertTrue(needsDndAccess(SystemActionType.RINGER_NORMAL))
        assertTrue(needsDndAccess(SystemActionType.DND_ON))
        assertTrue(needsDndAccess(SystemActionType.DND_OFF))
    }

    @Test
    fun `flashlight and panels do not need access`() {
        assertFalse(needsDndAccess(SystemActionType.FLASHLIGHT))
        assertFalse(needsDndAccess(SystemActionType.PANEL_VOLUME))
        assertFalse(needsDndAccess(SystemActionType.PANEL_INTERNET))
        assertFalse(needsDndAccess(SystemActionType.PANEL_WIFI))
        assertFalse(needsDndAccess(SystemActionType.PANEL_NFC))
        assertFalse(needsDndAccess(SystemActionType.OPEN_SETTINGS))
    }

    @Test
    fun `turning do not disturb on runs after the ringer whatever the written order`() {
        val dndOn = TapAction.SystemAction(SystemActionType.DND_ON)
        val vibrate = TapAction.SystemAction(SystemActionType.RINGER_VIBRATE)
        val torch = TapAction.SystemAction(SystemActionType.FLASHLIGHT)

        assertEquals(listOf(vibrate, torch, dndOn), executionOrder(listOf(dndOn, vibrate, torch)))
        assertEquals(listOf(vibrate, dndOn), executionOrder(listOf(vibrate, dndOn)))
    }

    @Test
    fun `turning do not disturb off keeps its written place`() {
        val dndOff = TapAction.SystemAction(SystemActionType.DND_OFF)
        val silent = TapAction.SystemAction(SystemActionType.RINGER_SILENT)

        assertEquals(listOf(dndOff, silent), executionOrder(listOf(dndOff, silent)))
    }

    @Test
    fun `locking the Mac runs after everything else, do not disturb included`() {
        val lock = TapAction.SystemAction(SystemActionType.MAC_LOCK)
        val dndOn = TapAction.SystemAction(SystemActionType.DND_ON)
        val dndOff = TapAction.SystemAction(SystemActionType.DND_OFF)
        val app = TapAction.App(packageName = "com.example")

        assertEquals(listOf(dndOff, app, lock), executionOrder(listOf(lock, dndOff, app)))
        assertEquals(listOf(app, dndOn, lock), executionOrder(listOf(lock, dndOn, app)))
    }

    @Test
    fun `the search also covers the last item of the template`() {
        val scenario = template(
            layout = TemplateLayout.FOUR_ICONS,
            actions = listOf(
                TapAction.App(packageName = "com.example"),
                TapAction.DeepLink(uri = "spotify://"),
                TapAction.SystemAction(SystemActionType.FLASHLIGHT),
                TapAction.SystemAction(SystemActionType.RINGER_SILENT),
            ),
        )

        assertTrue(usesDndAccess(listOf(scenario)))
    }

    @Test
    fun `a disabled scenario counts too`() {
        val scenario = template(actions = listOf(TapAction.SystemAction(SystemActionType.DND_OFF)))
            .copy(enabled = false)

        assertTrue(usesDndAccess(listOf(scenario)))
    }

    @Test
    fun `a scenario without a system action requests nothing`() {
        val scenario = template(actions = listOf(TapAction.App(packageName = "com.example")))

        assertFalse(usesDndAccess(listOf(scenario)))
        assertFalse(usesDndAccess(emptyList()))
    }

    @Test
    fun `a widget carries no action`() {
        val scenario = template().copy(
            content = TileContent.LauncherWidget(LauncherWidgetType.CALENDAR),
            size = TileSize.HERO_4X2,
        )

        assertEquals(emptyList<TapAction>(), tapActionsOf(scenario))
        assertFalse(usesDndAccess(listOf(scenario)))
    }

    private fun template(
        layout: TemplateLayout = TemplateLayout.ICON_TEXT,
        actions: List<TapAction> = listOf(TapAction.App(packageName = "com.example")),
    ): Scenario = Scenario(
        id = "test",
        name = "Test",
        size = layout.size,
        content = TileContent.Template(
            layout = layout,
            items = actions.map { TemplateItem(actions = listOf(it)) },
        ),
    )
}
