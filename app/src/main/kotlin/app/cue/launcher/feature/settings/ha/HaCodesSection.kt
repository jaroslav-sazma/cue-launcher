package app.cue.launcher.feature.settings.ha

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.ha.HaEventLogRepository
import app.cue.launcher.data.ha.HaLogEntry
import app.cue.launcher.data.scenario.ScenarioRepository
import app.cue.launcher.designsystem.FlashState
import app.cue.launcher.designsystem.Tokens
import kotlinx.coroutines.launch
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.LeadingIconBox
import app.cue.launcher.designsystem.relativeTimeText
import app.cue.launcher.designsystem.supportingInk
import app.cue.launcher.designsystem.supportingStyle

@Composable
internal fun HaCodesSection(
    screen: HaScreenState,
    scenarios: ScenarioRepository,
    log: HaEventLogRepository,
    flash: FlashState,
    modifier: Modifier = Modifier,
) {
    val all by remember(scenarios) { scenarios.observeScenarios() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val lastSeen by remember(log) { log.observeLastSeen() }.collectAsStateWithLifecycle(initialValue = emptyMap())
    val entries by remember(log) { log.observeLog() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val codes = remember(all, lastSeen) { haPairingCodes(all, lastSeen) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.ha_section_codes))
        if (codes.isEmpty()) {
            Note(text = stringResource(R.string.ha_codes_empty))
        } else {
            codes.forEach { code -> HaCodeRow(code = code) }
        }
        OutlinedButton(
            onClick = { screen.logVisible = !screen.logVisible },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
        ) {
            Text(
                text = stringResource(
                    if (screen.logVisible) R.string.ha_log_hide else R.string.ha_log_show,
                ),
            )
        }
        if (screen.logVisible) {
            HaLog(
                entries = entries,
                onClear = {
                    scope.launch {
                        log.clearLog()
                        flash.show(R.string.ha_log_cleared)
                    }
                },
            )
        }
    }
}

@Composable
private fun HaCodeRow(code: HaPairingCode, modifier: Modifier = Modifier) {
    TonalRow(onClick = null, modifier = modifier, error = code.neverSeen) {
        LeadingIconBox(
            glyph = if (code.neverSeen) GLYPH_CODE_MISSING else GLYPH_CODE,
            enabled = !code.neverSeen,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
        ) {

            Text(
                text = code.code,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = haPairingCodeDetail(code),
                style = if (code.neverSeen) MaterialTheme.typography.bodyMedium else supportingStyle,
                color = if (code.neverSeen) Color.Unspecified else supportingInk(),
            )
        }
        if (code.neverSeen) {
            Text(
                text = stringResource(R.string.ha_code_badge),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun HaLog(entries: List<HaLogEntry>, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        if (entries.isEmpty()) {
            Note(text = stringResource(R.string.ha_log_empty))
            return@Column
        }
        entries.forEach { entry -> HaLogRow(entry = entry) }
        TextButton(
            onClick = onClear,
            modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding),
        ) {
            Text(text = stringResource(R.string.ha_log_clear))
        }
    }
}

@Composable
private fun HaLogRow(entry: HaLogEntry, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        Text(
            text = relativeTimeText(entry.receivedAt),
            style = supportingStyle,
            color = supportingInk(),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.code.ifEmpty { stringResource(R.string.ha_log_no_code) },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    entry.rejected || entry.unknown -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = haLogDetail(entry),
                style = supportingStyle,
                color = supportingInk(),
            )
        }
    }
}

@Composable
private fun haLogDetail(entry: HaLogEntry): String {
    val what = when {
        entry.rejected -> stringResource(R.string.ha_log_rejected)
        entry.unknown -> stringResource(R.string.ha_log_unknown)
        entry.end -> stringResource(R.string.ha_log_end, haLogScenarios(entry.matched))
        else -> stringResource(R.string.ha_log_matched, haLogScenarios(entry.matched))
    }
    return if (entry.payload.isEmpty()) what else "$what · ${entry.payload}"
}

@Composable
private fun haLogScenarios(matched: Int): String =
    pluralStringResource(R.plurals.ha_code_scenarios, matched, matched)

private const val GLYPH_CODE = "home-outline"

private const val GLYPH_CODE_MISSING = "alert-circle-outline"
