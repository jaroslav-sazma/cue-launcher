package app.cue.launcher.feature.panel

import androidx.compose.runtime.Stable

@Stable
class PanelItemActions(
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val onMove: (Int) -> Unit,
    val onRemove: () -> Unit,
)
