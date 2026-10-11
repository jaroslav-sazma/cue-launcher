package app.cue.launcher.feature.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PanelReorderTest {

    private val column = listOf(
        PanelSlotBounds(index = 0, top = 0f, bottom = 100f),
        PanelSlotBounds(index = 1, top = 110f, bottom = 310f),
        PanelSlotBounds(index = 2, top = 320f, bottom = 370f),
    )

    @Test
    fun `the target is the item under the finger`() {
        assertEquals(0, panelDropIndex(column, y = 50f))
        assertEquals(1, panelDropIndex(column, y = 200f))
        assertEquals(2, panelDropIndex(column, y = 350f))
    }

    @Test
    @DisplayName("A finger in the gap between widgets targets the following item")
    fun `the gap belongs to the following item`() {
        assertEquals(1, panelDropIndex(column, y = 105f))
    }

    @Test
    fun `a finger outside the column targets its edge`() {
        assertEquals(0, panelDropIndex(column, y = -200f))
        assertEquals(2, panelDropIndex(column, y = 900f))
    }

    @Test
    fun `an empty snapshot has no target`() {
        assertNull(panelDropIndex(emptyList(), y = 0f))
    }

    @Test
    fun `outside a drag the order is unchanged`() {
        assertEquals(listOf(0, 1, 2), panelPreviewOrder(itemCount = 3, dragged = null, target = null))
        assertEquals(listOf(0, 1, 2), panelPreviewOrder(itemCount = 3, dragged = 1, target = 1))
    }

    @Test
    fun `the preview shows the result of the drop`() {
        assertEquals(listOf(1, 2, 0), panelPreviewOrder(itemCount = 3, dragged = 0, target = 2))
        assertEquals(listOf(2, 0, 1), panelPreviewOrder(itemCount = 3, dragged = 2, target = 0))
    }

    @Test
    @DisplayName("Without a drag, items stay where the snapshot found them")
    fun `without a drag items do not move`() {
        val order = listOf(0, 1, 2)
        assertEquals(0f, panelPreviewTop(column, order, index = 0))
        assertEquals(110f, panelPreviewTop(column, order, index = 1))
        assertEquals(320f, panelPreviewTop(column, order, index = 2))
    }

    @Test
    @DisplayName("The preview accounts for neighbour heights, not for the number of slots")
    fun `the preview position sums the heights before the item`() {

        assertEquals(270f, panelPreviewTop(column, listOf(1, 2, 0), index = 0))
        assertEquals(0f, panelPreviewTop(column, listOf(1, 2, 0), index = 1))
    }

    @Test
    fun `an item outside the snapshot has no position`() {
        assertNull(panelPreviewTop(column, listOf(0, 1, 2), index = 3))
        assertNull(panelPreviewTop(emptyList(), listOf(0), index = 0))
    }

    @Test
    fun `the middle of the column does not scroll`() {
        assertEquals(0f, autoScroll(pointerY = 500f))
    }

    @Test
    @DisplayName("In the strip near the edge scrolling ramps up linearly")
    fun `speed grows with depth in the strip`() {
        assertEquals(-450f, autoScroll(pointerY = 50f))
        assertEquals(450f, autoScroll(pointerY = 950f))
    }

    @Test
    fun `beyond the edge speed no longer grows`() {
        assertEquals(-900f, autoScroll(pointerY = 0f))
        assertEquals(-900f, autoScroll(pointerY = -500f))
        assertEquals(900f, autoScroll(pointerY = 1000f))
        assertEquals(900f, autoScroll(pointerY = 1500f))
    }

    @Test
    @DisplayName("A column with neither surface nor strip does not scroll")
    fun `a degenerate column does not scroll`() {
        assertEquals(0f, autoScroll(pointerY = 50f, edge = 0f))
        assertEquals(0f, autoScroll(pointerY = 50f, viewportBottom = 0f))
    }

    private fun autoScroll(
        pointerY: Float,
        viewportTop: Float = 0f,
        viewportBottom: Float = 1000f,
        edge: Float = 100f,
    ): Float = panelAutoScrollSpeed(
        pointerY = pointerY,
        viewportTop = viewportTop,
        viewportBottom = viewportBottom,
        edge = edge,
        maxSpeed = 900f,
    )
}
