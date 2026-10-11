package app.cue.launcher.designsystem

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import java.util.function.Consumer
import kotlin.math.roundToInt

@Composable
fun WallpaperBlurEffect(blurFraction: () -> Float) {
    val window = LocalActivity.current?.window ?: return
    val maxRadiusPx = with(LocalDensity.current) { Tokens.Motion.wallpaperBlur.roundToPx() }
    val blurred = !LocalCueColors.current.reducedTransparency && rememberCrossWindowBlurEnabled()

    DisposableEffect(window, blurred) {

        onDispose { window.setBackgroundBlurRadius(0) }
    }

    val current by rememberUpdatedState(blurFraction)
    LaunchedEffect(window, blurred, maxRadiusPx) {
        if (!blurred) {
            window.setBackgroundBlurRadius(0)
            return@LaunchedEffect
        }

        snapshotFlow { wallpaperBlurRadiusPx(current(), maxRadiusPx) }
            .collect { radius -> window.setBackgroundBlurRadius(radius) }
    }
}

fun wallpaperBlurRadiusPx(fraction: Float, maxRadiusPx: Int): Int {
    if (maxRadiusPx <= 0) return 0
    return (fraction.coerceIn(0f, 1f) * maxRadiusPx).roundToInt()
}

fun drawerBlurFraction(progress: Float): Float {
    val start = Tokens.Motion.WALLPAPER_BLUR_START
    return ((progress - start) / (1f - start)).coerceIn(0f, 1f)
}

@Composable
internal fun rememberCrossWindowBlurEnabled(): Boolean {
    val context = LocalContext.current
    val windowManager = remember(context) { context.getSystemService(WindowManager::class.java) }
    var enabled by remember(windowManager) {
        mutableStateOf(windowManager?.isCrossWindowBlurEnabled == true)
    }
    LifecycleStartEffect(windowManager) {
        val listener = Consumer<Boolean> { value -> enabled = value }
        windowManager?.addCrossWindowBlurEnabledListener(listener)
        onStopOrDispose { windowManager?.removeCrossWindowBlurEnabledListener(listener) }
    }
    return enabled
}
