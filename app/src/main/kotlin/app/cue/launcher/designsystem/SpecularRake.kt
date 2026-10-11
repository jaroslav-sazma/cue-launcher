package app.cue.launcher.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp

fun specularSheen(colors: CueColors, opacityLight: Float, opacityDark: Float, extent: Float): Brush =
    Brush.verticalGradient(
        0f to colors.edgeColor.copy(alpha = if (colors.isDark) opacityDark else opacityLight),
        extent to colors.edgeColor.copy(alpha = 0f),
    )

// Brightest halfway through the card's arrival and gone by the time it lands. The parabola peaks at
// a quarter, so it is normalised back to the token's value.
fun rakeStrength(progress: Float): Float {
    val amount = progress.coerceIn(0f, 1f)
    return Tokens.Motion.Depth.RAKE_PEAK * PARABOLA_NORMALISER * amount * (1f - amount)
}

fun rakeCentreFraction(progress: Float): Float =
    lerp(Tokens.Motion.Depth.RAKE_FROM, Tokens.Motion.Depth.RAKE_TO, progress.coerceIn(0f, 1f))

// Light catching a pane of glass as it turns towards you. Drawn as a rounded rect rather than
// clipped, so it cannot square off the card's corners.
fun Modifier.specularRake(
    reducedMotion: Boolean,
    index: Int,
    count: Int,
    radius: Dp,
    progress: () -> Float,
): Modifier = drawWithContent {
    drawContent()
    if (reducedMotion) return@drawWithContent
    val amount = cardStaggerProgress(progress(), index, count)
    val strength = rakeStrength(amount)
    if (strength <= 0f) return@drawWithContent
    val centre = size.width * rakeCentreFraction(amount)
    val half = size.width * Tokens.Motion.Depth.RAKE_WIDTH / 2f
    drawRoundRect(
        brush = Brush.linearGradient(
            0f to Color.Transparent,
            HALFWAY to Color.White.copy(alpha = strength),
            1f to Color.Transparent,
            start = Offset(centre - half, 0f),
            end = Offset(centre + half, size.height),
        ),
        cornerRadius = CornerRadius(radius.toPx()),
    )
}

private const val PARABOLA_NORMALISER = 4f
private const val HALFWAY = 0.5f
