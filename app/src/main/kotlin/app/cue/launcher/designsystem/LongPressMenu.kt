package app.cue.launcher.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.cue.launcher.R

@Composable
fun CueMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchor: MenuAnchor,

    ready: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalMenuBackdrop.current
    val reducedMotion = rememberReducedMotion()
    val open = expanded && ready
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = spatialFastSpec(reducedMotion),
        label = "menu.enter",
    )

    val current by rememberUpdatedState(anchor)
    val dismiss by rememberUpdatedState(onDismissRequest)
    DisposableEffect(open, backdrop) {
        val token = if (open) backdrop.open(current) { dismiss() } else null
        onDispose { token?.let(backdrop::close) }
    }

    if (!open && progress <= 0f) return

    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density)
    val safeBottomInset = insets.getBottom(density)

    val metrics = remember(open) {
        MenuMetrics(
            gap = with(density) { Tokens.Menu.anchorGap.roundToPx() },
            screenInset = with(density) { Tokens.Menu.screenInset.roundToPx() },
            safeTop = safeTop,
            safeBottomInset = safeBottomInset,
        )
    }

    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    val safeHeight = remember(open) {
        windowHeight - with(density) { (safeTop + safeBottomInset).toDp() }
    }
    val placement = remember { mutableStateOf<MenuPlacement?>(null) }

    Popup(
        popupPositionProvider = WindowOriginPositionProvider,
        onDismissRequest = onDismissRequest,

        properties = PopupProperties(focusable = true, clippingEnabled = false),
    ) {

        MenuLayout(
            anchor = anchor,
            metrics = metrics,
            placement = placement,

            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } },
        ) {
            MenuPanel(
                width = anchor.width,
                maxHeight = safeHeight,
                progress = progress,
                placement = placement,
                reducedMotion = reducedMotion,
                content = content,
            )
        }
    }
}

@Composable
private fun MenuLayout(
    anchor: MenuAnchor,
    metrics: MenuMetrics,
    placement: MutableState<MenuPlacement?>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val panel = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val press = anchor.pressPoint
        val result = menuPlacement(
            targetTop = anchor.bounds.top,
            targetBottom = anchor.bounds.bottom,
            pressX = press.x,
            pressY = press.y,
            popupWidth = panel.width,
            popupHeight = panel.height,
            windowWidth = constraints.maxWidth,
            gap = metrics.gap,
            inset = metrics.screenInset,
            safeTop = metrics.safeTop,
            safeBottom = constraints.maxHeight - metrics.safeBottomInset,
        )
        placement.value = result
        layout(constraints.maxWidth, constraints.maxHeight) { panel.place(result.x, result.y) }
    }
}

@Composable
private fun MenuPanel(
    width: Dp,
    maxHeight: Dp,
    progress: Float,
    placement: MutableState<MenuPlacement?>,
    reducedMotion: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val title = stringResource(R.string.menu_pane)
    TileSurface(
        modifier = Modifier
            .width(width)
            .heightIn(max = maxHeight.coerceAtLeast(0.dp))
            .graphicsLayer {
                val amount = progress.coerceIn(0f, 1f)
                alpha = amount

                compositingStrategy = CompositingStrategy.ModulateAlpha
                if (!reducedMotion) {

                    placement.value?.let { transformOrigin = TransformOrigin(it.originX, it.originY) }
                    val scale = lerp(Tokens.Menu.ENTER_SCALE, 1f, amount)
                    scaleX = scale
                    scaleY = scale
                }
            }

            .pointerInput(Unit) { detectTapGestures { } }
            .semantics {
                paneTitle = title
                isTraversalGroup = true
            },
        level = GlassLevel.THICK,
        radius = Tokens.Menu.radius,
        contentPadding = Tokens.Menu.padding,
        shadow = Tokens.Elevation.menu,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}

@Composable
fun CueMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    trailingGlyph: String? = null,
) {
    val colors = LocalCueColors.current
    val interaction = remember { MutableInteractionSource() }

    // Every item closes the menu, and the menu keeps rendering while it animates away. The item
    // stays where it was and only goes disabled, so the closing menu cannot be tapped twice.
    var used by remember { mutableStateOf(false) }
    val live = enabled && !used
    val ink = if (destructive) colors.semanticDanger else colors.textPrimary

    val lines = if (LocalDensity.current.fontScale > Tokens.Menu.WRAP_LABELS_ABOVE_FONT_SCALE) {
        Tokens.Menu.ITEM_LINES_WRAPPED
    } else {
        Tokens.Type.LABEL_LINES
    }
    Row(
        modifier = modifier.menuItemSurface(live, interaction) {
            used = true
            onClick()
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            GlyphIcon(name = glyph, size = Tokens.Menu.glyph, tint = ink)
            Spacer(modifier = Modifier.width(Tokens.Menu.glyphGap))
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = ink,
            style = Tokens.Menu.item.toTextStyle(),
            maxLines = lines,
            overflow = Tokens.Type.labelOverflow,
        )
        if (trailingGlyph != null) {
            Spacer(modifier = Modifier.width(Tokens.Menu.glyphGap))
            GlyphIcon(
                name = trailingGlyph,
                size = Tokens.Menu.trailingGlyph,
                tint = colors.textSecondary.copy(alpha = Tokens.Menu.TRAILING_OPACITY),
            )
        }
    }
}

