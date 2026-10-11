package app.cue.launcher.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.tanh

// The platform overscroll stretches pixels, and a stretch is a render effect rather than a
// transform: the wallpaper blur underneath is placed from layout coordinates, which carry every
// transform above the element and nothing else, so the glass deforms and its blur stays put. Glass
// does not stretch anyway. This one slides the pane instead, which the region follows for free.
//
// The travel is compressed rather than clamped, so the surface never stops dead under the finger.
fun glassOverscrollTravel(rawPx: Float, limitPx: Float): Float {
    if (limitPx <= 0f) return 0f
    return limitPx * tanh(rawPx / limitPx)
}

// How much of a drag pays off an overscroll already standing at `rawPx` instead of reaching the
// scroll behind it. Nothing, unless the two point in opposite directions — and never more than
// what is standing there, so the payment cannot carry the surface out the other side.
fun glassOverscrollRelax(rawPx: Float, delta: Float): Float {
    if (rawPx == 0f || sign(delta) == sign(rawPx)) return 0f
    return if (abs(delta) >= abs(rawPx)) -rawPx else delta
}

@Stable
class GlassOverscrollEffect(
    private val orientation: Orientation,
    private val limitPx: Float,
    private val settleSpec: AnimationSpec<Float>,
) : OverscrollEffect {

    private var raw by mutableFloatStateOf(0f)

    private val travel: Float get() = glassOverscrollTravel(raw, limitPx)

    override val isInProgress: Boolean get() = raw != 0f

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val relaxed = relax(delta.axis)
        val forScroll = delta.withAxis(delta.axis - relaxed)
        val consumed = performScroll(forScroll)
        if (source == NestedScrollSource.UserInput) raw += forScroll.axis - consumed.axis
        return consumed.withAxis(relaxed + consumed.axis)
    }

    // A drag back towards the content pays off the overscroll before any of it reaches the scroll,
    // so the pane returns from exactly where the finger left it.
    private fun relax(delta: Float): Float {
        val paid = glassOverscrollRelax(raw, delta)
        raw += paid
        return paid
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        val left = (velocity - performFling(velocity)).axis
        if (raw == 0f && left == 0f) return
        animate(
            initialValue = raw,
            targetValue = 0f,
            initialVelocity = left,
            animationSpec = settleSpec,
        ) { value, _ -> raw = value }
    }

    // Placement and not a graphics layer: the blur regions inside read their bounds from the layout
    // coordinates, and a placement offset is the cheapest thing those carry.
    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints,
        ): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                // place and not placeRelative: the travel is accumulated from raw pointer deltas,
                // so it is already in screen coordinates and a right-to-left layout must not
                // mirror it back the other way.
                val offset = travel.roundToInt()
                when (orientation) {
                    Orientation.Vertical -> placeable.place(0, offset)
                    Orientation.Horizontal -> placeable.place(offset, 0)
                }
            }
        }
    }

    private val Offset.axis: Float
        get() = if (orientation == Orientation.Vertical) y else x

    private val Velocity.axis: Float
        get() = if (orientation == Orientation.Vertical) y else x

    private fun Offset.withAxis(value: Float): Offset =
        if (orientation == Orientation.Vertical) copy(y = value) else copy(x = value)
}

@Composable
fun rememberGlassOverscroll(orientation: Orientation): GlassOverscrollEffect {
    val reducedMotion = rememberReducedMotion()
    val limitPx = with(LocalDensity.current) {
        if (reducedMotion) 0f else Tokens.Motion.overscrollTravel.toPx()
    }
    val settleSpec = spatialDefaultSpec<Float>(reducedMotion)
    return remember(orientation, limitPx, settleSpec) {
        GlassOverscrollEffect(orientation, limitPx, settleSpec)
    }
}
