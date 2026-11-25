package app.cue.launcher.feature.settings.safe

import android.content.Context
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cue.launcher.R
import app.cue.launcher.data.safe.SAFE_LOCK_AFTER_MINUTES
import app.cue.launcher.data.safe.SafeSettings
import app.cue.launcher.designsystem.DetailPaneScaffold
import app.cue.launcher.designsystem.Message
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.rememberFlashState
import app.cue.launcher.feature.settings.SettingsRow
import app.cue.launcher.feature.settings.SettingsSwitchRow
import app.cue.launcher.system.safe.SafeState
import app.cue.launcher.system.safe.SafeVault
import app.cue.launcher.system.safe.message
import kotlin.math.roundToInt

// A provider, not the vault: the graph builds it when the screen first opens.
val LocalSafeVault = staticCompositionLocalOf<() -> SafeVault> { error("No vault provided") }

@Composable
internal fun SafeScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val provide = LocalSafeVault.current
    val vault = remember(provide) { provide() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flash = rememberFlashState()
    val state by remember(vault) { vault.state }.collectAsStateWithLifecycle(initialValue = null)
    val settings by remember(vault) { vault.settings }.collectAsStateWithLifecycle(initialValue = null)
    val actions = remember(vault, context, scope, flash) { SafeActions(vault, context, scope, flash) }
    val choose = rememberFileChoice(actions, flash)
    var forgetAsked by rememberSaveable { mutableStateOf(false) }

    DetailPaneScaffold(
        title = stringResource(R.string.safe_title),
        onBack = onClose,
        modifier = modifier,
        flash = flash,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
        ) {
            val current = state
            val stored = settings
            when {
                current == null || stored == null -> Unit
                current == SafeState.NotConfigured -> SafeIntro(onChoose = choose)
                else -> SafeConfigured(
                    state = current,
                    settings = stored,
                    actions = actions,
                    onChoose = choose,
                    onForget = { forgetAsked = true },
                )
            }
        }
    }

    (state as? SafeState.NeedsPassword)?.let { asked ->
        SafePasswordDialog(state = asked, onSubmit = actions::unlockWithPassword, onDismiss = vault::dismiss)
    }
    if (forgetAsked) {
        SafeForgetDialog(
            onConfirm = {
                forgetAsked = false
                actions.forget()
            },
            onDismiss = { forgetAsked = false },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SafeIntro(onChoose: () -> Unit) {
    Message(text = stringResource(R.string.safe_intro))
    Row(modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding)) {
        Button(onClick = onChoose, shapes = ButtonDefaults.shapes()) {
            Text(text = stringResource(R.string.safe_choose))
        }
    }
    Note(text = stringResource(R.string.safe_drive_note))
}

@Composable
private fun SafeConfigured(
    state: SafeState,
    settings: SafeSettings,
    actions: SafeActions,
    onChoose: () -> Unit,
    onForget: () -> Unit,
) {
    val context = LocalContext.current
    SectionLabel(title = stringResource(R.string.safe_section_file))
    SettingsRow(label = settings.container?.displayName.orEmpty(), detail = safeStateLine(state))
    val writtenAt = (state as? SafeState.Unlocked)?.writtenAt ?: settings.container?.writtenAt
    writtenAt?.let { seconds ->
        SettingsRow(label = stringResource(R.string.safe_written_at), detail = dateTimeText(context, seconds * MILLIS))
    }
    SafeStateActions(state = state, actions = actions, onChoose = onChoose)

    SectionLabel(title = stringResource(R.string.safe_section_lock))
    LockAfter(minutes = settings.lockAfterMinutes, onChange = actions::setLockAfterMinutes)
    Note(text = stringResource(R.string.safe_lock_note))
    SettingsSwitchRow(
        label = stringResource(R.string.safe_open_in_files),
        detail = stringResource(R.string.safe_open_in_files_detail),
        checked = settings.openInFiles,
        onCheckedChange = actions::setOpenInFiles,
    )

    SafeFileActions(enabled = state !is SafeState.Unlocking, onChoose = onChoose, onForget = onForget)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SafeStateActions(state: SafeState, actions: SafeActions, onChoose: () -> Unit) {
    state.message?.takeIf { state is SafeState.ChooseFileAgain || state is SafeState.Failed }?.let {
        Message(text = stringResource(it))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.fieldGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            is SafeState.Unlocking -> LoadingIndicator()
            is SafeState.Unlocked -> {
                Button(onClick = actions::lock, shapes = ButtonDefaults.shapes()) {
                    Text(text = stringResource(R.string.safe_lock))
                }
                OutlinedButton(onClick = actions::showFiles, shapes = ButtonDefaults.shapes()) {
                    Text(text = stringResource(R.string.safe_show_files))
                }
            }
            SafeState.ChooseFileAgain -> Button(onClick = onChoose, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.safe_choose_again_action))
            }
            is SafeState.Failed -> Button(onClick = actions::unlock, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.safe_try_again))
            }
            SafeState.NotConfigured, SafeState.Locked, is SafeState.NeedsPassword ->
                Button(onClick = actions::unlock, shapes = ButtonDefaults.shapes()) {
                    Text(text = stringResource(R.string.safe_unlock))
                }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SafeFileActions(enabled: Boolean, onChoose: () -> Unit, onForget: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.fieldGap),
    ) {
        OutlinedButton(onClick = onChoose, enabled = enabled, shapes = ButtonDefaults.shapes()) {
            Text(text = stringResource(R.string.safe_choose_other))
        }
        OutlinedButton(onClick = onForget, enabled = enabled, shapes = ButtonDefaults.shapes()) {
            Text(text = stringResource(R.string.safe_forget))
        }
    }
    Note(text = stringResource(R.string.safe_forget_note))
}

// The stored value changes only when the drag ends, so the settings file is written once per drag.
@Composable
private fun LockAfter(minutes: Int, onChange: (Int) -> Unit) {
    val slider = rememberSliderState(
        value = minutes.toFloat(),
        trackRange = SAFE_LOCK_AFTER_MINUTES.first.toFloat()..SAFE_LOCK_AFTER_MINUTES.last.toFloat(),
    )
    LaunchedEffect(minutes) { if (!slider.isDragging) slider.value = minutes.toFloat() }
    val dragged = slider.value.roundToInt()
    val label = pluralStringResource(R.plurals.safe_lock_after, dragged, dragged)
    Column(
        modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.textSpacing),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Slider(
            state = slider,
            onValueChange = { slider.value = it },
            onValueChangeFinished = { onChange(slider.value.roundToInt()) },
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun safeStateLine(state: SafeState): String {
    val context = LocalContext.current
    return when (state) {
        is SafeState.Unlocked -> stringResource(R.string.safe_state_unlocked, timeText(context, state.locksAt))
        else -> stringResource(safeStateLineRes(state))
    }
}

@StringRes
internal fun safeStateLineRes(state: SafeState): Int = when (state) {
    SafeState.NotConfigured, SafeState.Locked -> R.string.safe_state_locked
    is SafeState.Unlocking -> state.step.message
    is SafeState.Unlocked -> R.string.safe_state_unlocked
    is SafeState.NeedsPassword -> R.string.safe_state_password
    SafeState.ChooseFileAgain, is SafeState.Failed -> R.string.safe_state_unavailable
}

private fun timeText(context: Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)

private fun dateTimeText(context: Context, millis: Long): String = DateUtils.formatDateTime(
    context,
    millis,
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
)

private const val MILLIS = 1000L
