package app.cue.launcher.feature.dragdrop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChange

internal suspend fun AwaitPointerEventScope.awaitLongPress(down: PointerInputChange): Boolean {
    try {
        withTimeout(viewConfiguration.longPressTimeoutMillis) {
            var change = down
            while (change.pressed) {
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    return@withTimeout
                }
                change = awaitPointerEvent(PointerEventPass.Initial)
                    .changes
                    .firstOrNull { it.id == down.id }
                    ?: return@withTimeout
            }
        }
    } catch (_: PointerEventTimeoutCancellationException) {
        return true
    }
    return false
}

internal suspend fun AwaitPointerEventScope.trackLiftedPointer(
    pointerId: PointerId,
    onDrag: (Offset) -> Unit,
    pass: PointerEventPass = PointerEventPass.Main,
    consumeAllChanges: Boolean = false,
): Boolean? {
    var travel = Offset.Zero
    var moved = false
    while (true) {
        val event = awaitPointerEvent(pass)
        val change = event.changes.firstOrNull { it.id == pointerId }

        val delta = change?.positionChange() ?: Offset.Zero
        if (consumeAllChanges) event.changes.forEach { it.consume() } else change?.consume()
        if (change == null) return null
        if (!change.pressed) return moved
        if (delta != Offset.Zero) {
            travel += delta

            if (travel.getDistance() > viewConfiguration.touchSlop) moved = true
            onDrag(delta)
        }
    }
}
