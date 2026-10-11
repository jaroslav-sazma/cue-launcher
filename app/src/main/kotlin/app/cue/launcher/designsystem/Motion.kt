package app.cue.launcher.designsystem

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

fun reducedMotionAt(animatorDurationScale: Float): Boolean = animatorDurationScale <= 0f

@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        reducedMotionAt(
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> spatialDefaultSpec(reducedMotion: Boolean): FiniteAnimationSpec<T> =
    if (reducedMotion) {
        MaterialTheme.motionScheme.defaultEffectsSpec()
    } else {
        MaterialTheme.motionScheme.defaultSpatialSpec()
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> spatialFastSpec(reducedMotion: Boolean): FiniteAnimationSpec<T> =
    if (reducedMotion) {
        MaterialTheme.motionScheme.fastEffectsSpec()
    } else {
        MaterialTheme.motionScheme.fastSpatialSpec()
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> spatialSlowSpec(reducedMotion: Boolean): FiniteAnimationSpec<T> =
    if (reducedMotion) {
        MaterialTheme.motionScheme.defaultEffectsSpec()
    } else {
        MaterialTheme.motionScheme.slowSpatialSpec()
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun effectsFastSpec(): FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.fastEffectsSpec()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun effectsSlowSpec(): FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.slowEffectsSpec()
