package app.cue.launcher.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp

// Progress is 0 while home is settled and 1 while the panel is. Every value is read in the draw
// phase, so a swipe invalidates drawing and never recomposition.

fun homeRecedeScale(progress: Float): Float =
    lerp(1f, Tokens.Motion.Depth.HOME_SCALE, progress.coerceIn(0f, 1f))

fun homeRecedeAlpha(progress: Float): Float =
    lerp(1f, Tokens.Motion.Depth.HOME_ALPHA, progress.coerceIn(0f, 1f))

fun homeDefocus(progress: Float): Float =
    lerp(1f, Tokens.Motion.Depth.HOME_DEFOCUS, progress.coerceIn(0f, 1f))

fun homeSink(progress: Float, sinkPx: Float): Float = sinkPx * progress.coerceIn(0f, 1f)

fun homeRecedeDepth(progress: Float) = BlurRegionDepth(
    alpha = homeRecedeAlpha(progress),
    blurScale = homeDefocus(progress),
)

// The home screen hosts AppWidgetHostViews, and an offscreen composite is what breaks the
// SurfaceView-backed ones, so the alpha is modulated per drawing instruction instead.
fun Modifier.homeRecede(reducedMotion: Boolean, progress: () -> Float): Modifier = graphicsLayer {
    val amount = progress()
    alpha = homeRecedeAlpha(amount)
    compositingStrategy = CompositingStrategy.ModulateAlpha
    if (!reducedMotion) {
        val scale = homeRecedeScale(amount)
        scaleX = scale
        scaleY = scale
        translationY = homeSink(amount, Tokens.Motion.Depth.homeSink.toPx())
    }
}
