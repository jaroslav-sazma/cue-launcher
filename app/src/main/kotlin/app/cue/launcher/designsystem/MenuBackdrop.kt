package app.cue.launcher.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.round
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

sealed interface MenuDecoration {

    data object Press : MenuDecoration

    @Immutable
    class Lift(val content: @Composable () -> Unit) : MenuDecoration

    @Immutable
    class Capture(val layer: GraphicsLayer) : MenuDecoration
}

fun Modifier.menuLiftSource(layer: GraphicsLayer?): Modifier =
    if (layer == null) {
        this
    } else {
        drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
    }

@Composable
fun rememberMenuLiftLayer(active: Boolean): GraphicsLayer? {
    val layer = rememberGraphicsLayer()
    return layer.takeIf { active }
}

@Immutable
class MenuAnchor(

    val bounds: IntRect,

    val press: IntOffset? = null,

    val width: Dp = Tokens.Menu.width,
    val decoration: MenuDecoration? = null,
) {
    val pressPoint: IntOffset get() = press ?: bounds.center
}

fun LayoutCoordinates.menuAnchorBounds(): IntRect {
    val bounds = boundsInWindow()
    return IntRect(
        left = bounds.left.roundToInt(),
        top = bounds.top.roundToInt(),
        right = bounds.right.roundToInt(),
        bottom = bounds.bottom.roundToInt(),
    )
}

@Stable
class MenuBackdropState internal constructor(private val enabled: Boolean) {

    var anchor by mutableStateOf<MenuAnchor?>(null)
        private set

    var lastAnchor by mutableStateOf<MenuAnchor?>(null)
        private set

    private var owner: Any? = null
    private var ownerDismiss: (() -> Unit)? = null

    internal fun open(anchor: MenuAnchor, onDismiss: () -> Unit): Any {
        val previous = ownerDismiss
        val token = Any()
        owner = token
        ownerDismiss = onDismiss
        if (enabled) {
            this.anchor = anchor
            lastAnchor = anchor
        }
        previous?.invoke()
        return token
    }

    internal fun close(token: Any) {
        if (owner === token) {
            owner = null
            ownerDismiss = null
            anchor = null
        }
    }

    companion object {

        val NONE: MenuBackdropState = MenuBackdropState(enabled = false)
    }
}

val LocalMenuBackdrop: ProvidableCompositionLocal<MenuBackdropState> =
    staticCompositionLocalOf { MenuBackdropState.NONE }

@Composable
fun rememberMenuBackdropState(): MenuBackdropState = remember { MenuBackdropState(enabled = true) }

@Composable
fun MenuBackdropHost(
    state: MenuBackdropState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalCueColors.current
    val progress by animateFloatAsState(
        targetValue = if (state.anchor != null) 1f else 0f,
        animationSpec = effectsFastSpec(),
        label = "menu.backdrop",
    )

    var hostOrigin by remember { mutableStateOf(IntOffset.Zero) }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            hostOrigin = coordinates.boundsInWindow().topLeft.round()
        },
    ) {
        val blurRadiusPx = with(LocalDensity.current) {
            if (colors.reducedTransparency) 0f else Tokens.Menu.backdropBlur.toPx()
        }
        Box(modifier = Modifier.menuBackdropBlur(blurRadiusPx) { progress }) {
            CompositionLocalProvider(LocalMenuBackdrop provides state, content = content)
        }
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress }
                    .background(colors.glass(GlassLevel.THIN)),
            )
            state.lastAnchor?.let { anchor ->
                MenuDecorationLayer(anchor = anchor, hostOrigin = hostOrigin, progress = progress)
            }
        }
    }
}

private fun Modifier.menuBackdropBlur(radiusPx: Float, progress: () -> Float): Modifier =
    graphicsLayer {
        val amount = progress().coerceIn(0f, 1f) * radiusPx
        clip = true
        renderEffect = if (amount <= 0f) null else BlurEffect(amount, amount, TileMode.Decal)
    }

@Composable
private fun MenuDecorationLayer(anchor: MenuAnchor, hostOrigin: IntOffset, progress: Float) {
    val amount = progress.coerceIn(0f, 1f)
    when (val decoration = anchor.decoration) {
        null -> Unit
        MenuDecoration.Press -> PressAnchor(center = anchor.pressPoint - hostOrigin, alpha = amount)

        is MenuDecoration.Lift -> LiftedTarget(
            bounds = anchor.bounds,
            hostOrigin = hostOrigin,
            progress = amount,
            content = decoration.content,
        )

        is MenuDecoration.Capture -> LiftedTarget(
            bounds = anchor.bounds,
            hostOrigin = hostOrigin,
            progress = amount,

            content = {
                Box(modifier = Modifier.fillMaxSize().drawBehind { drawLayer(decoration.layer) })
            },
        )
    }
}

@Composable
private fun PressAnchor(center: IntOffset, alpha: Float) {
    val colors = LocalCueColors.current
    val diameter = Tokens.Menu.pressRipple
    val radiusPx = with(LocalDensity.current) { (diameter / 2).roundToPx() }
    Box(
        modifier = Modifier
            .offset { IntOffset(center.x - radiusPx, center.y - radiusPx) }
            .size(diameter)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(
                colors.tone.copy(
                    alpha = if (colors.isDark) {
                        Tokens.Menu.RIPPLE_OPACITY_DARK
                    } else {
                        Tokens.Menu.RIPPLE_OPACITY_LIGHT
                    },
                ),
            )
            .border(
                width = Tokens.Material.edgeWidth,
                color = Color(Tokens.Material.EDGE_COLOR).copy(
                    alpha = if (colors.isDark) {
                        Tokens.Menu.RIPPLE_EDGE_OPACITY_DARK
                    } else {
                        Tokens.Menu.RIPPLE_EDGE_OPACITY_LIGHT
                    },
                ),
                shape = CircleShape,
            ),
    )
}

@Composable
private fun LiftedTarget(
    bounds: IntRect,
    hostOrigin: IntOffset,
    progress: Float,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val topLeft = bounds.topLeft - hostOrigin
    Box(
        modifier = Modifier
            .offset { topLeft }
            .size(
                width = with(density) { bounds.width.toDp() },
                height = with(density) { bounds.height.toDp() },
            )
            .graphicsLayer {
                alpha = progress
                val scale = lerp(1f, Tokens.Drag.LIFT_SCALE, progress)
                scaleX = scale
                scaleY = scale
            }

            .clearAndSetSemantics { },
    ) {
        content()
    }
}
