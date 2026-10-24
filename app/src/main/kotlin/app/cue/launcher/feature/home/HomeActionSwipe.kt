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
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
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
            val timeout = viewConfiguration.longPressTimeoutMillis
            var hold = SwipeHold.UNDECIDED
            var last = down
            // A still finger sends no events, so only the timer can tell that a long press began.
            withTimeoutOrNull(timeout) {
                followPointer(down.id, PointerEventPass.Initial) { change ->
                    last = change
                    hold = PageGesture.swipeHold(
                        distanceFromDownPx = (change.position - down.position).getDistance(),
                        elapsedMillis = change.uptimeMillis - down.uptimeMillis,
                        slopPx = viewConfiguration.touchSlop,
                        longPressTimeoutMillis = timeout,
                    )
                    hold == SwipeHold.UNDECIDED
                }
            }
            if (hold != SwipeHold.SWIPE) return@awaitEachGesture
            var fired = false
            val follow = { change: PointerInputChange ->
                val travel = change.position - down.position
                if (!fired && PageGesture.triggersAction(travel.x, travel.y, threshold)) {
                    fired = true
                    action.value()
                }
                if (fired) change.consume()
                true
            }
            follow(last)
            followPointer(down.id, PointerEventPass.Initial, follow)
        }
    }
}
