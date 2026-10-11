package app.cue.launcher.feature.panel

import app.cue.launcher.data.widgets.PANEL_WIDGET_ROWS_MIN
import kotlin.math.roundToInt

fun panelResizeRows(startRows: Int, dragPx: Float, rowPx: Float, maxRows: Int): Int {
    if (rowPx <= 0f) return startRows
    return panelWidgetRows(startRows + (dragPx / rowPx).roundToInt(), maxRows)
}

fun panelWidgetRows(rows: Int, maxRows: Int): Int =
    rows.coerceIn(PANEL_WIDGET_ROWS_MIN, maxOf(maxRows, PANEL_WIDGET_ROWS_MIN))

fun panelRowsInViewport(viewportHeightPx: Float, rowPx: Float): Int =
    if (rowPx <= 0f) PANEL_WIDGET_ROWS_MIN else (viewportHeightPx / rowPx).toInt()
