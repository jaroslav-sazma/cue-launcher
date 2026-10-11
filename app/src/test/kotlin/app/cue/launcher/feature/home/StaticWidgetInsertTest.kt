package app.cue.launcher.feature.home

import app.cue.launcher.system.widget.WidgetSizeSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StaticWidgetInsertTest {

    private val metrics = StaticGridMetrics(cellWidthPx = 92.5f, rowHeightPx = 82f, gutterPx = 10f)

    @Test
    fun `a narrow, short widget takes one cell`() {
        assertEquals(CellSpan(columns = 1, rows = 1), spanOf(widthPx = 60, heightPx = 60))
    }

    @Test
    fun `two cells also carry the gutter between them`() {

        assertEquals(CellSpan(columns = 2, rows = 1), spanOf(widthPx = 190, heightPx = 60))
        assertEquals(CellSpan(columns = 3, rows = 1), spanOf(widthPx = 200, heightPx = 60))
    }

    @Test
    fun `a taller widget takes more rows`() {
        assertEquals(CellSpan(columns = 3, rows = 2), spanOf(widthPx = 250, heightPx = 110))
    }

    @Test
    fun `a widget uses a full cell`() {
        assertEquals(CellSpan(columns = 1, rows = 1), spanOf(widthPx = 92, heightPx = 82))
        assertEquals(CellSpan(columns = 2, rows = 2), spanOf(widthPx = 93, heightPx = 83))
    }

    @Test
    fun `the row gap belongs inside a tall widget`() {
        val airy = metrics.copy(rowGapPx = 16f)

        assertEquals(2, widgetCellSpan(spec(60, 175), airy).rows)
        assertEquals(3, widgetCellSpan(spec(60, 175), metrics).rows)
        assertEquals(1, widgetCellSpan(spec(60, 82), airy).rows)
    }

    @Test
    fun `a resizable widget is measured by its minimum size`() {
        val spec = WidgetSizeSpec(
            minWidthPx = 300,
            minHeightPx = 200,
            horizontallyResizable = true,
            verticallyResizable = true,
            minResizeWidthPx = 80,
            minResizeHeightPx = 60,
        )

        assertEquals(CellSpan(columns = 1, rows = 1), widgetCellSpan(spec, metrics))
    }

    @Test
    fun `zero metrics do not break the span`() {
        val empty = StaticGridMetrics(cellWidthPx = 0f, rowHeightPx = 0f, gutterPx = 0f)

        assertEquals(CellSpan(columns = 1, rows = 1), widgetCellSpan(spec(100, 100), empty))
    }

    private fun spanOf(widthPx: Int, heightPx: Int): CellSpan =
        widgetCellSpan(spec(widthPx, heightPx), metrics)

    private fun spec(widthPx: Int, heightPx: Int) =
        WidgetSizeSpec(minWidthPx = widthPx, minHeightPx = heightPx, horizontallyResizable = false)
}
