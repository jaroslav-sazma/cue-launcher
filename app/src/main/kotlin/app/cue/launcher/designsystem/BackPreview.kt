package app.cue.launcher.designsystem

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Stable
class BackPreview internal constructor(private val state: Animatable<Float, AnimationVector1D>) {

    val travel: Float get() = state.value
}

fun backPreviewTravel(progress: Float): Float {
    val raw = progress.coerceIn(0f, 1f)
    return 1f - (1f - raw) * (1f - raw)
}

@Composable
fun rememberBackPreview(
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    onStart: () -> Unit = {},
    onCancel: () -> Unit = {},
    spec: FiniteAnimationSpec<Float> = spatialDefaultSpec(rememberReducedMotion()),
): BackPreview {
    val travel = remember { Animatable(0f) }
    val preview = remember(travel) { BackPreview(travel) }
    val dismiss by rememberUpdatedState(onDismiss)
    val start by rememberUpdatedState(onStart)
    val cancel by rememberUpdatedState(onCancel)

    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled) { events ->
        try {
            start()
            events.collect { event -> travel.snapTo(backPreviewTravel(event.progress)) }

            dismiss()
        } catch (cancellation: CancellationException) {
            cancel()
            scope.launch { travel.animateTo(0f, spec) }
            throw cancellation
        }
    }
    return preview
}

fun Modifier.backPreview(preview: BackPreview, reducedMotion: Boolean): Modifier =
    if (reducedMotion) {
        this
    } else {
        graphicsLayer {
            val scale = lerp(1f, Tokens.Motion.BACK_PREVIEW_SCALE, preview.travel.coerceIn(0f, 1f))
            scaleX = scale
            scaleY = scale
        }
    }
