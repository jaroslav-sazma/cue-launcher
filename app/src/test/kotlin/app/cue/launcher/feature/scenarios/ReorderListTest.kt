package app.cue.launcher.feature.scenarios

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReorderListTest {

    private val rowHeight = 60f

    @Test
    fun `the target changes after crossing half a row`() {
        assertEquals(1, reorderTargetIndex(from = 1, offsetPx = 29f, rowHeightPx = rowHeight, count = 4))
        assertEquals(2, reorderTargetIndex(from = 1, offsetPx = 31f, rowHeightPx = rowHeight, count = 4))
        assertEquals(0, reorderTargetIndex(from = 1, offsetPx = -31f, rowHeightPx = rowHeight, count = 4))
    }

    @Test
    fun `the target stays within the group`() {
        assertEquals(3, reorderTargetIndex(from = 1, offsetPx = 600f, rowHeightPx = rowHeight, count = 4))
        assertEquals(0, reorderTargetIndex(from = 1, offsetPx = -600f, rowHeightPx = rowHeight, count = 4))
    }

    @Test
    fun `an unmeasured row leaves the index unchanged`() {
        assertEquals(2, reorderTargetIndex(from = 2, offsetPx = 300f, rowHeightPx = 0f, count = 4))
        assertEquals(0, reorderTargetIndex(from = 0, offsetPx = 0f, rowHeightPx = rowHeight, count = 0))
    }

    @Test
    fun `a gap only appears between the source and target position`() {
        assertEquals(0, reorderShift(index = 1, from = 1, to = 3))
        assertEquals(-1, reorderShift(index = 2, from = 1, to = 3))
        assertEquals(-1, reorderShift(index = 3, from = 1, to = 3))
        assertEquals(0, reorderShift(index = 4, from = 1, to = 3))
        assertEquals(1, reorderShift(index = 1, from = 3, to = 1))
        assertEquals(1, reorderShift(index = 2, from = 3, to = 1))
        assertEquals(0, reorderShift(index = 0, from = 3, to = 1))
    }

    @Test
    fun `a move removes the item and inserts it at the target position`() {
        val order = listOf("a", "b", "c", "d")

        assertEquals(listOf("b", "c", "a", "d"), order.reordered(from = 0, to = 2))
        assertEquals(listOf("a", "d", "b", "c"), order.reordered(from = 3, to = 1))
    }

    @Test
    fun `a move outside the list changes nothing`() {
        val order = listOf("a", "b")

        assertEquals(order, order.reordered(from = 1, to = 1))
        assertEquals(order, order.reordered(from = 0, to = 5))
        assertEquals(order, order.reordered(from = -1, to = 0))
    }
}
