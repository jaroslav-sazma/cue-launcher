package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.Message

@Composable
internal fun WizardFooter(
    screen: BuilderState,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            StepBlocker(screen, modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding))
            WizardBottomBar(
                nextLabel = stringResource(
                    if (screen.step == SUMMARY_STEP) R.string.builder_save else R.string.builder_next,
                ),
                nextEnabled = screen.canAdvance,
                onNext = { screen.advance(onSave) },
                onBack = if (screen.canGoBack) screen::back else null,
            )
        }
    }
}

@Composable
private fun StepBlocker(screen: BuilderState, modifier: Modifier = Modifier) {
    when (screen.step) {
        CONTENT_STEP -> {
            val size = screen.draft.size
            val error = contentFormError(screen.draft.content)
            if (size != null && error != null) {
                Message(
                    text = contentFormErrorText(error, screen.draft.content, size),
                    modifier = modifier,
                )
            }
        }

        PRIORITY_STEP -> priorityError(screen.draft.priorityText)?.let { error ->
            Message(text = priorityErrorText(error), modifier = modifier)
        }

        else -> Unit
    }
}
