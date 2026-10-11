package app.cue.launcher.feature.widgets

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import app.cue.launcher.feature.dragdrop.awaitLongPress
import app.cue.launcher.feature.dragdrop.trackLiftedPointer

@Composable
fun Modifier.widgetLongPressDrag(
    onStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onFinish: (moved: Boolean) -> Unit,
    onCancel: () -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val finish by rememberUpdatedState(onFinish)
    val cancel by rememberUpdatedState(onCancel)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!awaitLongPress(down)) return@awaitEachGesture
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            start(down.position)
            val moved = trackLiftedPointer(
                pointerId = down.id,
                onDrag = drag,
                pass = PointerEventPass.Initial,
                consumeAllChanges = true,
            )
            when (moved) {
                null -> cancel()
                else -> finish(moved)
            }
        }
    }
}
