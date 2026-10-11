package app.cue.launcher.feature.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeMenuTest {

    @Test
    fun `the menu has five items`() {
        assertEquals(
            listOf(
                HomeMenuItem.ADD_APP,
                HomeMenuItem.ADD_WIDGET,
                HomeMenuItem.NEW_FOLDER,
                HomeMenuItem.SETTINGS,
                HomeMenuItem.WALLPAPER,
            ),
            homeMenuItems(offersRole = false),
        )
    }

    @Test
    fun `without a role, a sixth item is added at the end`() {
        assertEquals(
            listOf(
                HomeMenuItem.ADD_APP,
                HomeMenuItem.ADD_WIDGET,
                HomeMenuItem.NEW_FOLDER,
                HomeMenuItem.SETTINGS,
                HomeMenuItem.WALLPAPER,
                HomeMenuItem.SET_DEFAULT,
            ),
            homeMenuItems(offersRole = true),
        )
    }

    @Test
    fun `adding an app and a folder is blocked on a full home screen`() {
        listOf(HomeMenuItem.ADD_APP, HomeMenuItem.NEW_FOLDER).forEach { item ->
            assertFalse(homeMenuEnabled(item, hasFreeSlot = false, hasFreeGridCell = false), "$item")
            assertTrue(homeMenuEnabled(item, hasFreeSlot = true, hasFreeGridCell = false), "$item")
        }
    }

    @Test
    fun `from an empty position, adding works even on a full home screen`() {
        listOf(HomeMenuItem.ADD_APP, HomeMenuItem.NEW_FOLDER).forEach { item ->
            assertTrue(
                homeMenuEnabled(
                    item,
                    hasFreeSlot = false,
                    hasFreeGridCell = false,
                    onEmptySlot = true,
                ),
                "$item má z prázdné pozice zůstat dostupná",
            )
        }
    }

    @Test
    fun `add widget only cares about the grid`() {
        assertFalse(homeMenuEnabled(HomeMenuItem.ADD_WIDGET, hasFreeSlot = true, hasFreeGridCell = false))
        assertTrue(homeMenuEnabled(HomeMenuItem.ADD_WIDGET, hasFreeSlot = false, hasFreeGridCell = true))
    }

    @Test
    fun `an empty position under the finger does not unlock widget`() {
        assertFalse(
            homeMenuEnabled(
                HomeMenuItem.ADD_WIDGET,
                hasFreeSlot = true,
                hasFreeGridCell = false,
                onEmptySlot = true,
            ),
        )
    }

    @Test
    fun `a locked layout leaves only options that do not change the home screen`() {
        assertEquals(
            listOf(HomeMenuItem.SETTINGS, HomeMenuItem.WALLPAPER, HomeMenuItem.SET_DEFAULT),
            homeMenuItems(offersRole = true, layoutLocked = true),
        )
    }

    @Test
    fun `without a role, a locked home screen leaves two items`() {
        assertEquals(
            listOf(HomeMenuItem.SETTINGS, HomeMenuItem.WALLPAPER),
            homeMenuItems(offersRole = false, layoutLocked = true),
        )
    }

    @Test
    fun `settings, wallpaper, and role are not limited by a full home screen`() {
        listOf(HomeMenuItem.SETTINGS, HomeMenuItem.WALLPAPER, HomeMenuItem.SET_DEFAULT).forEach { item ->
            assertTrue(
                homeMenuEnabled(item, hasFreeSlot = false, hasFreeGridCell = false),
                "$item má zůstat dostupná",
            )
        }
    }
}
