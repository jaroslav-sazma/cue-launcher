package app.cue.launcher.feature.home

import app.cue.launcher.designsystem.Tokens
import android.widget.Toast
import app.cue.launcher.feature.dragdrop.followPointer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import app.cue.launcher.R
import app.cue.launcher.system.NotificationShade

@Composable
fun Modifier.shadeSwipeDown(onOpen: () -> Unit): Modifier {
    val density = LocalDensity.current
    val insets = WindowInsets.systemGestures.union(WindowInsets.mandatorySystemGestures)
    val top = insets.getTop(density).toFloat()
    val bottom = insets.getBottom(density).toFloat()
    val threshold = with(density) { Tokens.Drag.swipeTravel.toPx() }
    val open = rememberUpdatedState(onOpen)

    return pointerInput(top, bottom, threshold) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            val inSystemZone = ShadeGesture.startsInSystemGestureZone(
                startY = down.position.y,
                containerHeightPx = size.height.toFloat(),
                topInsetPx = top,
                bottomInsetPx = bottom,
            )
            if (inSystemZone) return@awaitEachGesture
            var travelX = 0f
            var travelY = 0f
            var fired = false
            followPointer(down.id, PointerEventPass.Main) { change ->
                if (!fired && change.isConsumed) return@followPointer false
                travelX += change.positionChange().x
                travelY += change.positionChange().y
                if (!fired && ShadeGesture.opensShade(travelX, travelY, threshold)) {
                    fired = true
                    open.value()
                }
                if (fired) change.consume()
                true
            }
        }
    }
}

@Composable
fun rememberShadePull(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            if (!NotificationShade.expand(context)) {
                Toast.makeText(context, R.string.home_shade_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
