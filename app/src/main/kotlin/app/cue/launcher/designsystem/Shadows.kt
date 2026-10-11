package app.cue.launcher.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

fun Modifier.cueShadow(
    shape: Shape,
    spec: ShadowSpec,
    color: Color = Color.Black,
): Modifier = dropShadow(shape) {
    radius = spec.blurRadius.toPx()
    offset = Offset(0f, spec.offsetY.toPx())
    this.color = color
    alpha = spec.alpha
}
