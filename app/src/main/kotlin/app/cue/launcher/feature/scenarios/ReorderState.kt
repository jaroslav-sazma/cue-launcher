package app.cue.launcher.feature.scenarios

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.cue.launcher.designsystem.rememberReducedMotion
import app.cue.launcher.designsystem.spatialFastSpec

@Stable
internal class ReorderState(

    private val gapPx: Float = 0f,
) {

    var group by mutableStateOf<Any?>(null)
        private set

    var from by mutableIntStateOf(NONE)
        private set

    private var count = 0

    private var offset by mutableFloatStateOf(0f)

    private var rowHeight by mutableFloatStateOf(0f)

    val active: Boolean get() = from != NONE

    private val pitch: Float get() = rowHeight + gapPx

    // Stored, not derived from the offset: rows read it in composition and must not recompose
    // on every dragged pixel, only when the drop target crosses a row boundary.
    var target by mutableIntStateOf(NONE)
        private set

    fun start(group: Any, index: Int, count: Int) {
        this.group = group
        this.count = count
        from = index
        offset = 0f
        target = index
    }

    fun drag(delta: Float) {
        offset += delta
        target = reorderTargetIndex(from, offset, pitch, count)
    }

    fun stop() {
        group = null
        from = NONE
        offset = 0f
        target = NONE
    }

    fun measure(heightPx: Int) {
        if (heightPx > 0) rowHeight = heightPx.toFloat()
    }

    fun isDragged(group: Any, index: Int): Boolean = active && this.group == group && from == index

    fun dragOffset(): Float = offset

    fun shiftPx(group: Any, index: Int): Float = when {
        !active || this.group != group || index == from -> 0f
        else -> reorderShift(index, from, target) * pitch
    }
}

@Composable
internal fun rememberReorderState(gap: Dp = 0.dp): ReorderState {
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    return remember(gapPx) { ReorderState(gapPx) }
}

@Composable
internal fun Modifier.reorderable(
    state: ReorderState,
    group: Any,
    index: Int,
    count: Int,
    onDrop: (from: Int, to: Int) -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current

    val drop = rememberUpdatedState(onDrop)
    val spec = spatialFastSpec<Float>(rememberReducedMotion())
    val shift = remember { Animatable(0f) }
    val shiftTarget = state.shiftPx(group, index)
    LaunchedEffect(shiftTarget) {
        // The drop itself must land instantly: the data reorder moves the row by the same
        // distance the translation held, so easing it out would double the movement.
        if (shiftTarget == 0f && !state.active) shift.snapTo(0f) else shift.animateTo(shiftTarget, spec)
    }
    return this
        .onSizeChanged { state.measure(it.height) }

        .zIndex(if (state.isDragged(group, index)) 1f else 0f)
        .graphicsLayer {
            translationY = if (state.isDragged(group, index)) state.dragOffset() else shift.value
        }
        .pointerInput(group, index, count) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.start(group, index, count)
                },
                onDrag = { change, delta ->
                    change.consume()
                    state.drag(delta.y)
                },
                onDragEnd = {
                    val to = state.target
                    state.stop()
                    if (to != index) drop.value(index, to)
                },
                onDragCancel = state::stop,
            )
        }
}

private const val NONE = -1
