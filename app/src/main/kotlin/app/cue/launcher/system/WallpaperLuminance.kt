package app.cue.launcher.system

import android.app.Application
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Handler
import android.os.Looper
import app.cue.launcher.designsystem.ContrastEngine
import app.cue.launcher.designsystem.relativeLuminance
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@SingleIn(AppScope::class)
@Inject
class WallpaperLuminance(private val application: Application) {

    private val wallpaperManager = WallpaperManager.getInstance(application)

    fun current(): Double = luminanceOf()

    fun changes(): Flow<Double> = callbackFlow {
        trySend(luminanceOf())
        val listener = WallpaperManager.OnColorsChangedListener { _, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) trySend(luminanceOf())
        }
        wallpaperManager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        awaitClose { wallpaperManager.removeOnColorsChangedListener(listener) }
    }.distinctUntilChanged()

    private fun luminanceOf(): Double {
        val colors = wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?: return FALLBACK_LUMINANCE
        return wallpaperLuminance(
            paletteLuminance = paletteLuminanceOf(colors),
            supportsDarkText = colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0,
        )
    }

    private fun paletteLuminanceOf(colors: WallpaperColors): Double {
        var sum = PRIMARY_WEIGHT * luminanceOf(colors.primaryColor.toArgb())
        var weight = PRIMARY_WEIGHT
        colors.secondaryColor?.let {
            sum += SECONDARY_WEIGHT * luminanceOf(it.toArgb())
            weight += SECONDARY_WEIGHT
        }
        colors.tertiaryColor?.let {
            sum += TERTIARY_WEIGHT * luminanceOf(it.toArgb())
            weight += TERTIARY_WEIGHT
        }
        return sum / weight
    }

    private fun luminanceOf(argb: Int): Double = relativeLuminance(argb and RGB_MASK)

    private companion object {

        const val FALLBACK_LUMINANCE = 0.85
        const val RGB_MASK = 0x00FFFFFF
        const val PRIMARY_WEIGHT = 4.0
        const val SECONDARY_WEIGHT = 2.0
        const val TERTIARY_WEIGHT = 1.0
    }
}

internal fun wallpaperLuminance(paletteLuminance: Double, supportsDarkText: Boolean): Double =
    if (supportsDarkText) {
        paletteLuminance.coerceAtLeast(ContrastEngine.WALLPAPER_LABEL_THRESHOLD)
    } else {
        paletteLuminance.coerceAtMost(ContrastEngine.WALLPAPER_LABEL_THRESHOLD - DARK_MARGIN)
    }

private const val DARK_MARGIN = 0.02
