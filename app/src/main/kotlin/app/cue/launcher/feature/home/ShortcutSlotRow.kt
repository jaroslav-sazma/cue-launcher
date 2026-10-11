package app.cue.launcher.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.dragdrop.ShortcutDragState
import app.cue.launcher.system.LauncherAppsRepository

@Composable
fun ShortcutSlotRow(
    slots: List<StaticSlot>,
    launcherApps: LauncherAppsRepository,
    dragState: ShortcutDragState,
    showLabels: Boolean,
    actions: StaticSlotActions,
    modifier: Modifier = Modifier,
    outlineEmptySlots: Boolean = false,
) {

    BoxWithConstraints(modifier = modifier) {
        val cellWidth = staticCellWidth(maxWidth)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Grid.gutter),
        ) {
            staticSlotCells(slots).forEach { slot ->
                StaticSlotCell(
                    slot = slot,
                    launcherApps = launcherApps,
                    dragState = dragState,
                    showLabel = showLabels,
                    actions = actions,
                    modifier = Modifier.width(staticSpanWidth(cellWidth, slot.columnSpan)),
                    outlineWhenEmpty = outlineEmptySlots,
                )
            }
        }
    }
}

fun staticCellWidth(rowWidth: Dp): Dp =
    (rowWidth - Tokens.Grid.gutter * (Tokens.Grid.COLUMNS - 1)) / Tokens.Grid.COLUMNS

fun staticCellWidthPx(totalWidthPx: Float, gutterPx: Float, columns: Int): Float =
    (totalWidthPx - gutterPx * (columns - 1)) / columns

fun staticSpanWidth(cellWidth: Dp, span: Int): Dp = cellWidth * span + Tokens.Grid.gutter * (span - 1)
