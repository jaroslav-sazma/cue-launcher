package app.cue.launcher.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

// The page is the far plane: blurred deeply enough to read as distance, so the cards floating on it
// — which carry their own, much clearer regions — stand off it instead of dissolving into it. The
// blur is the real shape-clipped wallpaper blur of API 37, not a gradient painted on top.
@Composable
fun Modifier.panelSurface(shape: Shape, cornerRadius: Dp): Modifier {
    val colors = LocalCueColors.current
    // Thin tint, thick blur: the page has to read as distance without washing out, because the
    // cards standing on it are what should hold the eye.
    val fill = colors.glass(GlassLevel.THIN)
    return wallpaperBlurRegion(
        key = PANEL_BLUR_REGION,
        cornerRadius = cornerRadius,
        blurRadius = colors.blurRadius(GlassLevel.THICK),
    ).clip(shape).drawBehind { drawRect(fill) }
}

// A card is a pane held closer to the eye than the page behind it, so it blurs *less*: you read
// more of the wallpaper through it than around it, and that is what makes it float.
//
// The glass belongs to the pane and to nothing else around it: an item can open a resize handle
// below its pane, and a region placed on the box that holds both would spread its blur under the
// handle too. The arrival layer goes on that box instead — above the pane, which is what lets the
// region inherit the travel, since a region is placed from layout coordinates and those carry only
// the transforms sitting above the element.
@Composable
fun Modifier.panelCardGlass(
    key: String,
    index: Int,
    count: Int,
    reducedMotion: Boolean,
    progress: () -> Float,
): Modifier {
    val colors = LocalCueColors.current
    val radius = Tokens.Radius.tile4x2
    return wallpaperBlurRegion(
        key = "$PANEL_BLUR_REGION.$key",
        cornerRadius = radius,
        blurRadius = colors.blurRadius(GlassLevel.THIN),
        zIndex = CARD_Z,
        localDepth = { cardArrivalDepth(progress(), index, count) },
    ).specularRake(
        reducedMotion = reducedMotion,
        index = index,
        count = count,
        radius = radius,
        progress = progress,
    )
}

private const val PANEL_BLUR_REGION = "panel"

// Above the page's own region, so the clearer pane wins where the two overlap.
private const val CARD_Z = 1f
