package app.cue.launcher.feature.scenarios.builder

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.designsystem.GLYPH_CHEVRON
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.RowSwitch
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Note
import app.cue.launcher.engine.HA_CODE_MAX_LENGTH
import java.text.DecimalFormatSymbols

@Composable
internal fun HaEventForm(
    condition: Condition.HaEvent,
    onChange: (Condition.HaEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    HaCodeFields(code = condition.code, onChange = { onChange(condition.copy(code = it)) }, modifier = modifier)
}

@Composable
internal fun HaCodeFields(code: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = { onChange(it.take(HA_CODE_MAX_LENGTH)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
            label = { Text(text = stringResource(R.string.builder_ha_code)) },
            singleLine = true,
        )
        Note(text = stringResource(R.string.builder_ha_note))
    }
}

@Composable
internal fun NotificationForm(
    condition: Condition.NotificationFrom,
    onPickApp: (() -> Unit)?,
    onChange: (Condition.NotificationFrom) -> Unit,
    access: ExtractAccess,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        WatchedAppRow(packageName = condition.packageName, onPickApp = onPickApp)
        Note(text = stringResource(R.string.builder_notification_package_note))
        ContainsField(value = condition.contains, onChange = { onChange(condition.copy(contains = it)) })
        NotificationExtractSection(condition = condition, access = access, onChange = onChange)
    }
}

@Composable
internal fun WatchedAppRow(
    packageName: String,
    onPickApp: (() -> Unit)?,
    modifier: Modifier = Modifier,
    @StringRes detail: Int = R.string.builder_notification_package,
) {
    TonalRow(onClick = onPickApp, modifier = modifier) {
        RowText(
            title = packageName.ifBlank { stringResource(R.string.builder_notification_pick_app) },
            modifier = Modifier.weight(1f),
            detail = stringResource(detail),
        )
        GlyphIcon(
            name = GLYPH_CHEVRON,
            size = Tokens.Settings.glyph,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
internal fun ContainsField(value: String?, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = onChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        label = { Text(text = stringResource(R.string.builder_notification_contains)) },
        singleLine = true,
    )
}

@Composable
internal fun NotificationEndedForm(
    condition: Condition.NotificationEnded,
    onPickApp: (() -> Unit)?,
    onChange: (Condition.NotificationEnded) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        WatchedAppRow(packageName = condition.packageName, onPickApp = onPickApp)
        val ongoingLabel = stringResource(R.string.builder_notification_ended_ongoing)
        TonalRow(onClick = { onChange(condition.copy(ongoing = !condition.ongoing)) }) {
            RowText(
                title = ongoingLabel,
                modifier = Modifier.weight(1f),
                detail = stringResource(R.string.builder_notification_ended_ongoing_detail),
            )
            RowSwitch(
                checked = condition.ongoing,
                onCheckedChange = { onChange(condition.copy(ongoing = it)) },
                modifier = Modifier.semantics { contentDescription = ongoingLabel },
            )
        }
        MinLastedSection(seconds = condition.minLastedSec, onChange = { onChange(condition.copy(minLastedSec = it)) })
    }
}

@Composable
private fun MinLastedSection(seconds: Int?, onChange: (Int?) -> Unit) {
    val separator = DecimalFormatSymbols.getInstance(LocalConfiguration.current.locales[0]).decimalSeparator
    val current = seconds?.takeIf { it > 0 }
    // An imported value off the presets stays on offer, so opening the form does not change it.
    val choices = (MIN_LASTED_CHOICES + listOfNotNull(current)).distinct().sortedBy { it ?: 0 }
    SectionLabel(title = stringResource(R.string.builder_notification_ended_lasted))
    ConnectedChoiceRow(
        options = choices.map { choice ->
            ConnectedChoice(
                label = choice?.let {
                    stringResource(R.string.builder_notification_ended_lasted_minutes, customMinutesOf(it, separator))
                } ?: stringResource(R.string.builder_notification_ended_lasted_any),
                selected = choice == current,
                onSelect = { onChange(choice) },
            )
        },
    )
    Note(text = stringResource(R.string.builder_notification_ended_lasted_note))
}

private val MIN_LASTED_CHOICES: List<Int?> = listOf(null, 60, 120, 300)

@Composable
internal fun DurationSection(
    condition: Condition,
    ending: EndingDraft,
    permissionTick: Int,
    onPickEndApp: (() -> Unit)?,
    onChange: (EndingDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.builder_duration_section))
        ConnectedChoiceRow(
            options = DurationChoice.entries.map { entry ->
                ConnectedChoice(
                    label = durationChipLabel(entry),
                    selected = entry == ending.choice,
                    onSelect = { onChange(ending.copy(choice = entry)) },
                )
            },
        )
        when (ending.choice) {
            DurationChoice.EVENT -> EndEventSection(
                condition = condition,
                end = ending.end,
                capSec = ending.capSec,
                permissionTick = permissionTick,
                onPickApp = onPickEndApp,
                onEnd = { onChange(ending.copy(end = it)) },
                onCap = { onChange(ending.copy(capSec = it)) },
            )

            DurationChoice.CUSTOM -> {
                OutlinedTextField(
                    value = ending.customMinutes,
                    onValueChange = { onChange(ending.copy(customMinutes = it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Tokens.Settings.surfacePadding),
                    label = { Text(text = stringResource(R.string.builder_duration_custom_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Note(text = stringResource(durationNote(condition)))
            }

            else -> Note(text = stringResource(durationNote(condition)))
        }
    }
}

@StringRes
internal fun durationNote(condition: Condition): Int = when (condition) {
    is Condition.HaEvent -> R.string.builder_duration_note_ha
    is Condition.Alarm -> R.string.builder_duration_note_alarm
    is Condition.PlaceSaved -> R.string.builder_duration_note_place
    else -> R.string.builder_duration_note
}
