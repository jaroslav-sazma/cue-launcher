package app.cue.launcher.feature.lock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LauncherActionTest {

    private val changesLayout = setOf(
        LauncherAction.ADD_SHORTCUT,
        LauncherAction.ADD_WIDGET,
        LauncherAction.NEW_FOLDER,
        LauncherAction.ADD_TO_FOLDER,
        LauncherAction.REORDER_FOLDER,
        LauncherAction.REMOVE_ITEM,
        LauncherAction.ADD_TO_HOME,
        LauncherAction.REORDER_PANEL,
        LauncherAction.ADD_PANEL_WIDGET,
        LauncherAction.OVERRIDE_ICON,
    )

    private val keepsWorking = setOf(
        LauncherAction.CONFIGURE_WIDGET,
        LauncherAction.OPEN_SETTINGS,
        LauncherAction.OPEN_WALLPAPER,
        LauncherAction.SET_DEFAULT_HOME,
    )

    @Test
    fun `an unlocked home screen forbids nothing`() {
        LauncherAction.entries.forEach { action ->
            assertTrue(action.availableWhen(layoutLocked = false), "$action")
        }
    }

    @Test
    fun `the lock hides everything that changes the layout`() {
        changesLayout.forEach { action ->
            assertFalse(action.availableWhen(layoutLocked = true), "$action should be locked")
        }
    }

    @Test
    fun `the lock leaves everything else`() {
        keepsWorking.forEach { action ->
            assertTrue(action.availableWhen(layoutLocked = true), "$action should stay available")
        }
        assertTrue(LauncherAction.OPEN_SETTINGS.availableWhen(layoutLocked = true))
    }

    @Test
    fun `the enumeration is complete`() {
        assertEquals(LauncherAction.entries.toSet(), changesLayout + keepsWorking)
    }
}
