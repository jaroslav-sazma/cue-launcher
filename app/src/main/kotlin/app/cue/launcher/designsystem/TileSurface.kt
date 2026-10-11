package app.cue.launcher.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

@Composable
fun TileSurface(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.REGULAR,
    radius: Dp = Tokens.Radius.tile2x2,
    contentPadding: Dp = Tokens.Padding.tile,
    shadow: ShadowSpec? = Tokens.Elevation.tile,

    shape: Shape = RoundedCornerShape(radius),

    surface: Color? = null,
    sheen: Brush? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalCueColors.current

    val spec = shadow?.takeUnless { colors.reducedTransparency }
    val light = sheen?.takeUnless { colors.reducedTransparency }
    Box(
        modifier = modifier
            .then(if (spec == null) Modifier else Modifier.cueShadow(shape, spec))
            .clip(shape)
            .background(surface ?: colors.glass(level))
            .then(if (light == null) Modifier else Modifier.background(light))
            .border(width = Tokens.Material.edgeWidth, color = colors.edge(level), shape = shape)
            .padding(contentPadding),
        content = content,
    )
}
