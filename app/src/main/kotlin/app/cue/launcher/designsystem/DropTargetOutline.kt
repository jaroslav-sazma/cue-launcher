package app.cue.launcher.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun Modifier.dropTargetOutline(visible: Boolean, hinted: Boolean = false): Modifier {
    val alpha by animateFloatAsState(
        targetValue = when {
            visible -> 1f
            hinted -> Tokens.Drag.HINT_OPACITY
            else -> 0f
        },
        animationSpec = effectsFastSpec(),
        label = "drag.target",
    )
    val color = LocalCueColors.current.accent
    return drawBehind {
        if (alpha <= 0f) return@drawBehind
        val stroke = Tokens.Drag.targetStroke.toPx()
        drawRoundRect(
            color = color,
            alpha = alpha,

            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(Tokens.Radius.icon.toPx()),
            style = Stroke(
                width = stroke,
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(Tokens.Drag.targetDash.toPx(), Tokens.Drag.targetGap.toPx()),
                ),
            ),
        )
    }
}
