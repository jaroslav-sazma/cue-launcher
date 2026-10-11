package app.cue.launcher.designsystem

import android.app.LocaleManager
import android.app.WallpaperManager
import android.text.TextUtils
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView

// The picker frames a wide wallpaper at the start edge of its crop and keeps the spare width past
// the end edge, so offset 0 (1 under RTL) is exactly the framed position and the swing can only
// head into that spare. WindowManager drops negative offsets, and a launcher that reports nothing
// inherits whatever offset the previous wallpaper target left behind, hence the report at rest too.
@Composable
fun WallpaperParallaxEffect(progress: () -> Float) {
    val view = LocalView.current
    val context = view.context
    val wallpaperManager = remember(context) { context.getSystemService(WallpaperManager::class.java) }
    val localeManager = remember(context) { context.getSystemService(LocaleManager::class.java) }
    val reducedMotion = rememberReducedMotion()
    val current by rememberUpdatedState(progress)
    LaunchedEffect(view, reducedMotion) {
        // The crop's edge follows the system locale, not a per-app one the launcher may run in.
        val rtl = TextUtils.getLayoutDirectionFromLocale(localeManager?.systemLocales?.get(0)) ==
            View.LAYOUT_DIRECTION_RTL
        snapshotFlow { wallpaperParallaxOffset(if (reducedMotion) 0f else current(), rtl) }
            .collect { offset ->
                val token = view.windowToken ?: return@collect
                wallpaperManager?.setWallpaperOffsets(token, offset, CENTERED)
            }
    }
}

fun wallpaperParallaxOffset(progress: Float, rtl: Boolean): Float {
    val swing = progress.coerceIn(0f, 1f) * Tokens.Motion.WALLPAPER_PARALLAX_SWING
    return if (rtl) 1f - swing else swing
}

private const val CENTERED = 0.5f
