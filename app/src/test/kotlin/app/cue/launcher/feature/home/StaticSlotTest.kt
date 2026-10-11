package app.cue.launcher.feature.home

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.layout.LauncherItem
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.data.layout.StaticLayout
import app.cue.launcher.data.layout.staticLayoutOf
import app.cue.launcher.data.scenario.LauncherWidgetType
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StaticSlotTest {

    @Test
    fun `the grid has three rows of four slots`() {
        val rows = staticSlotRows(StaticLayout.EMPTY, LayoutContainer.GRID)

        assertEquals(3, rows.size)
        assertEquals(listOf(4, 4, 4), rows.map { it.size })
    }

    @Test
    fun `the dock is one row of four slots`() {
        val rows = staticSlotRows(StaticLayout.EMPTY, LayoutContainer.DOCK)

        assertEquals(1, rows.size)
        assertEquals(4, rows.single().size)
    }

    @Test
    fun `slots go row by row, left to right, and know their coordinate`() {
        val rows = staticSlotRows(StaticLayout.EMPTY, LayoutContainer.GRID)

        assertEquals(listOf(0, 1, 2, 3), rows[0].map { it.position })
        assertEquals(listOf(4, 5, 6, 7), rows[1].map { it.position })
        assertEquals(1 to 2, rows[1][2].row to rows[1][2].column)
    }

    @Test
    fun `an item sits at its position, the other slots stay empty`() {
        val layout = staticLayoutOf(listOf(shortcut(id = 7, position = 6)))

        val rows = staticSlotRows(layout, LayoutContainer.GRID)

        assertEquals(7L, rows[1][2].item?.id)
        assertNull(rows[1][1].item)
        assertEquals(11, rows.flatten().count { it.item == null })
    }

    @Test
    fun `a gap after removal stays in place, nothing shifts`() {
        val items = listOf(shortcut(id = 1, position = 0), shortcut(id = 2, position = 1))

        val rows = staticSlotRows(staticLayoutOf(items.drop(1)), LayoutContainer.GRID)

        assertNull(rows[0][0].item)
        assertEquals(2L, rows[0][1].item?.id)
    }

    @Test
    fun `a wider item is drawn only by the first cell`() {
        val rows = staticSlotRows(staticLayoutOf(listOf(searchbar(position = 10))), LayoutContainer.GRID)

        val cells = staticSlotCells(rows[2])

        assertEquals(3, cells.size)
        assertEquals(listOf(8, 9, 10), cells.map { it.position })
        assertEquals(listOf(1, 1, 2), cells.map { it.columnSpan })
        assertTrue(rows[2][3].isContinuation)
        assertFalse(rows[2][2].isContinuation)
    }

    @Test
    fun `cell width aligns to the column grid`() {
        val cell = staticCellWidth(382.dp)

        assertEquals(88f, cell.value, 0.01f)
        assertEquals(88f, staticSpanWidth(cell, span = 1).value, 0.01f)
        assertEquals(186f, staticSpanWidth(cell, span = 2).value, 0.01f)
    }

    private fun searchbar(position: Int) = LauncherItem.LauncherWidget(
        id = 42,
        container = LayoutContainer.GRID,
        position = position,
        widget = LauncherWidgetType.SEARCHBAR,
    )

    private fun shortcut(id: Long, position: Int) = LauncherItem.Shortcut(
        id = id,
        container = LayoutContainer.GRID,
        position = position,
        component = ComponentRef("app.cue.test", "app.cue.test.MainActivity", userSerial = 0),
    )
}
