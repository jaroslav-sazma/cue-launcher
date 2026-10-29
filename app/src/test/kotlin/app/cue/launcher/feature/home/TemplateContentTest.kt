package app.cue.launcher.feature.home

import app.cue.launcher.R
import app.cue.launcher.data.scenario.ActionApp
import app.cue.launcher.data.scenario.AppProfile
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.designsystem.glyph.MdiGlyphs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TemplateContentTest {

    @Test
    fun `a given icon always applies`() {
        val item = TemplateItem(
            label = "Brána",
            icon = "mdi:gate",
            actions = listOf(TapAction.App(packageName = "com.google.android.dialer")),
        )

        assertEquals("gate", templateGlyphName(item))

        assertNull(templateIconApp(item))
    }

    @Test
    fun `a system action has a glyph from the catalog`() {
        val item = TemplateItem(actions = listOf(TapAction.SystemAction(SystemActionType.FLASHLIGHT)))

        assertEquals("flashlight", templateGlyphName(item))
        assertNull(templateIconApp(item))
    }

    @Test
    fun `every system action has a glyph`() {
        SystemActionType.entries.forEach { action ->
            val glyph = templateGlyphName(TemplateItem(actions = listOf(TapAction.SystemAction(action))))

            assertEquals(SYSTEM_ACTION_GLYPHS[action], glyph) { action.jsonName }
            assertTrue(glyph?.isNotEmpty() == true) { action.jsonName }
        }
    }

    @Test
    fun `an HA action without an icon gets the Home Assistant icon`() {
        val item = TemplateItem(actions = listOf(TapAction.HaAction(service = "cover.open_cover")))

        assertEquals(HA_GLYPH, templateGlyphName(item))
        assertNull(templateIconApp(item))
    }

    @Test
    fun `an HA action without an icon draws what Home Assistant would, a chosen icon stays`() {
        val action = TapAction.HaAction(service = "cover.open_cover", entity = "cover.gate")
        val glyphs = listOf("mdi:gate", "mdi:gate-open")

        assertEquals("gate", templateGlyphName(TemplateItem(actions = listOf(action)), glyphs))
        val chosen = TemplateItem(icon = "mdi:boom-gate", actions = listOf(action))
        assertEquals("boom-gate", templateGlyphName(chosen, glyphs))
    }

    @Test
    fun `a name the bundled font does not know gives way to the next`() {
        val item = TemplateItem(actions = listOf(TapAction.HaAction(service = "cover.toggle", entity = "cover.gate")))
        val font = MdiGlyphs(mapOf("gate-open" to 0xF0299))
        val glyphs = mapOf("cover.gate" to listOf("mdi:gate-future", "mdi:gate-open"))

        assertEquals(listOf("mdi:gate-open"), templateStateGlyphs(item, glyphs, font, macLocked = null))
        assertEquals(emptyList<String>(), templateStateGlyphs(item, emptyMap(), font, macLocked = null))
    }

    @Test
    fun `Lock Mac draws the laptop it locks, a lock once locked and a crossed laptop while unknown`() {
        val item = TemplateItem(actions = listOf(TapAction.SystemAction(SystemActionType.MAC_LOCK)))
        val font = MdiGlyphs(emptyMap())
        val glyph = { locked: Boolean? -> templateGlyphName(item, templateStateGlyphs(item, emptyMap(), font, locked)) }

        assertEquals("laptop", glyph(false))
        assertEquals("lock", glyph(true))
        assertEquals("laptop-off", glyph(null))
        val chosen = TemplateItem(icon = "mdi:monitor", actions = item.actions)
        assertEquals("monitor", templateGlyphName(chosen, templateStateGlyphs(chosen, emptyMap(), font, true)))
    }

    @Test
    fun `a text tile that locks the Mac taps only while the Mac is unlocked`() {
        val lock = TapAction.SystemAction(SystemActionType.MAC_LOCK)
        val template = TileContent.Template(
            layout = TemplateLayout.TEXT_ONLY,
            text = "Lock the Mac",
            items = listOf(TemplateItem(actions = listOf(lock))),
        )

        assertEquals(listOf(lock), template.enabledWholeTileActions(macLocked = false))
        assertTrue(template.enabledWholeTileActions(macLocked = true).isEmpty())
        assertTrue(template.enabledWholeTileActions(macLocked = null).isEmpty())
        assertTrue(template.wholeTileEnabled(macLocked = false))
        assertFalse(template.wholeTileEnabled(macLocked = true))
        assertFalse(template.wholeTileEnabled(macLocked = null))
        assertNull(template.wholeTileStateRes(macLocked = false))
        assertEquals(R.string.tile_mac_locked, template.wholeTileStateRes(macLocked = true))
        assertEquals(R.string.tile_mac_unavailable, template.wholeTileStateRes(macLocked = null))
    }

    @Test
    fun `a button that locks the Mac among other actions ignores the Mac`() {
        val actions = listOf(
            TapAction.SystemAction(SystemActionType.MAC_LOCK),
            TapAction.SystemAction(SystemActionType.DND_OFF),
        )
        val item = TemplateItem(actions = actions)
        val template = TileContent.Template(layout = TemplateLayout.TEXT_ONLY, text = "Leaving", items = listOf(item))
        val font = MdiGlyphs(emptyMap())

        listOf(true, false, null).forEach { locked ->
            assertEquals(actions, template.enabledWholeTileActions(locked)) { "locked=$locked" }
            assertTrue(template.wholeTileEnabled(locked)) { "locked=$locked" }
            assertNull(template.wholeTileStateRes(locked)) { "locked=$locked" }
            assertNull(macLockStateRes(item, locked)) { "locked=$locked" }
            assertEquals("laptop", templateGlyphName(item, templateStateGlyphs(item, emptyMap(), font, locked)))
        }
    }

    @Test
    fun `a text tile that does not lock the Mac has no lock state`() {
        val template = TileContent.Template(
            layout = TemplateLayout.TEXT_ONLY,
            text = "Torch",
            items = listOf(TemplateItem(actions = listOf(TapAction.SystemAction(SystemActionType.FLASHLIGHT)))),
        )

        assertTrue(template.wholeTileEnabled(macLocked = null))
        assertNull(template.wholeTileStateRes(macLocked = true))
    }

    @Test
    fun `app, shortcut, activity, and capability are represented by the app icon`() {
        val actions = listOf(
            TapAction.App(packageName = "com.android.chrome"),
            TapAction.Shortcut(packageName = "com.android.chrome", shortcutId = "new_tab"),
            TapAction.ActivityLaunch(packageName = "com.android.chrome", component = "org.x.SearchActivity"),
            TapAction.Capability(packageName = "com.android.chrome", capability = "actions.intent.OPEN_APP_FEATURE"),
        )

        actions.forEach { action ->
            val item = TemplateItem(actions = listOf(action))
            assertEquals(ActionApp("com.android.chrome"), templateIconApp(item)) { action.toString() }
            assertNull(templateGlyphName(item)) { action.toString() }
        }
    }

    @Test
    fun `a work app action is drawn with the icon of the work app`() {
        val actions = listOf(
            TapAction.App(packageName = "com.microsoft.office.outlook", profile = AppProfile.WORK),
            TapAction.Shortcut(
                packageName = "com.microsoft.office.outlook",
                shortcutId = "compose",
                profile = AppProfile.WORK,
            ),
            TapAction.Capability(
                packageName = "com.microsoft.office.outlook",
                capability = "actions.intent.CREATE_MESSAGE",
                profile = AppProfile.WORK,
            ),
        )

        actions.forEach { action ->
            val item = TemplateItem(actions = listOf(action))
            assertEquals(ActionApp("com.microsoft.office.outlook", AppProfile.WORK), templateIconApp(item)) {
                action.toString()
            }
        }
    }

    @Test
    fun `a deep link without an icon stays empty`() {
        val item = TemplateItem(actions = listOf(TapAction.DeepLink(uri = "google.navigation:q=Domů")))

        assertNull(templateGlyphName(item))
        assertNull(templateIconApp(item))
    }

    @Test
    fun `an empty icon is derived from the action`() {
        val item = TemplateItem(
            icon = "  ",
            actions = listOf(TapAction.SystemAction(SystemActionType.DND_ON)),
        )

        assertEquals("minus-circle", templateGlyphName(item))
    }

    @Test
    fun `only text has an action on the whole tile`() {
        val action = TapAction.DeepLink(uri = "google.navigation:q=Domů")
        val template = TileContent.Template(
            layout = TemplateLayout.TEXT_ONLY,
            text = "Navigovat",
            items = listOf(TemplateItem(actions = listOf(action))),
        )

        assertEquals(listOf(action), template.wholeTileActions)
    }

    @Test
    fun `templates with icons have no action on the tile`() {
        val items = List(FOUR) { TemplateItem(actions = listOf(TapAction.SystemAction(SystemActionType.DND_ON))) }

        listOf(TemplateLayout.ICON_TEXT, TemplateLayout.TEXT_TWO_ICONS, TemplateLayout.FOUR_ICONS)
            .forEach { layout ->
                val template = TileContent.Template(
                    layout = layout,
                    items = items.take(layout.itemCount),
                )

                assertTrue(template.wholeTileActions.isEmpty()) { layout.jsonName }
            }
    }

    private companion object {
        const val FOUR = 4
    }
}
