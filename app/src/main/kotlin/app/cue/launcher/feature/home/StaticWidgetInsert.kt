package app.cue.launcher.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.system.widget.WidgetSizeSpec
import kotlin.math.ceil

data class CellSpan(val columns: Int, val rows: Int)

data class StaticGridMetrics(
    val cellWidthPx: Float,
    val rowHeightPx: Float,
    val gutterPx: Float,
    val rowGapPx: Float = 0f,
)

fun widgetCellSpan(spec: WidgetSizeSpec, metrics: StaticGridMetrics): CellSpan = CellSpan(

    columns = cellsFor(spec.smallestWidthPx + metrics.gutterPx, metrics.cellWidthPx + metrics.gutterPx),

    rows = cellsFor(spec.smallestHeightPx + metrics.rowGapPx, metrics.rowHeightPx + metrics.rowGapPx),
)

private fun cellsFor(neededPx: Float, cellPx: Float): Int =
    if (cellPx <= 0f) 1 else ceil(neededPx / cellPx).toInt().coerceAtLeast(1)

@Composable
fun rememberStaticGridMetrics(): StaticGridMetrics {
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current
    val rowGap = LocalHomeBands.current.rowGap
    return remember(windowWidthPx, density, rowGap) {
        with(density) {
            val insets = (Tokens.Grid.contentInsetStart + Tokens.Grid.contentInsetEnd).toPx()
            val gutterPx = Tokens.Grid.gutter.toPx()
            val columns = LayoutContainer.GRID.columns
            val contentWidth = (windowWidthPx - insets).coerceAtLeast(0f)
            StaticGridMetrics(
                cellWidthPx = staticCellWidthPx(contentWidth, gutterPx, columns),
                rowHeightPx = Tokens.Grid.RowHeight.static.toPx(),
                gutterPx = gutterPx,
                rowGapPx = rowGap.toPx(),
            )
        }
    }
}
