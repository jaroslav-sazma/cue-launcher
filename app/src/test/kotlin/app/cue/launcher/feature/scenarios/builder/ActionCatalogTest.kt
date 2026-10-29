package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActionCatalogTest {

    @Test
    fun `the catalog has five choices in wireframe order`() {
        assertEquals(
            listOf(
                ActionChoice.OPEN_APP,
                ActionChoice.IN_APP,
                ActionChoice.SYSTEM,
                ActionChoice.HA,
                ActionChoice.URI,
            ),
            ACTION_CATALOG,
        )
    }

    @Test
    fun `the catalog offers every choice exactly once`() {
        assertEquals(ActionChoice.entries.toSet(), ACTION_CATALOG.toSet())
        assertEquals(ACTION_CATALOG.size, ACTION_CATALOG.distinct().size)
    }

    @Test
    fun `four technical types fall under one question`() {
        val inApp = listOf(
            TapAction.Shortcut(packageName = "com.spotify.music", shortcutId = "liked"),
            TapAction.Capability(packageName = "com.spotify.music", capability = "actions.intent.PLAY_MEDIA"),
            TapAction.ActivityLaunch(packageName = "com.spotify.music", component = ".SearchActivity"),
        )

        inApp.forEach { action -> assertEquals(ActionChoice.IN_APP, actionChoiceOf(action), action.toString()) }
    }

    @Test
    fun `the remaining types have their own question`() {
        assertEquals(ActionChoice.OPEN_APP, actionChoiceOf(TapAction.App("com.spotify.music")))
        assertEquals(
            ActionChoice.SYSTEM,
            actionChoiceOf(TapAction.SystemAction(SystemActionType.RINGER_SILENT)),
        )
        assertEquals(
            ActionChoice.HA,
            actionChoiceOf(TapAction.HaAction(service = "light.turn_off", data = JsonObject(emptyMap()))),
        )
    }

    @Test
    fun `a deep link comes back as a custom link`() {
        assertEquals(ActionChoice.URI, actionChoiceOf(TapAction.DeepLink("spotify:search")))
    }

    @Test
    fun `the system action catalog knows the whole format enum`() {
        assertEquals(SystemActionType.entries.toSet(), SYSTEM_ACTION_CATALOG.toSet())
        assertEquals(SYSTEM_ACTION_CATALOG.size, SYSTEM_ACTION_CATALOG.distinct().size)
    }

    @Test
    fun `sections are in wireframe order and nothing drops out`() {
        val sections = systemActionSections()

        assertEquals(
            listOf(
                SystemActionSection.SOUND,
                SystemActionSection.DEVICE,
                SystemActionSection.PLACE,
                SystemActionSection.MAC,
                SystemActionSection.PANELS,
            ),
            sections.map { it.section },
        )
        assertEquals(SYSTEM_ACTION_CATALOG, sections.flatMap { it.actions })
    }

    @Test
    fun `sound holds both sides of the toggle`() {
        val sound = systemActionSections().first { it.section == SystemActionSection.SOUND }

        assertTrue(SystemActionType.DND_OFF in sound.actions)
        assertTrue(SystemActionType.RINGER_NORMAL in sound.actions)
    }

    @Test
    fun `an empty section is not produced`() {
        val sections = systemActionSections(listOf(SystemActionType.FLASHLIGHT))

        assertEquals(listOf(SystemActionSection.DEVICE), sections.map { it.section })
    }

    @Test
    fun `the TV remote sits beside the flashlight while Google TV can open it`() {
        val device = systemActionSections().first { it.section == SystemActionSection.DEVICE }

        assertEquals(listOf(SystemActionType.FLASHLIGHT, SystemActionType.TV_REMOTE), device.actions)
    }

    @Test
    fun `an action the phone cannot run is not offered, and the rest stays`() {
        val sections = systemActionSections(unavailable = setOf(SystemActionType.TV_REMOTE))

        assertFalse(sections.any { SystemActionType.TV_REMOTE in it.actions })
        assertEquals(SYSTEM_ACTION_CATALOG - SystemActionType.TV_REMOTE, sections.flatMap { it.actions })
    }

    @Test
    fun `Lock the Mac is offered only with a Mac paired, in a section of its own`() {
        val unpaired = systemActionSections(unavailable = unpairedSystemActions(bridgePaired = false))
        val paired = systemActionSections(unavailable = unpairedSystemActions(bridgePaired = true))

        assertFalse(unpaired.any { it.section == SystemActionSection.MAC })
        assertEquals(SYSTEM_ACTION_CATALOG - SystemActionType.MAC_LOCK, unpaired.flatMap { it.actions })
        assertEquals(listOf(SystemActionType.MAC_LOCK), paired.single { it.section == SystemActionSection.MAC }.actions)
    }

    @Test
    fun `special access is only needed for Do Not Disturb`() {
        val needing = SYSTEM_ACTION_CATALOG.filter(::systemActionNeedsDndAccess)

        assertEquals(listOf(SystemActionType.DND_ON, SystemActionType.DND_OFF), needing)
        assertFalse(systemActionNeedsDndAccess(SystemActionType.RINGER_SILENT))
    }
}
