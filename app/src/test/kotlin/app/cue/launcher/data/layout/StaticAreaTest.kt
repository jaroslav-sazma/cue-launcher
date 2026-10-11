package app.cue.launcher.data.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class StaticAreaTest {

    @ParameterizedTest(name = "pozice {0}, {1}×{2} → {3}")
    @CsvSource(
        "0,4,3,true",
        "0,4,4,false",
        "4,2,2,true",
        "8,1,2,false",
        "3,2,1,false",
        "0,1,1,true",
        "0,1,0,false",
    )
    fun `a rectangle must fit into the grid`(position: Int, columns: Int, rows: Int, fits: Boolean) {
        assertEquals(fits, LayoutContainer.GRID.fitsArea(position, columns, rows))
    }

    @Test
    fun `a rectangle covers positions row by row`() {
        assertEquals(
            listOf(1, 2, 5, 6, 9, 10),
            areaPositions(LayoutContainer.GRID, position = 1, columnSpan = 2, rowSpan = 3),
        )
    }

    @Test
    fun `a widget occupies all its cells`() {
        val layout = staticLayoutOf(listOf(widget(position = 0, columnSpan = 2, rowSpan = 2)))

        listOf(0, 1, 4, 5).forEach { position ->
            assertEquals(1L, layout.itemAt(LayoutContainer.GRID, position)?.id, "pozice $position")
        }
        assertNull(layout.itemAt(LayoutContainer.GRID, 2))
        assertNull(layout.itemAt(LayoutContainer.GRID, 8))

        assertEquals(1, layout.allItems().size)
    }

    @Test
    fun `an overlapping widget is not drawn`() {
        val layout = staticLayoutOf(
            listOf(
                widget(id = 1, position = 0, columnSpan = 2, rowSpan = 2),
                widget(id = 2, position = 5, columnSpan = 2, rowSpan = 1),
            ),
        )

        assertEquals(listOf(1L), layout.allItems().map { it.id })
    }

    @Test
    fun `free space is searched row by row from the left`() {
        val layout = staticLayoutOf(listOf(shortcut(position = 0)))

        assertEquals(1, layout.firstFreeArea(LayoutContainer.GRID, columnSpan = 1, rowSpan = 1))

        assertEquals(1, layout.firstFreeArea(LayoutContainer.GRID, columnSpan = 2, rowSpan = 1))

        assertEquals(4, layout.firstFreeArea(LayoutContainer.GRID, columnSpan = 4, rowSpan = 1))
    }

    @Test
    fun `there is no space on a full grid`() {
        val full = (0 until LayoutContainer.GRID.slotCount).map { shortcut(position = it) }

        assertNull(staticLayoutOf(full).firstFreeArea(LayoutContainer.GRID, 1, 1))
    }

    @Test
    fun `an item that is being moved does not count as an obstacle`() {
        val moved = widget(id = 7, position = 0, columnSpan = 4, rowSpan = 2)
        val layout = staticLayoutOf(listOf(moved))

        assertFalse(layout.areaIsFree(LayoutContainer.GRID, 4, columnSpan = 4, rowSpan = 2))
        assertTrue(
            layout.areaIsFree(LayoutContainer.GRID, 4, columnSpan = 4, rowSpan = 2, ignoring = moved),
        )
    }

    private fun widget(
        id: Long = 1,
        position: Int,
        columnSpan: Int,
        rowSpan: Int,
    ) = LauncherItem.AppWidget(
        id = id,
        container = LayoutContainer.GRID,
        position = position,
        appWidgetId = 100 + id.toInt(),
        columnSpan = columnSpan,
        rowSpan = rowSpan,
    )

    private fun shortcut(position: Int) = LauncherItem.Shortcut(
        id = position.toLong() + 1,
        container = LayoutContainer.GRID,
        position = position,
        component = ComponentRef("app.$position", "Main", 0L),
    )
}
