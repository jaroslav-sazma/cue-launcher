package app.cue.launcher.feature.scenarios

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioFormat
import app.cue.launcher.data.scenario.portableScenarios
import app.cue.launcher.data.settings.BackupSummary
import app.cue.launcher.designsystem.ExperimentalLabel
import app.cue.launcher.designsystem.GLYPH_CHEVRON
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.system.ConfigBackup
import app.cue.launcher.system.DocumentStore
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.LeadingIconBox
import app.cue.launcher.designsystem.Message

@Composable
internal fun BackupActions(
    scenarios: List<Scenario>,
    lastBackup: BackupSummary?,
    requirement: JitRequirement?,
    @StringRes denied: Int?,
    onImport: () -> Unit,
    onExportConfig: () -> Unit,
    onExportSet: () -> Unit,
    onResolve: (JitRequirement) -> Unit,
    modifier: Modifier = Modifier,
    onPasteJson: (() -> Unit)? = null,
) {
    // Scrolls on its own: the tab cannot, since the import preview below it is a lazy list.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Tokens.Settings.navBarReserve),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.backup_export_title))
        BackupRow(
            glyph = GLYPH_CONFIG,
            title = stringResource(R.string.backup_export_config),
            detail = stringResource(R.string.backup_export_config_detail),
            onClick = onExportConfig,
        )
        val hasScenarios = scenarios.isNotEmpty()
        BackupRow(
            glyph = GLYPH_SET,
            title = stringResource(R.string.scenarios_export),
            detail = stringResource(
                if (hasScenarios) R.string.backup_export_set_detail else R.string.scenarios_export_empty,
            ),
            onClick = onExportSet.takeIf { hasScenarios },
        )
        SectionLabel(title = stringResource(R.string.backup_restore_title))
        BackupRow(
            glyph = GLYPH_IMPORT,
            title = stringResource(R.string.scenarios_import),
            detail = stringResource(R.string.backup_import_detail),
            onClick = onImport,
        )
        onPasteJson?.let { paste ->
            BackupRow(
                glyph = GLYPH_PASTE,
                title = stringResource(R.string.ai_chat_choice_paste),
                detail = stringResource(R.string.ai_chat_choice_paste_detail),
                onClick = paste,
                badge = { ExperimentalLabel() },
            )
        }
        Note(text = stringResource(R.string.scenarios_secrets_note))
        lastBackup?.let { summary -> LastBackupRow(summary) }
        denied?.let { Message(text = stringResource(it)) }
        requirement?.let { missing ->
            RequirementRequest(requirement = missing, onResolve = { onResolve(missing) })
        }
    }
}

// Each choice is one row with its own icon and a line saying what ends up in the file, because the
// titles alone ("the whole configuration" against "the set") do not tell them apart.
@Composable
private fun BackupRow(
    glyph: String,
    title: String,
    detail: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
) {
    TonalRow(onClick = onClick, modifier = modifier) {
        LeadingIconBox(glyph = glyph, enabled = onClick != null)
        RowText(title = title, modifier = Modifier.weight(1f), detail = detail, badge = badge)
        if (onClick != null) {
            GlyphIcon(
                name = GLYPH_CHEVRON,
                size = Tokens.Settings.glyph,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun LastBackupRow(summary: BackupSummary, modifier: Modifier = Modifier) {
    val count = pluralStringResource(R.plurals.backup_last_scenarios, summary.scenarios, summary.scenarios)
    Note(
        text = stringResource(R.string.backup_last, summary.name, count, kilobytes(summary.bytes)),
        modifier = modifier.padding(horizontal = Tokens.Settings.screenPadding),
    )
}

internal fun kilobytes(bytes: Int): Int = ((bytes + BYTES_PER_KB - 1) / BYTES_PER_KB).coerceAtLeast(1)

private const val BYTES_PER_KB = 1024

private const val GLYPH_CONFIG = "archive-arrow-down-outline"

private const val GLYPH_SET = "file-export-outline"

private const val GLYPH_IMPORT = "file-import-outline"

private const val GLYPH_PASTE = "content-paste"

internal suspend fun exportConfig(
    context: Context,
    uri: Uri,
    backup: ConfigBackup,
    scenarios: List<Scenario>,
    fallbackName: String,
): BackupWrite {
    val portable = portableScenarios(scenarios)
    val text = ScenarioFormat.encodeConfig(portable, backup.read())
    if (!DocumentStore.writeText(context, uri, text)) {
        return BackupWrite(context.getString(R.string.scenarios_export_failed), failed = true)
    }
    val bytes = text.toByteArray(Charsets.UTF_8).size
    return BackupWrite(
        message = context.getString(R.string.backup_export_config_done),
        summary = BackupSummary(
            name = DocumentStore.displayName(context, uri) ?: fallbackName,
            scenarios = portable.size,
            bytes = bytes,
            at = System.currentTimeMillis(),
        ),
    )
}

internal suspend fun exportSet(
    context: Context,
    uri: Uri,
    setName: String,
    scenarios: List<Scenario>,
): BackupWrite {
    if (scenarios.isEmpty()) return BackupWrite(context.getString(R.string.scenarios_export_empty))
    val text = ScenarioFormat.encodeSet(setName, portableScenarios(scenarios))
    val saved = DocumentStore.writeText(context, uri, text)
    return BackupWrite(
        context.getString(if (saved) R.string.scenarios_export_done else R.string.scenarios_export_failed),
        failed = !saved,
    )
}

internal data class BackupWrite(
    val message: String,
    val summary: BackupSummary? = null,
    val failed: Boolean = false,
)
