package app.cue.launcher.feature.panel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import app.cue.launcher.data.widgets.PanelItemKey
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.widgets.widgetLongPressDrag

@Stable
class PanelReorderState(
    private val autoScroller: PanelAutoScroller,
    private val speedPx: Float,
) {

    private val slots = mutableMapOf<Int, LayoutCoordinates>()

    private var snapshot: List<PanelSlotBounds> = emptyList()

    private var grab = Offset.Zero

    private var originLeft = 0f

    private var itemCount = 0

    private var pendingColumn: List<PanelItemKey> = emptyList()
    private var pending: PanelMove? by mutableStateOf(null)

    var draggedIndex: Int? by mutableStateOf(null)
        private set

    var targetIndex: Int? by mutableStateOf(null)
        private set

    private var pointer by mutableStateOf(Offset.Zero)

    private var scroll by mutableIntStateOf(0)

    fun previewOrder(column: List<PanelItemKey>): List<Int> {
        val held = pending?.takeIf { column == pendingColumn }
        return when {
            draggedIndex != null -> panelPreviewOrder(column.size, draggedIndex, targetIndex)
            held != null -> panelPreviewOrder(column.size, held.from, held.to)
            else -> panelPreviewOrder(column.size, null, null)
        }
    }

    internal fun liftOffset(): Offset {
        val order = panelPreviewOrder(itemCount, draggedIndex, targetIndex)
        val top = draggedIndex?.let { panelPreviewTop(snapshot, order, it) }
        return if (top == null) {
            Offset.Zero
        } else {
            Offset(
                x = pointer.x - grab.x - originLeft,
                y = pointer.y - grab.y - (top - scroll),
            )
        }
    }

    internal fun setCoordinates(slot: Int, coordinates: LayoutCoordinates) {
        slots[slot] = coordinates
    }

    internal fun start(index: Int, column: List<PanelItemKey>, pointerInSlot: Offset) {
        val bounds = slots[index]?.takeIf { it.isAttached }?.boundsInRoot() ?: return
        val itemCount = column.size
        this.itemCount = itemCount
        pendingColumn = column
        pending = null
        scroll = autoScroller.offset
        snapshot = slots.mapNotNull { (slot, coordinates) ->
            if (slot >= itemCount) return@mapNotNull null
            val rect = coordinates.takeIf { it.isAttached }?.boundsInRoot() ?: return@mapNotNull null
            PanelSlotBounds(slot, rect.top + scroll, rect.bottom + scroll)
        }
        grab = pointerInSlot
        originLeft = bounds.left
        pointer = bounds.topLeft + pointerInSlot
        draggedIndex = index
        targetIndex = index
        autoScroller.follow(speedPx = speedPx, pointerY = { pointer.y }) {
            scroll = autoScroller.offset
            updateTarget()
        }
    }

    internal fun drag(delta: Offset) {
        if (draggedIndex == null) return
        pointer += delta
        updateTarget()
    }

    internal fun finish(): PanelMove? {
        val from = draggedIndex
        val to = targetIndex
        cancel()
        val move = if (from != null && to != null && from != to) PanelMove(from, to) else null
        pending = move
        return move
    }

    internal fun cancel() {
        autoScroller.stop()
        draggedIndex = null
        targetIndex = null
    }

    private fun updateTarget() {
        if (draggedIndex == null) return
        targetIndex = panelDropIndex(snapshot, pointer.y + scroll) ?: targetIndex
    }
}

@Composable
fun rememberPanelReorderState(autoScroller: PanelAutoScroller): PanelReorderState {
    val density = LocalDensity.current
    return remember(autoScroller, density) {
        with(density) {
            PanelReorderState(
                autoScroller = autoScroller,
                speedPx = Tokens.Drag.autoScrollSpeed.toPx(),
            )
        }
    }
}

fun Modifier.panelReorderSlot(state: PanelReorderState, slot: Int): Modifier =
    onGloballyPositioned { coordinates -> state.setCoordinates(slot, coordinates) }

@Composable
@Suppress("LongParameterList")
fun Modifier.panelReorderSource(
    state: PanelReorderState,
    index: Int,
    column: List<PanelItemKey>,
    onMove: (PanelMove) -> Unit,
    onMenu: () -> Unit,
    locked: Boolean = false,

    hasMenu: Boolean = true,
): Modifier {
    val currentIndex by rememberUpdatedState(index)
    val currentColumn by rememberUpdatedState(column)
    val currentMove by rememberUpdatedState(onMove)
    val currentMenu by rememberUpdatedState(onMenu)
    val currentLocked by rememberUpdatedState(locked)
    if (locked && !hasMenu) return this
    return widgetLongPressDrag(
        onStart = { position ->
            if (!currentLocked) state.start(currentIndex, currentColumn, position)
        },
        onDrag = { delta -> if (!currentLocked) state.drag(delta) },
        onFinish = { moved ->
            val move = state.finish()
            when {

                currentLocked -> currentMenu()

                !moved -> currentMenu()
                move != null -> currentMove(move)
                else -> Unit
            }
        },
        onCancel = state::cancel,
    )
}

fun Modifier.panelReorderLift(state: PanelReorderState, index: Int): Modifier = graphicsLayer {
    if (state.draggedIndex != index) return@graphicsLayer
    val offset = state.liftOffset()
    translationX = offset.x
    translationY = offset.y
    scaleX = Tokens.Drag.LIFT_SCALE
    scaleY = Tokens.Drag.LIFT_SCALE
    rotationZ = Tokens.Drag.LIFT_ROTATION
}
