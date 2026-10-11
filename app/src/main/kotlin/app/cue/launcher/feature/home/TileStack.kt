package app.cue.launcher.feature.home

import android.content.Context
import android.view.View
import app.cue.launcher.feature.dragdrop.followPointer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.cue.launcher.R
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.StackIndicator
import app.cue.launcher.designsystem.TileSurface
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.designsystem.effectsFastSpec
import app.cue.launcher.designsystem.rememberReducedMotion
import app.cue.launcher.designsystem.spatialFastSpec
import app.cue.launcher.engine.QueueEntry
import app.cue.launcher.feature.widgets.SCROLL_DOWN
import app.cue.launcher.feature.widgets.SCROLL_UP
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.feature.widgets.WidgetTouchArbitration
import app.cue.launcher.feature.widgets.locationInWindow
import app.cue.launcher.feature.widgets.scrollsVerticallyAt

@Composable
fun TileStack(
    pages: List<QueueEntry>,
    hint: StackHint,
    modifier: Modifier = Modifier,
    hostedWidget: (QueueEntry) -> View? = { null },
    content: @Composable (QueueEntry) -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var direction by remember { mutableIntStateOf(1) }
    val index = stackIndexOf(pages, selectedId)
    val entry = pages[index]

    val onStep = { step: Int ->
        val next = stackPageAt(index, step, pages.size)
        if (next != index) {
            direction = step
            selectedId = pages[next].scenario.id
            hint.dismiss()
        }
    }
    val nextLabel = stringResource(R.string.stack_next)
    val previousLabel = stringResource(R.string.stack_previous)
    val dots = stackDotWindow(pages.size, index, Tokens.Indicator.Stack.MAX_DOTS)

    Box(
        modifier = modifier
            .tileStackSwipe(onStep) { hostedWidget(entry) }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(nextLabel) {
                        onStep(1)
                        true
                    },
                    CustomAccessibilityAction(previousLabel) {
                        onStep(-1)
                        true
                    },
                )
            },
    ) {
        StackPage(entry = entry, direction = direction, content = content)
        StackIndicator(
            dots = dots,
            activeIndex = index,
            plusCount = stackPlusCount(pages.size, dots.count()),
            overWidget = entry.scenario.content is TileContent.AppWidget,
            contentDescription = stringResource(R.string.stack_position, index + 1, pages.size),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = Tokens.Indicator.Stack.edgeOffset),
        )
        if (hint.visible) {
            StackHintPill(
                onDismiss = hint::dismiss,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Tokens.Indicator.Stack.edgeOffset),
            )
        }
    }
}

@Composable
private fun StackPage(
    entry: QueueEntry,
    direction: Int,
    content: @Composable (QueueEntry) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val fade = effectsFastSpec()
    val slide = spatialFastSpec<IntOffset>(reducedMotion)
    AnimatedContent(
        targetState = entry,
        contentKey = { it.scenario.id },
        modifier = Modifier.fillMaxSize(),
        label = "stack.swipe",
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(fade) togetherWith fadeOut(fade)
            } else {
                (slideInVertically(slide) { height -> direction * height } + fadeIn(fade))
                    .togetherWith(
                        slideOutVertically(slide) { height -> -direction * height } + fadeOut(fade),
                    )
            }
        },
    ) { page -> content(page) }
}

@Composable
private fun StackHintPill(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    TileSurface(
        modifier = modifier.clickable(onClick = onDismiss),
        level = GlassLevel.THICK,
        radius = Tokens.Indicator.Stack.pillRadius,
        contentPadding = 0.dp,
    ) {
        Text(
            text = stringResource(R.string.stack_hint),
            color = LocalCueColors.current.textPrimary,
            style = Tokens.Type.label.toTextStyle(),
            maxLines = Tokens.Type.LABEL_LINES,
            overflow = Tokens.Type.labelOverflow,
            modifier = Modifier.padding(
                horizontal = Tokens.Indicator.Stack.hintPaddingHorizontal,
                vertical = Tokens.Indicator.Stack.hintPaddingVertical,
            ),
        )
    }
}

// A hosted widget keeps every touch it is handed, including the swipes it has no use for, and the
// interop filter consumes them on the way back up — a drag detector above it never even starts. So
// the stack claims the swipe on the way down instead: it reads the initial pass ahead of the host
// view, asks the widget whether the drag is one it would scroll, and consumes only from the moment
// the gesture is its own. Everything it does not claim, down to the touch slop, reaches the widget.
@Composable
private fun Modifier.tileStackSwipe(onStep: (Int) -> Unit, hostedWidget: () -> View?): Modifier {
    val step by rememberUpdatedState(onStep)
    val widget by rememberUpdatedState(hostedWidget)
    val slop = LocalViewConfiguration.current.touchSlop
    val threshold = with(LocalDensity.current) { Tokens.Drag.swipeTravel.toPx() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .pointerInput(threshold, slop) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val swipe = StackSwipe(slop, threshold, origin, widget())
                followPointer(down.id, PointerEventPass.Initial) { change ->
                    val moved = swipe.onDrag(change)
                    if (moved != 0) step(moved)
                    true
                }
            }
        }
}

// The stack's half of the arbitration with the widget under it: it follows the drag until the
// widget has no use for it, and from that moment consumes, which is what makes the interop filter
// cancel the host view. A widget that scrolls where the finger went keeps the whole gesture.
private class StackSwipe(
    slopPx: Float,
    private val thresholdPx: Float,
    private val origin: Offset,
    private val widget: View?,
) {
    private val arbitration = WidgetTouchArbitration(slopPx)
    private val location = IntArray(2)
    private var travel = 0f
    private var owned = false

    fun onDrag(change: PointerInputChange): Int {
        val delta = change.positionChange()
        if (!owned && !claim(change.position, delta)) return 0
        change.consume()
        travel += delta.y
        val moved = stackStep(travel, thresholdPx)
        if (moved != 0) travel = 0f
        return moved
    }

    private fun claim(position: Offset, delta: Offset): Boolean {
        val at = widget?.let { origin + position - it.locationInWindow(location) }
        owned = arbitration.onDrag(
            deltaXPx = delta.x,
            deltaYPx = delta.y,
            canScrollUp = at != null && widget.scrollsVerticallyAt(at, SCROLL_UP),
            canScrollDown = at != null && widget.scrollsVerticallyAt(at, SCROLL_DOWN),
        ) && arbitration.vertical
        return owned
    }
}

// The host view of the widget a stack page shows, if that page is one: what the swipe above it has
// to get past.
fun hostedWidgetOf(entry: QueueEntry, context: Context, widgets: WidgetHosting): View? =
    (entry.scenario.content as? TileContent.AppWidget)
        ?.boundAppWidgetId
        ?.let { widgets.hostedView(context, it)?.view }

@Immutable
class StackHint(val visible: Boolean, private val onSeen: () -> Unit) {

    fun dismiss() = onSeen()

    fun hidden(): StackHint = StackHint(visible = false, onSeen = onSeen)

    companion object {

        val NONE = StackHint(visible = false, onSeen = {})
    }
}
