package app.cue.launcher.data.layout

import app.cue.launcher.data.scenario.LauncherWidgetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class StaticLayoutTest {

    @Test
    fun `the grid has three rows of four, both docks have four positions`() {
        assertEquals(12, LayoutContainer.GRID.slotCount)
        assertEquals(4, LayoutContainer.DOCK.slotCount)
        assertEquals(4, LayoutContainer.PANEL_DOCK.slotCount)
    }

    @Test
    fun `there are twenty static positions`() {
        assertEquals(20, LayoutContainer.entries.sumOf { it.slotCount })
    }

    @Test
    fun `stored container keys are unique and translate back`() {
        val keys = LayoutContainer.entries.map { it.storageKey }

        assertEquals(keys.size, keys.toSet().size)
        LayoutContainer.entries.forEach { container ->
            assertEquals(container, LayoutContainer.fromStorageKey(container.storageKey))
        }
        assertNull(LayoutContainer.fromStorageKey("panelDock"))
    }

    @Test
    fun `home screen containers are the grid and the dock`() {
        assertEquals(listOf(LayoutContainer.GRID, LayoutContainer.DOCK), LayoutContainer.home)
    }

    @ParameterizedTest(name = "position {0} = row {1}, column {2}")
    @CsvSource("0,0,0", "3,0,3", "4,1,0", "11,2,3")
    fun `positions are indexed row by row, left to right`(position: Int, row: Int, column: Int) {
        assertEquals(row, LayoutContainer.GRID.rowOf(position))
        assertEquals(column, LayoutContainer.GRID.columnOf(position))
        assertEquals(position, LayoutContainer.GRID.positionOf(row, column))
    }

    @Test
    fun `a position outside the container range is invalid`() {
        assertTrue(LayoutContainer.GRID.isValidPosition(11))
        assertFalse(LayoutContainer.GRID.isValidPosition(12))
        assertFalse(LayoutContainer.GRID.isValidPosition(-1))
        assertFalse(LayoutContainer.DOCK.isValidPosition(4))
    }

    @Test
    fun `items are laid out into slots and the rest stays empty`() {
        val layout = staticLayoutOf(
            listOf(
                shortcut(id = 1, container = LayoutContainer.GRID, position = 5),
                shortcut(id = 2, container = LayoutContainer.DOCK, position = 0),
            ),
        )

        assertEquals(1L, layout.itemAt(LayoutContainer.GRID, 5)?.id)
        assertEquals(2L, layout.itemAt(LayoutContainer.DOCK, 0)?.id)
        assertNull(layout.itemAt(LayoutContainer.GRID, 0))
        assertEquals(12, layout.slots(LayoutContainer.GRID).size)
        assertEquals(4, layout.slots(LayoutContainer.DOCK).size)
        assertFalse(layout.isEmpty())
    }

    @Test
    fun `an uninstall leaves a gap, items do not shift`() {
        val items = listOf(
            shortcut(id = 1, container = LayoutContainer.GRID, position = 0),
            shortcut(id = 2, container = LayoutContainer.GRID, position = 1),
        )

        val afterUninstall = staticLayoutOf(items.filterNot { it.id == 1L })

        assertNull(afterUninstall.itemAt(LayoutContainer.GRID, 0))
        assertEquals(2L, afterUninstall.itemAt(LayoutContainer.GRID, 1)?.id)
    }

    @Test
    fun `an item out of range is discarded instead of crashing`() {
        val layout = staticLayoutOf(listOf(shortcut(id = 1, container = LayoutContainer.GRID, position = 99)))

        assertTrue(layout.isEmpty())
    }

    @Test
    fun `a wider item occupies all of its positions`() {
        val layout = staticLayoutOf(listOf(searchbar(id = 5, position = 10)))

        assertEquals(5L, layout.itemAt(LayoutContainer.GRID, 10)?.id)
        assertEquals(5L, layout.itemAt(LayoutContainer.GRID, 11)?.id)
        assertNull(layout.itemAt(LayoutContainer.GRID, 9))

        assertEquals(1, layout.allItems().size)
    }

    @Test
    fun `a wider item does not overflow into the next row`() {
        assertTrue(staticLayoutOf(listOf(searchbar(id = 1, position = 2))).allItems().isNotEmpty())

        assertTrue(staticLayoutOf(listOf(searchbar(id = 1, position = 3))).isEmpty())
        assertTrue(staticLayoutOf(listOf(searchbar(id = 1, position = 11))).isEmpty())
    }

    @Test
    fun `an overlap with a wider item, the earlier one wins`() {
        val layout = staticLayoutOf(
            listOf(
                searchbar(id = 1, position = 0),
                shortcut(id = 2, container = LayoutContainer.GRID, position = 1),
            ),
        )

        assertEquals(1L, layout.itemAt(LayoutContainer.GRID, 1)?.id)
        assertEquals(listOf(1L), layout.allItems().map { it.id })
    }

    @ParameterizedTest(name = "position {0}, width {1} → {2}")
    @CsvSource("0,1,true", "0,2,true", "2,2,true", "3,2,false", "11,1,true", "11,2,false", "0,0,false")
    fun `the width must fit within the row`(position: Int, span: Int, fits: Boolean) {
        assertEquals(fits, LayoutContainer.GRID.fitsArea(position, span))
    }

    @Test
    fun `an empty layout has no slot occupied`() {
        assertTrue(StaticLayout.EMPTY.isEmpty())
        assertEquals(12, StaticLayout.EMPTY.slots(LayoutContainer.GRID).count { it == null })
    }

    private fun searchbar(id: Long, position: Int) = LauncherItem.LauncherWidget(
        id = id,
        container = LayoutContainer.GRID,
        position = position,
        widget = LauncherWidgetType.SEARCHBAR,
    )

    private fun shortcut(id: Long, container: LayoutContainer, position: Int) = LauncherItem.Shortcut(
        id = id,
        container = container,
        position = position,
        component = ComponentRef("app.cue.test", "app.cue.test.MainActivity", userSerial = 0),
    )
}
