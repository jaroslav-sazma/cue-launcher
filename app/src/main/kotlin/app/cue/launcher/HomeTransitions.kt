package app.cue.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.cue.launcher.designsystem.MenuBackdropState
import app.cue.launcher.designsystem.WallpaperBlurEffect
import app.cue.launcher.designsystem.drawerBlurFraction
import app.cue.launcher.designsystem.effectsFastSpec
import app.cue.launcher.designsystem.rememberMenuBackdropState
import app.cue.launcher.designsystem.rememberReducedMotion
import app.cue.launcher.designsystem.spatialDefaultSpec

@Composable
internal fun rememberHomeTransitions(drawerOpen: Boolean): HomeTransitions {
    val reducedMotion = rememberReducedMotion()
    val drawerProgress = animateFloatAsState(
        targetValue = if (drawerOpen) 1f else 0f,
        animationSpec = spatialDefaultSpec(reducedMotion),
        label = "drawer.open",
    )
    val menuBackdrop = rememberMenuBackdropState()
    val menuProgress by animateFloatAsState(
        targetValue = if (menuBackdrop.anchor != null) 1f else 0f,
        animationSpec = effectsFastSpec(),
        label = "menu.backdrop.wallpaper",
    )
    // The panel is deliberately absent from the window blur. Its glass is a shaped wallpaper blur
    // region that travels with the panel itself, and blurring the whole window underneath it would
    // stack a second, unshaped blur on the same pixels.
    WallpaperBlurEffect {
        maxOf(drawerBlurFraction(drawerProgress.value), menuProgress)
    }
    // Held rather than rebuilt: callers key derived state off this, and a fresh instance per
    // composition would throw that away every time anything above it recomposed.
    return remember(reducedMotion, drawerProgress, menuBackdrop) {
        HomeTransitions(
            reducedMotion = reducedMotion,
            drawerProgress = drawerProgress,
            menuBackdrop = menuBackdrop,
        )
    }
}

@Stable
internal class HomeTransitions(
    val reducedMotion: Boolean,
    private val drawerProgress: State<Float>,
    val menuBackdrop: MenuBackdropState,
) {

    // A function and not a value: the drawer travels every frame, and read in composition that
    // would recompose the whole launcher underneath it once per frame instead of redrawing it.
    fun drawerProgress(): Float = drawerProgress.value
}
