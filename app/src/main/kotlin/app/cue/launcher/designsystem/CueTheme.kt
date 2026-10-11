package app.cue.launcher.designsystem

import android.app.UiModeManager
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import app.cue.launcher.designsystem.glyph.LocalMdiGlyphs
import app.cue.launcher.designsystem.glyph.rememberMdiGlyphs

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CueTheme(
    wallpaperLuminance: Double,
    isDark: Boolean = isSystemInDarkTheme(),

    reducedTransparency: Boolean = false,
    accent: ThemeAccent = ThemeAccent.Primary,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = remember(context, isDark) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    val raisedContrast = remember(context, colorScheme) {
        (context.getSystemService(UiModeManager::class.java)?.contrast ?: 0f) > 0f
    }
    val cueColors = remember(wallpaperLuminance, isDark, reducedTransparency, accent, colorScheme) {
        CueColors(
            scheme = ContrastEngine.compute(
                wallpaperLuminance = wallpaperLuminance,
                isDark = isDark,
                reducedTransparency = reducedTransparency,
                accents = ContrastAccents(
                    accent = accent.rgbIn(colorScheme),
                    palette = ACCENT_ROLES.map { it(colorScheme).toRgb() },
                    glassReference = colorScheme.primary.toRgb(),
                ),
            ),
            tone = colorScheme.surface,
            isDark = isDark,
            reducedTransparency = reducedTransparency,
        )
    }
    CompositionLocalProvider(
        LocalCueColors provides cueColors,
        LocalRaisedContrast provides raisedContrast,

        LocalMdiGlyphs provides rememberMdiGlyphs(),
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}

@Immutable
sealed interface ThemeAccent {
    data object Primary : ThemeAccent
    data object Tertiary : ThemeAccent
    data class Custom(val rgb: Int) : ThemeAccent
}

private fun ThemeAccent.rgbIn(scheme: ColorScheme): Int = when (this) {
    ThemeAccent.Primary -> scheme.primary.toRgb()
    ThemeAccent.Tertiary -> scheme.tertiary.toRgb()
    is ThemeAccent.Custom -> rgb
}

private val ACCENT_ROLES: List<(ColorScheme) -> Color> = listOf(
    ColorScheme::primary,
    ColorScheme::tertiary,
    ColorScheme::secondary,
    ColorScheme::error,
)

@Immutable
data class CueColors(
    val scheme: ContrastScheme,
    val tone: Color,
    val isDark: Boolean,
    val reducedTransparency: Boolean,

    val edgeColor: Color = Color(Tokens.Material.EDGE_COLOR),
) {

    fun glass(level: GlassLevel): Color = if (reducedTransparency) {
        opaqueShade(level)
    } else {
        tone.copy(alpha = scheme.alphaOf(level))
    }

    // The dial sits one step lighter than the glass under it, in both modes.
    val clockDial: Color
        get() = when {
            reducedTransparency -> opaqueShade(GlassLevel.REGULAR)
            isDark -> edgeColor.copy(alpha = Tokens.CustomWidget.Clock.DIAL_OPACITY_DARK)
            else -> edgeColor.copy(alpha = Tokens.CustomWidget.Clock.DIAL_OPACITY_LIGHT)
        }

    val clockTick: Color
        get() = textPrimary.copy(
            alpha = if (isDark) {
                Tokens.CustomWidget.Clock.TICK_OPACITY_DARK
            } else {
                Tokens.CustomWidget.Clock.TICK_OPACITY_LIGHT
            },
        )

    fun edge(level: GlassLevel): Color = edgeColor.copy(
        alpha = if (reducedTransparency) {
            Tokens.Material.Opaque.EDGE_OPACITY
        } else {
            level.spec.edgeOpacity(isDark)
        },
    )

    fun blurRadius(level: GlassLevel): Dp = if (reducedTransparency) 0.dp else level.spec.blurRadius

    val textPrimary: Color = Color(scheme.textPrimary.opaqueArgb())
    val textSecondary: Color = Color(scheme.textSecondary.opaqueArgb())
    val eyebrow: Color = Color(scheme.eyebrow.opaqueArgb())
    val labelOnZone: Color = Color(scheme.labelOnZone.opaqueArgb())
    val labelOnWallpaper: Color = Color(scheme.labelOnWallpaper.opaqueArgb())
    val labelOnWallpaperShadow: Color = Color(scheme.labelOnWallpaperShadow.opaqueArgb())
    val tintedIcon: Color = Color(scheme.tintedIcon.opaqueArgb())
    val accent: Color = Color(scheme.accent.opaqueArgb())

    val accentPalette: List<Color> by lazy { scheme.accentPalette.map { Color(it.opaqueArgb()) } }

    fun onAccent(accent: Color = this.accent): Color =
        Color(ContrastEngine.glyphInkOn(accent.toRgb()).opaqueArgb())

    val semanticInfo: Color = Color(scheme.semanticInfo.opaqueArgb())
    val semanticSafe: Color = Color(scheme.semanticSafe.opaqueArgb())
    val semanticCaution: Color = Color(scheme.semanticCaution.opaqueArgb())
    val semanticDanger: Color = Color(scheme.semanticDanger.opaqueArgb())

    private fun opaqueShade(level: GlassLevel): Color {
        val step = when (level) {
            GlassLevel.THIN -> 0f
            GlassLevel.REGULAR -> Tokens.Material.Opaque.LIGHTNESS_STEP_REGULAR
            GlassLevel.THICK -> Tokens.Material.Opaque.LIGHTNESS_STEP_THICK
        }

        val signed = if (isDark) -step else step
        // Deliberately per-channel sRGB math: Compose's Color lerp interpolates in Oklab, which
        // would shift the shade's hue, not only its lightness.
        val mix = { channel: Float ->
            if (signed < 0) channel * (1 + signed) else channel + (1 - channel) * signed
        }
        return Color(red = mix(tone.red), green = mix(tone.green), blue = mix(tone.blue))
    }
}

val LocalCueColors: ProvidableCompositionLocal<CueColors> = staticCompositionLocalOf {
    error("LocalCueColors is unavailable — CueTheme is missing.")
}

private const val RGB_MASK = 0x00FFFFFF
private const val OPAQUE_ALPHA = 0xFF000000

private fun Color.toRgb(): Int = toArgb() and RGB_MASK

private fun Int.opaqueArgb(): Int = this or OPAQUE_ALPHA.toInt()
