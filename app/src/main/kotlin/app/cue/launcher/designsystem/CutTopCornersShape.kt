package app.cue.launcher.designsystem

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

data class CutTopCornersShape(private val radius: Dp) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = with(density) { radius.toPx() }
            .coerceAtMost(minOf(size.width / 2f, size.height))
        val path = Path().apply {
            moveTo(0f, 0f)

            arcTo(
                Rect(left = 0f, top = -r, right = 2 * r, bottom = r),
                startAngleDegrees = LEFT_EDGE_DEGREES,
                sweepAngleDegrees = -QUARTER_TURN_DEGREES,
                forceMoveTo = false,
            )
            lineTo(size.width - r, r)
            arcTo(
                Rect(left = size.width - 2 * r, top = -r, right = size.width, bottom = r),
                startAngleDegrees = BOTTOM_EDGE_DEGREES,
                sweepAngleDegrees = -QUARTER_TURN_DEGREES,
                forceMoveTo = false,
            )
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

private const val LEFT_EDGE_DEGREES = 180f
private const val BOTTOM_EDGE_DEGREES = 90f
private const val QUARTER_TURN_DEGREES = 90f