@Composable
private fun Modifier.menuItemSurface(
    live: Boolean,
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier {
    val colors = LocalCueColors.current
    val pressed by interaction.collectIsPressedAsState()
    val press = colors.textPrimary.copy(
        alpha = if (colors.isDark) Tokens.Menu.PRESS_OPACITY_DARK else Tokens.Menu.PRESS_OPACITY_LIGHT,
    )
    return this
        .fillMaxWidth()
        .heightIn(min = Tokens.Menu.itemHeight)
        .clip(RoundedCornerShape(Tokens.Menu.itemRadius))
        .background(if (pressed && live) press else Color.Transparent)
        .clickable(enabled = live, interactionSource = interaction, indication = null, onClick = onClick)
        .then(
            if (live) Modifier else Modifier.graphicsLayer { alpha = Tokens.Menu.DISABLED_OPACITY },
        )
        .padding(horizontal = Tokens.Menu.itemPaddingHorizontal)
}

@Composable
fun CueMenuHeader(icon: ImageBitmap?, title: String) {
    val colors = LocalCueColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Tokens.Menu.headerPaddingHorizontal,
                end = Tokens.Menu.headerPaddingHorizontal,
                top = Tokens.Menu.headerPaddingTop,
                bottom = Tokens.Menu.headerPaddingBottom,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon = icon, contentDescription = null, size = Tokens.Menu.headerIcon)
        Spacer(modifier = Modifier.width(Tokens.Menu.headerGap))
        Text(
            text = title,
            color = colors.textPrimary,
            style = Tokens.Menu.header.toTextStyle(),
            maxLines = Tokens.Type.LABEL_LINES,
            overflow = Tokens.Type.labelOverflow,
        )
    }
}

@Composable
fun CueMenuSection(label: String) {
    Text(
        text = label,
        modifier = Modifier.padding(
            start = Tokens.Menu.itemPaddingHorizontal,
            end = Tokens.Menu.itemPaddingHorizontal,
            top = Tokens.Menu.sectionPaddingTop,
            bottom = Tokens.Menu.sectionPaddingBottom,
        ),
        color = LocalCueColors.current.accent,
        style = Tokens.Type.eyebrow.toTextStyle(),
    )
}

@Composable
fun CueMenuDestructiveItem(label: String, separated: Boolean = true, onClick: () -> Unit) {

    if (separated) CueMenuRule()
    CueMenuItem(label = label, glyph = MENU_REMOVE_GLYPH, destructive = true, onClick = onClick)
}

private const val MENU_REMOVE_GLYPH = "delete-outline"

@Composable
fun CueMenuRule() {
    val colors = LocalCueColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Menu.ruleInset, vertical = Tokens.Menu.ruleGap)
            .height(Tokens.Menu.ruleThickness)
            .background(
                colors.textPrimary.copy(
                    alpha = if (colors.isDark) {
                        Tokens.Menu.RULE_OPACITY_DARK
                    } else {
                        Tokens.Menu.RULE_OPACITY_LIGHT
                    },
                ),
            ),
    )
}

private object WindowOriginPositionProvider : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

private class MenuMetrics(
    val gap: Int,
    val screenInset: Int,
    val safeTop: Int,
    val safeBottomInset: Int,
)
