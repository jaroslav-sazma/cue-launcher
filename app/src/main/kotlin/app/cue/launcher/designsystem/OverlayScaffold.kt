package app.cue.launcher.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

@Composable
fun OverlayScaffold(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    progress: () -> Float = { 1f },
    dismissLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalCueColors.current
    val reducedMotion = rememberReducedMotion()

    val backPreview = rememberBackPreview(
        onDismiss = onDismiss,
        spec = spatialSlowSpec(reducedMotion),
    )
    Box(
        modifier = modifier
            .fillMaxSize()

            .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
            .then(
                if (dismissLabel == null) {
                    Modifier
                } else {

                    Modifier.semantics {
                        contentDescription = dismissLabel
                        onClick(label = dismissLabel) {
                            onDismiss()
                            true
                        }
                    }
                },
            )
            .graphicsLayer { alpha = progress().coerceIn(0f, 1f) }

            // The scrim's blur comes up with the overlay, and it does that through the depth
            // lambda rather than the radius parameter so the travel never reaches composition.
            .wallpaperBlurRegion(
                key = "overlay",
                cornerRadius = Tokens.Radius.overlayScrim,
                blurRadius = Tokens.Material.thin.blurRadius,
                zIndex = OVERLAY_BLUR_UNDER_BLOCKS,
                localDepth = { BlurRegionDepth(blurScale = progress().coerceIn(0f, 1f)) },
            )
            .clip(RoundedCornerShape(Tokens.Radius.overlayScrim))
            .background(colors.glass(GlassLevel.THIN)),
        contentAlignment = Alignment.Center,
    ) {
        OverlayPanel(
            title = title,
            modifier = Modifier
                .width(Tokens.Folder.panelWidth)
                .overlayPanelEnter(progress, reducedMotion)
                .backPreview(backPreview, reducedMotion)
                .closeOnHandleSwipe(onDismiss),
            content = content,
        )
    }
}

@Composable
private fun OverlayPanel(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalCueColors.current
    TileSurface(
        modifier = modifier,
        level = GlassLevel.REGULAR,
        radius = Tokens.Radius.overlayPanel,
        contentPadding = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(
                top = Tokens.Folder.panelPaddingTop,
                start = Tokens.Folder.panelPaddingHorizontal,
                end = Tokens.Folder.panelPaddingHorizontal,
                bottom = Tokens.Folder.panelPaddingBottom,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Tokens.Folder.handleGap),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = Tokens.Folder.handleWidth, height = Tokens.Folder.handleHeight)
                        .clip(RoundedCornerShape(Tokens.Folder.handleHeight / 2))
                        .background(colors.textPrimary.copy(alpha = Tokens.Folder.HANDLE_OPACITY)),
                )
            }
            if (title != null) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    style = Tokens.Folder.title.toTextStyle(),
                    maxLines = Tokens.Type.LABEL_LINES,
                    overflow = Tokens.Type.labelOverflow,
                    modifier = Modifier.padding(bottom = Tokens.Folder.titleGap),
                )
            }
            content()
        }
    }
}

private const val OVERLAY_BLUR_UNDER_BLOCKS = -1f

// What Modifier.blur builds, but with the radius read in the draw phase and the layer attached only
// while an overlay is on screen at all. It sits on the whole launcher, so it is an offscreen
// composite of every page — the thing Modifier.homeRecede goes out of its way to avoid — and
// rebuilding it per frame would relayout the pager underneath for the length of a slow animation.
@Composable
fun Modifier.overlayBackdrop(progress: () -> Float): Modifier {
    val current = rememberUpdatedState(progress)
    val open by remember { derivedStateOf { current.value() > 0f } }
    if (!open) return this
    return graphicsLayer {
        val pixels = Tokens.Folder.backgroundBlur.toPx() * current.value().coerceIn(0f, 1f)
        renderEffect = if (pixels > 0f) BlurEffect(pixels, pixels, TileMode.Clamp) else null
        shape = RectangleShape
        clip = true
    }
}

private fun Modifier.overlayPanelEnter(progress: () -> Float, reducedMotion: Boolean): Modifier =
    graphicsLayer {
        val amount = progress().coerceIn(0f, 1f)
        alpha = amount

        compositingStrategy = CompositingStrategy.ModulateAlpha
        if (!reducedMotion) {
            val scale = lerp(Tokens.Folder.ENTER_SCALE, 1f, amount)
            scaleX = scale
            scaleY = scale
        }
    }

@Composable
private fun Modifier.closeOnHandleSwipe(onDismiss: () -> Unit): Modifier {
    val dismiss by rememberUpdatedState(onDismiss)
    val threshold = with(LocalDensity.current) { Tokens.Drag.liftDistance.toPx() }
    return pointerInput(threshold) {
        var travel = 0f
        detectVerticalDragGestures(
            onDragStart = { travel = 0f },
            onVerticalDrag = { _, amount ->
                travel += amount
                if (travel > threshold) {
                    travel = 0f
                    dismiss()
                }
            },
        )
    }
}
