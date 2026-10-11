package app.cue.launcher.designsystem

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle

@Composable
fun labelOnWallpaperStyle(): TextStyle {
    val colors = LocalCueColors.current
    val spec = Tokens.Type.labelOnWallpaperShadow
    val density = LocalDensity.current
    return Tokens.Type.label.toTextStyle(color = colors.labelOnWallpaper).copy(
        shadow = Shadow(
            color = colors.labelOnWallpaperShadow,
            offset = with(density) { Offset(0f, spec.offsetY.toPx()) },
            blurRadius = with(density) { spec.blurRadius.toPx() },
        ),
    )
}

fun labelsVisibleAt(fontScale: Float): Boolean =
    fontScale <= Tokens.Type.HIDE_LABELS_ABOVE_FONT_SCALE

@Composable
fun IconLabel(text: String, style: TextStyle) {
    if (!labelsVisibleAt(LocalDensity.current.fontScale)) return
    Spacer(modifier = Modifier.height(Tokens.Type.labelGapBelowIcon))
    Text(
        text = text,
        style = style,
        maxLines = Tokens.Type.LABEL_LINES,
        overflow = Tokens.Type.labelOverflow,
    )
}

@Composable
fun labelOnZoneStyle(): TextStyle =
    Tokens.Type.label.toTextStyle(color = LocalCueColors.current.labelOnZone)
