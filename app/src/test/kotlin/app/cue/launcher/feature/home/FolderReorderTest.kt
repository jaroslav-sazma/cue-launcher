package app.cue.launcher.feature.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FolderReorderTest {

    @Test
    fun `the target is the cell under the finger`() {
        assertEquals(2, folderDropIndex(grid(), x = 250f, y = 50f, itemCount = ITEM_COUNT))
    }

    @Test
    fun `an add tile is not a target`() {

        assertNull(folderDropIndex(grid(), x = 150f, y = 150f, itemCount = ITEM_COUNT))
    }

    @Test
    fun `outside the listing there is no target`() {
        assertNull(folderDropIndex(grid(), x = 250f, y = 450f, itemCount = ITEM_COUNT))
    }

    @Test
    fun `the preview shifts the other icons`() {
        assertEquals(listOf(1, 2, 0, 3, 4), folderPreviewOrder(ITEM_COUNT, dragged = 0, target = 2))
    }

    @Test
    fun `a preview move backward inserts the icon before its neighbors`() {
        assertEquals(listOf(0, 4, 1, 2, 3), folderPreviewOrder(ITEM_COUNT, dragged = 4, target = 1))
    }

    @Test
    fun `outside a drag the order is unchanged`() {
        assertEquals(listOf(0, 1, 2, 3, 4), folderPreviewOrder(ITEM_COUNT, dragged = null, target = null))
    }

    @Test
    fun `a target on its own cell does not change the order`() {
        assertEquals(listOf(0, 1, 2, 3, 4), folderPreviewOrder(ITEM_COUNT, dragged = 2, target = 2))
    }

    private fun grid(): List<FolderCellBounds> = (0..ITEM_COUNT).map { index ->
        val left = (index % COLUMNS) * CELL
        val top = (index / COLUMNS) * CELL
        FolderCellBounds(index = index, left = left, top = top, right = left + CELL, bottom = top + CELL)
    }

    private companion object {
        const val ITEM_COUNT = 5
        const val COLUMNS = 4
        const val CELL = 100f
    }
}
