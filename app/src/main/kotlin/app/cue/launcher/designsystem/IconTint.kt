package app.cue.launcher.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

@Immutable
data class IconTint(val background: Int, val foreground: Int, val edge: Int = Color.Transparent.toArgb())

@Composable
fun rememberIconTint(): IconTint {
    val colors = LocalCueColors.current
    return remember(colors) {
        IconTint(
            background = colors.glass(GlassLevel.THICK).toArgb(),
            foreground = colors.tintedIcon.toArgb(),
            edge = colors.edge(GlassLevel.THICK).toArgb(),
        )
    }
}

val LocalSurfaceIconTint: ProvidableCompositionLocal<IconTint?> = staticCompositionLocalOf { null }

val LocalTintedIcons: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }
