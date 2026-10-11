package app.cue.launcher.system.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetSizingTest {

    private val resizable = WidgetSizeSpec(
        minWidthPx = 600,
        minHeightPx = 400,
        horizontallyResizable = true,
        verticallyResizable = true,
    )
    private val fixed = resizable.copy(horizontallyResizable = false, verticallyResizable = false)

    @Test
    fun `a resizable widget gets the full column width`() {
        val box = panelWidgetBox(resizable, slotWidthPx = 1000, slotHeightPx = 340)
        assertEquals(WidgetBox(widthPx = 1000, heightPx = 340), box)
    }

    @Test
    fun `a resizable widget also fits a narrower column`() {
        val box = panelWidgetBox(resizable, slotWidthPx = 500, slotHeightPx = 340)
        assertEquals(500, box.widthPx)
    }

    @Test
    fun `a fixed widget keeps its natural width`() {
        val box = panelWidgetBox(fixed, slotWidthPx = 1000, slotHeightPx = 340)
        assertEquals(600, box.widthPx)
    }

    @Test
    fun `a fixed widget wider than the column is clipped to the column`() {
        val box = panelWidgetBox(fixed, slotWidthPx = 400, slotHeightPx = 340)
        assertEquals(400, box.widthPx)
    }

    @Test
    fun `the height is the row slot, not the provider minHeight`() {
        val box = panelWidgetBox(resizable, slotWidthPx = 1000, slotHeightPx = 170)
        assertEquals(170, box.heightPx)
    }

    @Test
    fun `a widget that cannot resize vertically still fills its rows`() {
        val box = panelWidgetBox(fixed, slotWidthPx = 1000, slotHeightPx = 510)
        assertEquals(510, box.heightPx)
    }

    @Test
    fun `nonsensical inputs give zero, not a negative number`() {
        val nonsense = WidgetSizeSpec(minWidthPx = -10, minHeightPx = -10, horizontallyResizable = false)
        val box = panelWidgetBox(nonsense, slotWidthPx = -100, slotHeightPx = -100)
        assertEquals(WidgetBox(widthPx = 0, heightPx = 0), box)
    }

    @Test
    fun `a resizable widget fills the bento slot`() {
        val box = bentoWidgetBox(resizable, slotWidthPx = 1000, slotHeightPx = 450)
        assertEquals(WidgetBox(widthPx = 1000, heightPx = 450), box)
    }

    @Test
    fun `a fixed widget keeps its natural size in a bento slot`() {
        val box = bentoWidgetBox(fixed, slotWidthPx = 1000, slotHeightPx = 450)
        assertEquals(WidgetBox(widthPx = 600, heightPx = 400), box)
    }

    @Test
    fun `a fixed widget larger than the bento slot is clipped to the slot`() {
        val box = bentoWidgetBox(fixed, slotWidthPx = 500, slotHeightPx = 300)
        assertEquals(WidgetBox(widthPx = 500, heightPx = 300), box)
    }

    @Test
    fun `nonsensical bento slot dimensions give zero`() {
        val box = bentoWidgetBox(resizable, slotWidthPx = -100, slotHeightPx = -100)
        assertEquals(WidgetBox(widthPx = 0, heightPx = 0), box)
    }

    @Test
    fun `resizability is assessed separately on each axis`() {
        val horizontalOnly = resizable.copy(verticallyResizable = false)
        val box = bentoWidgetBox(horizontalOnly, slotWidthPx = 1000, slotHeightPx = 450)
        assertEquals(WidgetBox(widthPx = 1000, heightPx = 400), box)
    }

    @Test
    fun `a resizable widget fits down to minResize`() {
        val shrinkable = resizable.copy(minResizeWidthPx = 300, minResizeHeightPx = 200)

        assertTrue(widgetFitsSlot(shrinkable, slotWidthPx = 300, slotHeightPx = 200))
        assertFalse(widgetFitsSlot(shrinkable, slotWidthPx = 299, slotHeightPx = 200))
        assertFalse(widgetFitsSlot(shrinkable, slotWidthPx = 300, slotHeightPx = 199))
    }

    @Test
    fun `a fixed widget does not fit a smaller slot`() {
        assertTrue(widgetFitsSlot(fixed, slotWidthPx = 600, slotHeightPx = 400))
        assertFalse(widgetFitsSlot(fixed, slotWidthPx = 599, slotHeightPx = 400))
    }

    @Test
    fun `an unset minResize is ignored`() {
        val withoutMinResize = resizable.copy(minResizeWidthPx = 0, minResizeHeightPx = 0)

        assertEquals(600, withoutMinResize.smallestWidthPx)
        assertEquals(400, withoutMinResize.smallestHeightPx)
        assertFalse(widgetFitsSlot(withoutMinResize, slotWidthPx = 500, slotHeightPx = 400))
    }

    @Test
    fun `a minResize larger than the minimum shrinks nothing`() {
        val stubborn = resizable.copy(minResizeWidthPx = 900, minResizeHeightPx = 900)

        assertEquals(600, stubborn.smallestWidthPx)
        assertEquals(400, stubborn.smallestHeightPx)
    }
}
