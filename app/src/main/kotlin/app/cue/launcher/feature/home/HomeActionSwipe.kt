package app.cue.launcher.feature.home

import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.dragdrop.followPointer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection

@Composable
fun Modifier.homeActionSwipe(enabled: () -> Boolean, onAction: () -> Unit): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.systemGestures
    val left = insets.getLeft(density, layoutDirection).toFloat()
    val right = insets.getRight(density, layoutDirection).toFloat()
    val threshold = with(density) { Tokens.Drag.swipeTravel.toPx() }

    val active = rememberUpdatedState(enabled)
    val action = rememberUpdatedState(onAction)

    return pointerInput(left, right, threshold) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!active.value()) return@awaitEachGesture
            val inEdgeZone = PageGesture.startsInEdgeZone(
                startX = down.position.x,
                containerWidthPx = size.width.toFloat(),
                leftInsetPx = left,
                rightInsetPx = right,
            )
            if (inEdgeZone) return@awaitEachGesture
            var travelX = 0f
            var travelY = 0f
            var fired = false
            followPointer(down.id, PointerEventPass.Initial) { change ->
                travelX += change.positionChange().x
                travelY += change.positionChange().y

                if (!fired && PageGesture.triggersAction(travelX, travelY, threshold)) {
                    fired = true
                    action.value()
                }
                if (fired) change.consume()
                true
            }
        }
    }
}
