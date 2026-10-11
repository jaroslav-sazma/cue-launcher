package app.cue.launcher.feature.dragdrop

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import app.cue.launcher.designsystem.Tokens

@Composable
fun Modifier.shortcutDropTarget(
    state: ShortcutDragState,
    onLift: () -> Unit,
    onDrop: (DragPayload, DropTarget) -> Unit,
    onPressWithoutDrag: (DragPayload) -> Unit,
): Modifier {
    val liftDistancePx = with(LocalDensity.current) { Tokens.Drag.liftDistance.toPx() }

    val currentOnLift by rememberUpdatedState(onLift)
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentOnPress by rememberUpdatedState(onPressWithoutDrag)
    val target = remember(state, liftDistancePx) {
        ShortcutDropTarget(
            state = state,
            liftDistancePx = liftDistancePx,
            onLift = { currentOnLift() },
            onDrop = { payload, target -> currentOnDrop(payload, target) },
            onPressWithoutDrag = { payload -> currentOnPress(payload) },
        )
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event -> SHORTCUT_MIME_TYPE in event.mimeTypes() },
        target = target,
    )
}

private class ShortcutDropTarget(
    private val state: ShortcutDragState,
    private val liftDistancePx: Float,
    private val onLift: () -> Unit,
    private val onDrop: (DragPayload, DropTarget) -> Unit,
    private val onPressWithoutDrag: (DragPayload) -> Unit,
) : DragAndDropTarget {

    private var dragged: DragPayload? = null
    private var start = Offset.Zero
    private var lifted = false
    private var dropped = false

    override fun onStarted(event: DragAndDropEvent) {
        dragged = decodeDragPayload(event.toAndroidDragEvent().localState as? CharSequence)

        state.startDrag(dragged)
        start = event.position
        lifted = false
        dropped = false
    }

    override fun onMoved(event: DragAndDropEvent) {
        val position = event.position
        if (!lifted) {
            if ((position - start).getDistance() < liftDistancePx) return
            lifted = true
            onLift()
        }
        state.aimAt(position)
    }

    override fun onExited(event: DragAndDropEvent) = state.clearTarget()

    @Suppress("ReturnCount")
    override fun onDrop(event: DragAndDropEvent): Boolean {
        // A finger never holds perfectly still, so the system hands out a drop target after a
        // pixel of travel. Below the lift distance the gesture is still a long press, and taking
        // the drop would move the item onto its own slot and swallow the menu.
        if (!lifted) return false
        val target = state.targetAt(event.position) ?: return false
        val clip = event.toAndroidDragEvent().clipData ?: return false
        val payload = decodeDragPayload(clip.getItemAt(0)?.text) ?: return false
        dropped = true
        onDrop(payload, target)
        return true
    }

    override fun onEnded(event: DragAndDropEvent) {
        state.endDrag()
        val payload = dragged
        if (!lifted && !dropped && payload != null) onPressWithoutDrag(payload)
        dragged = null
    }
}

private val DragAndDropEvent.position: Offset
    get() = with(toAndroidDragEvent()) { Offset(x, y) }
