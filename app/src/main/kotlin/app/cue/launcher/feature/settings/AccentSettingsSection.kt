package app.cue.launcher.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import app.cue.launcher.R
import app.cue.launcher.data.scenario.AccentSource
import app.cue.launcher.data.scenario.accentHexOf
import app.cue.launcher.data.scenario.accentRgbOf
import app.cue.launcher.data.scenario.parseAccentHex
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.data.settings.effectiveAccent
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.scenarios.builder.ConnectedChoice
import app.cue.launcher.feature.scenarios.builder.ConnectedChoiceRow
import kotlinx.coroutines.launch

@Composable
internal fun AccentSettingsSection(
    settings: SettingsRepository,
    stored: LauncherSettings,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val choice = stored.appearance.effectiveAccent
    val custom = stored.appearance.customAccent
    val scheme = MaterialTheme.colorScheme
    val shown = if (choice == AccentSource.TERTIARY) scheme.tertiary else scheme.primary
    val choose = { wanted: AccentSource, rgb: Int? -> scope.launch { settings.setAccent(wanted, rgb) } }

    SettingsSection(title = stringResource(R.string.settings_accent_section), modifier = modifier) {
        Note(text = stringResource(R.string.settings_accent_hint))
        ConnectedChoiceRow(
            options = listOf(
                ConnectedChoice(
                    label = stringResource(R.string.settings_accent_primary),
                    selected = choice == AccentSource.PRIMARY,
                    onSelect = { choose(AccentSource.PRIMARY, null) },
                ),
                ConnectedChoice(
                    label = stringResource(R.string.settings_accent_tertiary),
                    selected = choice == AccentSource.TERTIARY,
                    onSelect = { choose(AccentSource.TERTIARY, null) },
                ),
                ConnectedChoice(
                    label = stringResource(R.string.settings_accent_custom),
                    selected = choice == AccentSource.CUSTOM,
                    onSelect = { choose(AccentSource.CUSTOM, custom ?: accentRgbOf(shown.toArgb())) },
                ),
            ),
        )
        if (choice == AccentSource.CUSTOM && custom != null) {
            CustomAccentField(stored = custom, onValid = { rgb -> choose(AccentSource.CUSTOM, rgb) })
        }
    }
}

// Only a stored colour this field did not send replaces the text: a save landing after the next
// keystroke would otherwise put back what the user has just deleted.
internal data class AccentDraft(val text: String, val sent: Int) {

    fun typed(value: String): AccentDraft = AccentDraft(text = value, sent = parseAccentHex(value) ?: sent)

    fun synced(stored: Int): AccentDraft = if (stored == sent) this else of(stored)

    companion object {
        fun of(stored: Int): AccentDraft = AccentDraft(text = accentHexOf(stored), sent = stored)
    }
}

private val accentDraftSaver: Saver<AccentDraft, Any> = listSaver(
    save = { listOf(it.text, it.sent) },
    restore = { AccentDraft(text = it[0] as String, sent = it[1] as Int) },
)

@Composable
private fun CustomAccentField(stored: Int, onValid: (Int) -> Unit, modifier: Modifier = Modifier) {
    var draft by rememberSaveable(stateSaver = accentDraftSaver) { mutableStateOf(AccentDraft.of(stored)) }
    val parsed = parseAccentHex(draft.text)

    LaunchedEffect(stored) { draft = draft.synced(stored) }

    OutlinedTextField(
        value = draft.text,
        onValueChange = { value ->
            val next = draft.typed(value)
            if (next.sent != draft.sent) onValid(next.sent)
            draft = next
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        label = { Text(text = stringResource(R.string.settings_accent_hex)) },
        placeholder = { Text(text = stringResource(R.string.settings_accent_hex_placeholder)) },
        leadingIcon = { AccentSwatch(rgb = parsed ?: stored) },
        supportingText = if (parsed == null) {
            { Text(text = stringResource(R.string.settings_accent_hex_invalid)) }
        } else {
            null
        },
        isError = parsed == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
    )
}

@Composable
private fun AccentSwatch(rgb: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Tokens.Settings.swatch)
            .background(color = Color(rgb).copy(alpha = 1f), shape = CircleShape)
            .border(
                width = Tokens.Settings.swatchBorder,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            ),
    )
}
