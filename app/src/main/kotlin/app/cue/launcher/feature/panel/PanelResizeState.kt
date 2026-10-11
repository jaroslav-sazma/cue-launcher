package app.cue.launcher.feature.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.cue.launcher.R
import app.cue.launcher.data.widgets.PanelItem
import app.cue.launcher.data.widgets.PanelItemKey
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.Tokens

@Stable
class PanelResizeState(
    val maxRows: Int,
    private val rowPx: Float,
    private val autoScroller: PanelAutoScroller,
    private val speedPx: Float,
    private val onStep: () -> Unit,
    private val onApply: (Int, Int) -> Unit,
) {

    private var active: PanelItemKey? by mutableStateOf(null)

    private var pendingRows: Int? by mutableStateOf(null)

    private var drag: PanelResizeDrag? by mutableStateOf(null)

    val running: Boolean get() = active != null

    fun isActive(key: PanelItemKey): Boolean = active == key

    fun start(key: PanelItemKey) {
        active = key
        pendingRows = null
    }

    fun stop() {
        autoScroller.stop()
        drag = null
        active = null
        pendingRows = null
    }

    fun rowsOf(item: PanelItem.AppWidget): Int {
        val stored = panelWidgetRows(item.widget.heightRows, maxRows)
        return if (isActive(item.key)) pendingRows ?: stored else stored
    }

    fun heightOf(item: PanelItem.AppWidget): Dp = Tokens.Panel.widgetRow * rowsOf(item)

    fun stepTo(appWidgetId: Int, rows: Int) {
        onApply(appWidgetId, panelWidgetRows(rows, maxRows))
    }

    internal fun dragStart(item: PanelItem.AppWidget, pointerY: Float) {
        val rows = rowsOf(item)
        drag = PanelResizeDrag(startRows = rows, startPointerY = pointerY)
        pendingRows = rows
    }

    // The follower waits for the first real movement: the handle of a widget sitting low in the
    // panel grabs inside the edge zone, and merely touching it must not inflate the widget.
    internal fun dragBy(delta: Float) {
        val current = drag ?: return
        if (!current.following) {
            current.following = true
            autoScroller.follow(speedPx = speedPx, pointerY = { drag?.pointerY }) { scrolled ->
                // Only the pixels the follower really scrolled count as travel. Reading the raw
                // offset instead would feed back: shrinking content reclamps it, and the widget
                // would keep shrinking on its own.
                drag?.byScroll(scrolled)
                applyRows()
            }
        }
        current.byFinger(delta)
        applyRows()
    }

    internal fun dragEnd(appWidgetId: Int) {
        val rows = pendingRows
        autoScroller.stop()
        drag = null
        if (rows != null) onApply(appWidgetId, rows)
    }

    private fun applyRows() {
        val current = drag ?: return
        val next = panelResizeRows(current.startRows, current.travel, rowPx, maxRows)
        if (next != pendingRows) {
            pendingRows = next
            onStep()
        }
    }
}

// The finger and the handle move together, so the travel that grows the widget is the finger's own
// movement plus whatever the panel scrolled underneath it. The pointer the follower steers by counts
// the finger alone: feeding the scroll back in would make it chase itself deeper into the edge zone
// every frame, and the proportional ramp would collapse into full speed no matter how far in the
// grab really is.
private class PanelResizeDrag(val startRows: Int, private val startPointerY: Float) {

    var travel = 0f
        private set

    private var fingerTravel = 0f

    var following = false

    val pointerY: Float get() = startPointerY + fingerTravel

    fun byFinger(delta: Float) {
        travel += delta
        fingerTravel += delta
    }

    fun byScroll(delta: Float) {
        travel += delta
    }
}

@Composable
fun rememberPanelResizeState(
    autoScroller: PanelAutoScroller,
    viewportHeightPx: Float,
    onApply: (Int, Int) -> Unit,
): PanelResizeState {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val rowPx = with(density) { Tokens.Panel.widgetRow.toPx() }
    val maxRows = panelRowsInViewport(viewportHeightPx, rowPx)
    val apply by rememberUpdatedState(onApply)
    val speedPx = with(density) { Tokens.Panel.Resize.autoScrollSpeed.toPx() }
    return remember(maxRows, rowPx, autoScroller, speedPx, haptics) {
        PanelResizeState(
            maxRows = maxRows,
            rowPx = rowPx,
            autoScroller = autoScroller,
            speedPx = speedPx,
            onStep = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
            onApply = { id, rows -> apply(id, rows) },
        )
    }
}

// Swallows every touch over the widget while the handle is out, so the widget itself cannot react;
// releasing leaves the mode. A reorder long press still wins, it consumes on the same pass earlier.
fun Modifier.panelResizeBlocker(onStop: () -> Unit): Modifier = pointerInput(onStop) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
        onStop()
    }
}

// Watches from the Final pass only: a touch that nobody claimed — empty panel space, a scroll —
// leaves the resize mode, while the handle keeps its own gesture because it consumes the down.
fun Modifier.panelResizeDismiss(state: PanelResizeState): Modifier = pointerInput(state.running) {
    if (!state.running) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        if (!down.isConsumed) state.stop()
    }
}

@Composable
fun PanelResizeHandle(
    onStart: (pointerY: Float) -> Unit,
    onDrag: (delta: Float) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCueColors.current
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val finish by rememberUpdatedState(onFinish)
    val label = stringResource(R.string.panel_widget_resize_handle)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Tokens.Panel.Resize.handleHeight)
            .onGloballyPositioned { coordinates = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    // The down is consumed so the panel does not read the grab as a tap elsewhere.
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val anchor = coordinates?.takeIf { it.isAttached }
                    start(anchor?.localToRoot(down.position)?.y ?: down.position.y)
                    verticalDrag(down.id) { change ->
                        // Read before consuming: a consumed change reports no movement at all.
                        val delta = change.positionChange().y
                        change.consume()
                        drag(delta)
                    }
                    finish()
                }
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            modifier = Modifier
                .width(Tokens.Panel.Resize.pillWidth)
                .height(Tokens.Panel.Resize.pillHeight)
                .background(
                    color = colors.glass(GlassLevel.REGULAR),
                    shape = RoundedCornerShape(Tokens.Panel.Resize.pillRadius),
                ),
        )
    }
}
