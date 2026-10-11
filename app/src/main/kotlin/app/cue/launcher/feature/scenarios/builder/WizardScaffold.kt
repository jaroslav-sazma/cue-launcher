package app.cue.launcher.feature.scenarios.builder

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cue.launcher.R
import app.cue.launcher.designsystem.GLYPH_BACK
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.MediumFilledButton
import app.cue.launcher.designsystem.MediumOutlinedButton

const val WIZARD_STEPS = SUMMARY_STEP

@StringRes
internal fun stepLabel(step: Int): Int = when (step) {
    FIRST_STEP -> R.string.builder_step1_label
    TYPE_STEP -> R.string.builder_step2_label
    CONTENT_STEP -> R.string.builder_step3_label
    PRIORITY_STEP -> R.string.builder_step4_label
    else -> R.string.builder_step5_label
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WizardTopBar(
    title: String,
    step: Int,
    closes: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBack) {

                GlyphIcon(
                    name = if (closes) GLYPH_CLOSE else GLYPH_BACK,
                    size = Tokens.Settings.glyph,
                    tint = LocalContentColor.current,
                    contentDescription = stringResource(R.string.builder_back),
                )
            }
        },
        actions = {
            Text(
                text = stringResource(R.string.builder_step_badge, step, WIZARD_STEPS),
                modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
internal fun WizardProgress(step: Int, label: String, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = step.toFloat() / WIZARD_STEPS,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "wizard-progress",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
        )
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = Tokens.Settings.screenPadding,
                vertical = Tokens.Settings.textSpacing,
            ),
            style = MaterialTheme.typography.headlineSmallEmphasized,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WizardBottomBar(
    nextLabel: String,
    nextEnabled: Boolean,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier
            .padding(FloatingToolbarDefaults.ScreenOffset)
            .fillMaxWidth(),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap)) {
            onBack?.let {
                MediumOutlinedButton(
                    label = stringResource(R.string.builder_back),
                    onClick = it,
                    modifier = Modifier.width(Tokens.Builder.backButtonWidth),
                )
            }
            MediumFilledButton(
                label = nextLabel,
                onClick = onNext,
                modifier = Modifier.weight(1f),
                enabled = nextEnabled,
            )
        }
    }
}

@Composable
internal fun WizardDiscardDialog(onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.builder_leave_title)) },
        text = { Text(text = stringResource(R.string.builder_leave_detail)) },
        confirmButton = {
            // Dismissed first: the dialog is its own window and would stay up while the wizard animates away.
            TextButton(
                onClick = {
                    onDismiss()
                    onDiscard()
                },
            ) {
                Text(text = stringResource(R.string.builder_leave_discard))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.builder_leave_stay)) }
        },
    )
}

private const val GLYPH_CLOSE = "close"

