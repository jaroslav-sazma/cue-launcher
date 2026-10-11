package app.cue.launcher.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp

fun panelEmergeScale(progress: Float): Float =
    lerp(Tokens.Motion.Depth.PANEL_SCALE, 1f, progress.coerceIn(0f, 1f))

fun panelEmergeAlpha(progress: Float): Float =
    lerp(Tokens.Motion.Depth.PANEL_ALPHA, 1f, progress.coerceIn(0f, 1f))

// Negative: the panel starts further left than the pager places it and closes that extra distance
// by the time the swipe settles, so the near plane outruns the one behind it.
fun panelLead(progress: Float, leadPx: Float): Float = -leadPx * (1f - progress.coerceIn(0f, 1f))

fun panelEmergeDepth(progress: Float) = BlurRegionDepth(alpha = panelEmergeAlpha(progress))

fun Modifier.panelEmerge(reducedMotion: Boolean, progress: () -> Float): Modifier = graphicsLayer {
    val amount = progress()
    alpha = panelEmergeAlpha(amount)
    compositingStrategy = CompositingStrategy.ModulateAlpha
    if (!reducedMotion) {
        val scale = panelEmergeScale(amount)
        scaleX = scale
        scaleY = scale
        translationX = panelLead(amount, Tokens.Motion.Depth.panelLead.toPx())
    }
}
