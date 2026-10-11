package app.cue.launcher.feature.widgets

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalViewConfiguration

@Composable
fun Modifier.widgetTouchArbiter(view: View): Modifier {
    val slop = LocalViewConfiguration.current.touchSlop
    return pointerInput(view, slop) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val arbitration = WidgetTouchArbitration(slop)
            var pressed = true
            while (pressed) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                pressed = change != null && change.pressed
                if (!pressed || change == null) break
                val delta = change.positionChange()
                val handover = arbitration.onDrag(
                    deltaXPx = delta.x,
                    deltaYPx = delta.y,

                    canScrollUp = view.scrollsVerticallyAt(down.position, SCROLL_UP),
                    canScrollDown = view.scrollsVerticallyAt(down.position, SCROLL_DOWN),
                )
                if (handover) change.consume()
            }
        }
    }
}

// `position` is in the view's own coordinates. A host view never scrolls itself — whatever the
// widget put inside it does — so the answer is the one of the deepest child under the finger.
internal fun View.scrollsVerticallyAt(position: Offset, direction: Int): Boolean = when {
    canScrollVertically(direction) -> true
    this !is ViewGroup -> false
    else -> (childCount - 1 downTo 0).any { index ->
        val child = getChildAt(index)
        val local = Offset(position.x + scrollX - child.left, position.y + scrollY - child.top)
        child.visibility == View.VISIBLE &&
            local.x >= 0f && local.x < child.width &&
            local.y >= 0f && local.y < child.height &&
            child.scrollsVerticallyAt(local, direction)
    }
}

internal const val SCROLL_UP = -1
internal const val SCROLL_DOWN = 1

internal fun View.locationInWindow(into: IntArray): Offset {
    getLocationInWindow(into)
    return Offset(into[0].toFloat(), into[1].toFloat())
}
