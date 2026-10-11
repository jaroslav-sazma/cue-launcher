package app.cue.launcher.system

import app.cue.launcher.data.scenario.TapAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppActionsTest {

    @Test
    fun `categories go in order of reliability`() {
        val groups = appActionGroups(listOf(activity(), capability(), link(), configurable(), shortcut()))

        assertEquals(
            listOf(
                AppActionKind.SHORTCUT,
                AppActionKind.CONFIGURABLE,
                AppActionKind.LINK,
                AppActionKind.CAPABILITY,
                AppActionKind.ACTIVITY,
            ),
            groups.map { it.kind },
        )
    }

    @Test
    fun `a shortcut picker has no action until it is configured`() {
        val entry = configurable()

        assertEquals(null, entry.action)
        assertEquals("CONFIGURABLE:com.microsoft.office.outlook.CalendarShortcut", entry.key)
    }

    @Test
    fun `an empty category is not shown`() {
        val groups = appActionGroups(listOf(shortcut(), activity()))

        assertEquals(listOf(AppActionKind.SHORTCUT, AppActionKind.ACTIVITY), groups.map { it.kind })
    }

    @Test
    fun `an app without actions has no category`() {
        assertTrue(appActionGroups(emptyList()).isEmpty())
    }

    @Test
    fun `order within a category is preserved`() {
        val first = shortcut(id = "liked", label = "Oblíbené skladby")
        val second = shortcut(id = "weekly", label = "Discover Weekly")

        val entries = appActionGroups(listOf(first, second)).single().entries

        assertEquals(listOf("Oblíbené skladby", "Discover Weekly"), entries.map { it.label })
    }

    @Test
    fun `a capability gets a human-readable name`() {
        assertEquals("Play media", capabilityLabel("actions.intent.PLAY_MEDIA"))
        assertEquals("Start exercise", capabilityLabel("actions.intent.START_EXERCISE"))
    }

    @Test
    fun `a capability without dots stays as is`() {
        assertEquals("Neco", capabilityLabel("NECO"))
        assertEquals("", capabilityLabel(""))
    }

    private fun shortcut(id: String = "liked", label: String = "Oblíbené skladby") = AppActionEntry(
        kind = AppActionKind.SHORTCUT,
        label = label,
        detail = null,
        action = TapAction.Shortcut(packageName = PACKAGE, shortcutId = id),
    )

    private fun configurable() = AppActionEntry(
        kind = AppActionKind.CONFIGURABLE,
        label = "Calendar shortcut",
        detail = null,
        action = null,
        configActivity = "com.microsoft.office.outlook.CalendarShortcut",
    )

    private fun link() = AppActionEntry(
        kind = AppActionKind.LINK,
        label = "Hledání",
        detail = "spotify:search",
        action = TapAction.DeepLink("spotify:search"),
    )

    private fun capability() = AppActionEntry(
        kind = AppActionKind.CAPABILITY,
        label = "Play media",
        detail = "actions.intent.PLAY_MEDIA",
        action = TapAction.Capability(packageName = PACKAGE, capability = "actions.intent.PLAY_MEDIA"),
    )

    private fun activity() = AppActionEntry(
        kind = AppActionKind.ACTIVITY,
        label = "Hledání",
        detail = "SearchActivity",
        action = TapAction.ActivityLaunch(packageName = PACKAGE, component = ".SearchActivity"),
    )

    private companion object {
        const val PACKAGE = "com.spotify.music"
    }
}
