package app.cue.launcher.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppShortcutTest {

    @Test
    fun `manifest shortcuts come before dynamic ones`() {
        val shortcuts = iconMenuShortcuts(
            listOf(
                offer(id = "recent", declaredInManifest = false, rank = 0),
                offer(id = "incognito", declaredInManifest = true, rank = 1),
            ),
            ACTIVITY,
        )

        assertEquals(listOf("incognito", "recent"), shortcuts.map { it.id })
    }

    @Test
    fun `within a group rank decides`() {
        val shortcuts = iconMenuShortcuts(
            listOf(
                offer(id = "druhy", declaredInManifest = true, rank = 2),
                offer(id = "prvni", declaredInManifest = true, rank = 1),
            ),
            ACTIVITY,
        )

        assertEquals(listOf("prvni", "druhy"), shortcuts.map { it.id })
    }

    @Test
    fun `equal rank preserves the order from the system`() {
        val shortcuts = iconMenuShortcuts(
            listOf(
                offer(id = "a", declaredInManifest = false, rank = 0),
                offer(id = "b", declaredInManifest = false, rank = 0),
            ),
            ACTIVITY,
        )

        assertEquals(listOf("a", "b"), shortcuts.map { it.id })
    }

    @Test
    fun `a disabled shortcut is not offered`() {
        val shortcuts = iconMenuShortcuts(listOf(offer(id = "incognito", enabled = false)), ACTIVITY)

        assertTrue(shortcuts.isEmpty())
    }

    @Test
    fun `a shortcut without a label is not offered`() {
        val shortcuts = iconMenuShortcuts(listOf(offer(id = "incognito").copy(label = " ")), ACTIVITY)

        assertTrue(shortcuts.isEmpty())
    }

    @Test
    fun `a shortcut for a foreign activity is not offered`() {
        val shortcuts = iconMenuShortcuts(
            listOf(
                offer(id = "cizi", activityClassName = "com.android.chrome.OtherActivity"),
                offer(id = "vlastni", activityClassName = ACTIVITY),
            ),
            ACTIVITY,
        )

        assertEquals(listOf("vlastni"), shortcuts.map { it.id })
    }

    @Test
    fun `a shortcut without an activity stays`() {
        val shortcuts = iconMenuShortcuts(listOf(offer(id = "vse", activityClassName = null)), ACTIVITY)

        assertEquals(listOf("vse"), shortcuts.map { it.id })
    }

    @Test
    fun `the offer list is clipped to the limit`() {
        val offers = (1..6).map { offer(id = "s$it", declaredInManifest = true, rank = it) }

        val shortcuts = iconMenuShortcuts(offers, ACTIVITY)

        assertEquals(ICON_MENU_SHORTCUT_LIMIT, shortcuts.size)
        assertEquals(listOf("s1", "s2", "s3", "s4"), shortcuts.map { it.id })
    }

    @Test
    fun `an app without shortcuts has no offer list`() {
        assertTrue(iconMenuShortcuts(emptyList(), ACTIVITY).isEmpty())
    }

    private fun offer(
        id: String,
        activityClassName: String? = ACTIVITY,
        enabled: Boolean = true,
        declaredInManifest: Boolean = true,
        rank: Int = 0,
    ) = AppShortcutOffer(
        id = id,
        label = LABEL,
        activityClassName = activityClassName,
        enabled = enabled,
        declaredInManifest = declaredInManifest,
        rank = rank,
    )

    private companion object {
        const val ACTIVITY = "com.google.android.apps.chrome.Main"
        const val LABEL = "Nové anonymní okno"
    }
}
