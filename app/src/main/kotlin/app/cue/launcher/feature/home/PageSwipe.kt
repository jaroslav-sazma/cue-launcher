package app.cue.launcher.feature.home

import app.cue.launcher.feature.dragdrop.followPointer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration

@Composable
fun Modifier.pageSwipeEdgeGuard(): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.systemGestures
    val left = insets.getLeft(density, layoutDirection).toFloat()
    val right = insets.getRight(density, layoutDirection).toFloat()
    val slop = LocalViewConfiguration.current.touchSlop
    return pointerInput(left, right, slop) {
        if (left <= 0f && right <= 0f) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val inEdgeZone = PageGesture.startsInEdgeZone(
                startX = down.position.x,
                containerWidthPx = size.width.toFloat(),
                leftInsetPx = left,
                rightInsetPx = right,
            )
            if (!inEdgeZone) return@awaitEachGesture
            var travelX = 0f
            var travelY = 0f
            var blocking = false
            followPointer(down.id, PointerEventPass.Initial) { change ->
                travelX += change.positionChange().x
                travelY += change.positionChange().y
                blocking = blocking || PageGesture.blocksPaging(travelX, travelY, slop)
                if (blocking) change.consume()
                true
            }
        }
    }
}
