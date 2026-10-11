package app.cue.launcher.feature.drawer

import app.cue.launcher.designsystem.Tokens
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity

@Composable
fun Modifier.drawerSwipeUp(onOpen: () -> Unit): Modifier {
    val density = LocalDensity.current
    val bottomInset = WindowInsets.systemGestures
        .union(WindowInsets.mandatorySystemGestures)
        .getBottom(density)
        .toFloat()
    val threshold = with(density) { Tokens.Drag.swipeTravel.toPx() }
    return pointerInput(bottomInset, threshold, onOpen) {
        var travel = 0f
        var armed = false
        detectVerticalDragGestures(
            onDragStart = { start ->
                travel = 0f
                armed = !DrawerGesture.startsInSystemGestureZone(
                    startY = start.y,
                    containerHeightPx = size.height.toFloat(),
                    bottomInsetPx = bottomInset,
                )
            },
            onVerticalDrag = { _, amount ->
                travel += amount
                if (armed && DrawerGesture.opensDrawer(travel, threshold)) {
                    armed = false
                    onOpen()
                }
            },
        )
    }
}

@Composable
fun Modifier.drawerCloseOnOverscroll(onClose: () -> Unit): Modifier {
    val threshold = with(LocalDensity.current) { Tokens.Drag.swipeTravel.toPx() }
    val connection = remember(threshold, onClose) {
        object : NestedScrollConnection {
            private var travel = 0f

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                travel = if (consumed.y != 0f || available.y <= 0f) 0f else travel + available.y
                if (DrawerGesture.closesDrawer(travel, threshold)) {
                    travel = 0f
                    onClose()
                }
                return Offset.Zero
            }
        }
    }
    return nestedScroll(connection)
}

@Composable
fun Modifier.drawerSwipeDown(onClose: () -> Unit): Modifier {
    val threshold = with(LocalDensity.current) { Tokens.Drag.swipeTravel.toPx() }
    return pointerInput(threshold, onClose) {
        var travel = 0f
        var armed = false
        detectVerticalDragGestures(
            onDragStart = {
                travel = 0f
                armed = true
            },
            onVerticalDrag = { _, amount ->
                travel += amount
                if (armed && DrawerGesture.closesDrawer(travel, threshold)) {
                    armed = false
                    onClose()
                }
            },
        )
    }
}
