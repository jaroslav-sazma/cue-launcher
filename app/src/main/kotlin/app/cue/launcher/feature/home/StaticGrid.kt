package app.cue.launcher.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.data.layout.StaticLayout
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.dragdrop.ShortcutDragState
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.system.LauncherAppsRepository
import kotlin.math.roundToInt

@Composable
fun StaticGrid(
    layout: StaticLayout,
    launcherApps: LauncherAppsRepository,
    dragState: ShortcutDragState,
    widgets: WidgetHosting,
    actions: StaticSlotActions,
    modifier: Modifier = Modifier,
    rowGap: Dp = LocalHomeBands.current.rowGap,
) {
    val container = LayoutContainer.GRID
    val rows = remember(layout) { staticSlotRows(layout, container) }

    val cells = remember(rows) { rows.flatMap(::staticSlotCells) }
    val density = LocalDensity.current
    val gutterPx = with(density) { Tokens.Grid.gutter.toPx() }
    val rowHeightPx = with(density) { Tokens.Grid.RowHeight.static.toPx() }
    val rowGapPx = with(density) { rowGap.toPx() }

    Layout(
        modifier = modifier,
        content = {
            cells.forEach { slot ->
                StaticSlotCell(
                    slot = slot,
                    launcherApps = launcherApps,
                    dragState = dragState,
                    showLabel = true,
                    actions = actions,
                    widgets = widgets,
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val cellWidth = staticCellWidthPx(width.toFloat(), gutterPx, container.columns)
        val height = spanPx(rowHeightPx, rowGapPx, container.rows).roundToInt()
        val placeables = measurables.mapIndexed { index, measurable ->
            val slot = cells[index]
            measurable.measure(
                Constraints.fixed(
                    width = spanPx(cellWidth, gutterPx, slot.columnSpan).roundToInt(),
                    height = spanPx(rowHeightPx, rowGapPx, slot.rowSpan).roundToInt(),
                ),
            )
        }
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val slot = cells[index]
                placeable.place(
                    x = ((cellWidth + gutterPx) * slot.column).roundToInt(),
                    y = ((rowHeightPx + rowGapPx) * slot.row).roundToInt(),
                )
            }
        }
    }
}

private fun spanPx(cellSize: Float, gapPx: Float, span: Int): Float =
    cellSize * span + gapPx * (span - 1)
