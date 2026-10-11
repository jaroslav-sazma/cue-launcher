package app.cue.launcher.feature.drawer

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.feature.dragdrop.ShortcutDragState

@Stable
class DrawerDrag(
    val state: ShortcutDragState,

    val onAddToHome: (ComponentRef) -> Unit,

    val onHide: (ComponentRef) -> Unit = {},
) {

    var menuFor: ComponentRef? by mutableStateOf(null)
}
