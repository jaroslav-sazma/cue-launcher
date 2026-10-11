package app.cue.launcher.feature.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PanelResizeTest {

    private val rowPx = 170f

    @Test
    fun `no drag keeps the height`() {
        assertEquals(2, panelResizeRows(startRows = 2, dragPx = 0f, rowPx = rowPx, maxRows = 6))
    }

    @Test
    fun `dragging down one row grows the widget by one row`() {
        assertEquals(3, panelResizeRows(startRows = 2, dragPx = rowPx, rowPx = rowPx, maxRows = 6))
    }

    @Test
    fun `dragging up one row shrinks the widget by one row`() {
        assertEquals(1, panelResizeRows(startRows = 2, dragPx = -rowPx, rowPx = rowPx, maxRows = 6))
    }

    @Test
    fun `the step snaps at half a row`() {
        assertEquals(2, panelResizeRows(startRows = 2, dragPx = rowPx * 0.49f, rowPx = rowPx, maxRows = 6))
        assertEquals(3, panelResizeRows(startRows = 2, dragPx = rowPx * 0.51f, rowPx = rowPx, maxRows = 6))
    }

    @Test
    fun `a widget never shrinks below one row`() {
        assertEquals(1, panelResizeRows(startRows = 2, dragPx = -rowPx * 10, rowPx = rowPx, maxRows = 6))
    }

    @Test
    fun `a widget never grows past the viewport`() {
        assertEquals(6, panelResizeRows(startRows = 2, dragPx = rowPx * 10, rowPx = rowPx, maxRows = 6))
    }

    @Test
    fun `a viewport too short for a single row still allows one`() {
        assertEquals(1, panelResizeRows(startRows = 2, dragPx = 0f, rowPx = rowPx, maxRows = 0))
    }

    @Test
    fun `an unmeasured row height changes nothing`() {
        assertEquals(2, panelResizeRows(startRows = 2, dragPx = 500f, rowPx = 0f, maxRows = 6))
    }

    @Test
    fun `a stored height taller than the viewport is shown clamped`() {
        assertEquals(4, panelWidgetRows(rows = 7, maxRows = 4))
    }

    @Test
    fun `the viewport holds as many whole rows as fit`() {
        assertEquals(4, panelRowsInViewport(viewportHeightPx = 700f, rowPx = rowPx))
        assertEquals(1, panelRowsInViewport(viewportHeightPx = 0f, rowPx = 0f))
    }
}
