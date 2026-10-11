package app.cue.launcher.feature.dragdrop

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange

// Follows one pointer until it lifts, handing every move to the caller. Returning false from
// onMove abandons the gesture immediately — the ShadeSwipe contract for a consumed event.
internal suspend fun AwaitPointerEventScope.followPointer(
    pointerId: PointerId,
    pass: PointerEventPass,
    onMove: (PointerInputChange) -> Boolean,
) {
    var pressed = true
    while (pressed) {
        val event = awaitPointerEvent(pass)
        val change = event.changes.firstOrNull { it.id == pointerId }
        pressed = change != null && change.pressed
        if (pressed && change != null && !onMove(change)) return
    }
}
