package app.cue.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.rememberReducedMotion
import app.cue.launcher.designsystem.spatialFastSpec
import app.cue.launcher.feature.dragdrop.trackLiftedPointer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Stable
class FolderReorderState {

    private val cells = mutableMapOf<Int, LayoutCoordinates>()
    private val lastSlots = mutableMapOf<Int, Int>()
    private var itemCount = 0

    var draggedIndex: Int? by mutableStateOf(null)
        private set

    var targetIndex: Int? by mutableStateOf(null)
        private set

    private var pointer by mutableStateOf(Offset.Zero)

    val liftedSlot: Int? get() = targetIndex ?: draggedIndex

    fun previewOrder(itemCount: Int): List<Int> = folderPreviewOrder(itemCount, draggedIndex, targetIndex)

    internal fun liftOffset(): Offset {
        val center = liftedSlot?.let(::center)
        return if (center == null) Offset.Zero else pointer - center
    }

    internal fun setCoordinates(index: Int, coordinates: LayoutCoordinates) {
        cells[index] = coordinates
    }

    internal fun unregister(index: Int) {
        cells.remove(index)
    }

    internal fun start(index: Int, itemCount: Int) {
        val center = center(index) ?: return
        lastSlots.clear()
        this.itemCount = itemCount
        pointer = center
        draggedIndex = index
        targetIndex = index
    }

    internal fun drag(delta: Offset) {
        if (draggedIndex == null) return
        pointer += delta

        val bounds = cells.mapNotNull { (index, coordinates) ->
            val rect = coordinates.takeIf { it.isAttached }?.boundsInRoot() ?: return@mapNotNull null
            FolderCellBounds(index, rect.left, rect.top, rect.right, rect.bottom)
        }
        targetIndex = folderDropIndex(bounds, pointer.x, pointer.y, itemCount) ?: targetIndex
    }

    internal fun finish(): FolderMove? {
        val from = draggedIndex
        val to = targetIndex
        cancel()
        return if (from != null && to != null && from != to) FolderMove(from, to) else null
    }

    internal fun cancel() {
        draggedIndex = null
        targetIndex = null
    }

    internal fun center(index: Int): Offset? =
        cells[index]?.takeIf { it.isAttached }?.boundsInRoot()?.center

    // Cells are static grid boxes and only their content is re-bound, so per-item slot history
    // has to live here, not in the cell composition.
    internal fun trackSlot(index: Int, slot: Int): Int? = lastSlots.put(index, slot)
}

@Composable
fun Modifier.folderReorderCell(state: FolderReorderState, index: Int): Modifier {
    DisposableEffect(state, index) {
        onDispose { state.unregister(index) }
    }
    return onGloballyPositioned { coordinates -> state.setCoordinates(index, coordinates) }
}

// Cells keep their grid position and swap content, so a re-bound item starts translated from
// the slot it came from and eases into place. The lifted item is excluded: folderReorderLift
// already places it under the finger.
@Composable
fun Modifier.folderSlotSwap(state: FolderReorderState, index: Int, slot: Int): Modifier {
    val spec = spatialFastSpec<Offset>(rememberReducedMotion())
    val swap = remember(index) { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(index, slot) {
        val previous = state.trackSlot(index, slot)
        if (previous == null || previous == slot) return@LaunchedEffect
        if (state.draggedIndex == null || state.draggedIndex == index) return@LaunchedEffect
        val from = state.center(previous) ?: return@LaunchedEffect
        val to = state.center(slot) ?: return@LaunchedEffect
        swap.snapTo(from - to)
        swap.animateTo(Offset.Zero, spec)
    }
    return graphicsLayer {
        translationX = swap.value.x
        translationY = swap.value.y
    }
}

@Composable
fun Modifier.folderReorderSource(
    state: FolderReorderState,
    index: Int,
    itemCount: Int,
    onMove: (FolderMove) -> Unit,
    onMenu: () -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val currentIndex by rememberUpdatedState(index)
    val currentCount by rememberUpdatedState(itemCount)
    val currentMove by rememberUpdatedState(onMove)
    val currentMenu by rememberUpdatedState(onMenu)
    return pointerInput(state) {
        detectFolderReorder(
            onStart = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                state.start(currentIndex, currentCount)
            },
            onDrag = { delta -> state.drag(delta.fromLiftedLayer()) },
            onFinish = { moved ->
                val move = state.finish()
                when {

                    !moved -> currentMenu()
                    move != null -> currentMove(move)
                    else -> Unit
                }
            },
            onCancel = state::cancel,
        )
    }
}

fun Modifier.folderReorderLift(state: FolderReorderState, index: Int): Modifier = graphicsLayer {
    if (state.draggedIndex != index) return@graphicsLayer
    val offset = state.liftOffset()
    translationX = offset.x
    translationY = offset.y
    scaleX = Tokens.Drag.LIFT_SCALE
    scaleY = Tokens.Drag.LIFT_SCALE
    rotationZ = Tokens.Drag.LIFT_ROTATION
}

private fun Offset.fromLiftedLayer(): Offset {
    val radians = Tokens.Drag.LIFT_ROTATION / STRAIGHT_ANGLE_DEGREES * PI.toFloat()
    val cos = cos(radians)
    val sin = sin(radians)
    return Offset(
        x = (x * cos - y * sin) * Tokens.Drag.LIFT_SCALE,
        y = (x * sin + y * cos) * Tokens.Drag.LIFT_SCALE,
    )
}

private suspend fun PointerInputScope.detectFolderReorder(
    onStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onFinish: (moved: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
        onStart()
        when (val moved = trackLiftedPointer(down.id, onDrag)) {
            null -> onCancel()
            else -> onFinish(moved)
        }
    }
}

private const val STRAIGHT_ANGLE_DEGREES = 180f
