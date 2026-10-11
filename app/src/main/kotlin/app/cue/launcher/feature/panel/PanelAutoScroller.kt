package app.cue.launcher.feature.panel

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import app.cue.launcher.designsystem.Tokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// One follower for the whole panel: reorder and resize are mutually exclusive gestures, so a single
// job also guarantees they never fight over the scroll mutex.
@Stable
class PanelAutoScroller(
    private val scrollState: ScrollState,
    private val scope: CoroutineScope,
    private val edgePx: Float,
) {

    private var viewport: LayoutCoordinates? = null
    private var job: Job? = null

    val offset: Int get() = scrollState.value

    internal fun setViewport(coordinates: LayoutCoordinates) {
        viewport = coordinates
    }

    // onFrame gets the pixels the panel really scrolled, which is not the same as the change of
    // offset: the offset also moves on its own when growing or shrinking content reclamps it.
    fun follow(speedPx: Float, pointerY: () -> Float?, onFrame: (scrolled: Float) -> Unit) {
        job?.cancel()
        job = scope.launch {
            var previousFrame = 0L
            while (true) {
                val frame = withFrameNanos { it }
                val seconds = if (previousFrame == 0L) 0f else (frame - previousFrame) / NANOS_PER_SECOND
                previousFrame = frame
                val y = pointerY() ?: return@launch
                val bounds = viewport?.takeIf { it.isAttached }?.boundsInRoot()
                val speed = when (bounds) {
                    null -> 0f
                    else -> panelAutoScrollSpeed(
                        pointerY = y,
                        viewportTop = bounds.top,
                        viewportBottom = bounds.bottom,
                        edge = edgePx,
                        maxSpeed = speedPx,
                    )
                }
                val scrolled = if (speed != 0f && seconds > 0f) scrollState.scrollBy(speed * seconds) else 0f
                onFrame(scrolled)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

@Composable
fun rememberPanelAutoScroller(scrollState: ScrollState): PanelAutoScroller {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    return remember(scrollState, scope, density) {
        with(density) {
            PanelAutoScroller(
                scrollState = scrollState,
                scope = scope,
                edgePx = Tokens.Drag.autoScrollEdge.toPx(),
            )
        }
    }
}

fun Modifier.panelAutoScrollViewport(scroller: PanelAutoScroller): Modifier =
    onGloballyPositioned(scroller::setViewport)

private const val NANOS_PER_SECOND = 1_000_000_000f
