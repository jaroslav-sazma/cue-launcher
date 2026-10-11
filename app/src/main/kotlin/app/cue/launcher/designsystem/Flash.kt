package app.cue.launcher.designsystem

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Immutable
data class Flash(
    val message: String,
    val error: Boolean = false,
    val action: String? = null,
) {
    internal val brief: Boolean get() = '\n' !in message && message.length <= SHORT_FLASH_CHARS
}

@Stable
class FlashState internal constructor(
    internal val host: SnackbarHostState,
    private val scope: CoroutineScope,
    private val resources: Resources,
) {

    private var shown: Job? = null

    fun show(@StringRes message: Int, error: Boolean = false) = show(Flash(resources.getString(message), error))

    // A newer flash replaces the one on screen instead of queueing behind it: an answer that turns up
    // seconds late reads as the answer to whatever was tapped last. onDone is skipped for a replaced one.
    fun show(flash: Flash, onDone: (acted: Boolean) -> Unit = {}) {
        shown?.cancel()
        shown = scope.launch {
            onDone(host.showSnackbar(FlashVisuals(flash)) == SnackbarResult.ActionPerformed)
        }
    }

    fun dismiss() {
        shown?.cancel()
    }
}

@Composable
fun rememberFlashState(): FlashState {
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    return remember(scope, resources) { FlashState(SnackbarHostState(), scope, resources) }
}

@Composable
fun FlashHost(state: FlashState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = state.host, modifier = modifier) { data ->
        val flash = (data.visuals as FlashVisuals).flash
        val error = flash.error
        val scheme = MaterialTheme.colorScheme
        // No modifier: the snackbar already keeps its own 12 dp off the edges and takes the full width
        // by itself, and a fillMaxWidth on top makes the one-row layout, which measures the action
        // with the snackbar's own constraints, stretch the button over the text.
        Snackbar(
            snackbarData = data,
            actionOnNewLine = !flash.brief,
            containerColor = if (error) scheme.errorContainer else SnackbarDefaults.color,
            contentColor = if (error) scheme.onErrorContainer else SnackbarDefaults.contentColor,
            actionColor = if (error) scheme.error else SnackbarDefaults.actionColor,
            dismissActionContentColor = if (error) {
                scheme.onErrorContainer
            } else {
                SnackbarDefaults.dismissActionContentColor
            },
        )
    }
}

// A failure stays up longer and can be closed by hand, and so does anything too long to read in
// the short window: the short one is for a line like "Saved.", not for an instruction.
private class FlashVisuals(val flash: Flash) : SnackbarVisuals {

    override val message: String get() = flash.message

    override val actionLabel: String? get() = flash.action

    override val withDismissAction: Boolean get() = flash.error || !flash.brief

    override val duration: SnackbarDuration
        get() = when {
            flash.error || flash.action != null || !flash.brief -> SnackbarDuration.Long
            else -> SnackbarDuration.Short
        }
}

private const val SHORT_FLASH_CHARS = 60
