package app.cue.launcher.feature.scenarios.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.ha.HaStateRead
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioRepository
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.designsystem.GLYPH_DELETE
import app.cue.launcher.designsystem.GLYPH_CHEVRON
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.scenarios.ConditionDiagnosis
import app.cue.launcher.feature.scenarios.ContentHealth
import app.cue.launcher.feature.scenarios.ScenarioDiagnosis
import app.cue.launcher.feature.scenarios.builder.MatchForm
import app.cue.launcher.feature.scenarios.builder.MatchSeparator
import app.cue.launcher.feature.scenarios.builder.BuilderState
import app.cue.launcher.feature.scenarios.builder.EnabledRow
import app.cue.launcher.feature.scenarios.builder.PriorityStep
import app.cue.launcher.feature.scenarios.builder.ScenarioDraft
import app.cue.launcher.feature.scenarios.builder.TilePreview
import app.cue.launcher.feature.scenarios.builder.blockingReasonText
import app.cue.launcher.feature.scenarios.builder.contentSummary
import app.cue.launcher.feature.scenarios.builder.liveReadingText
import app.cue.launcher.feature.scenarios.builder.tileSizeBadge
import app.cue.launcher.feature.scenarios.conditionPhrase
import app.cue.launcher.feature.scenarios.conditionPhraseText
import app.cue.launcher.designsystem.relativeTimeText
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Note

@Composable
internal fun PreviewSection(
    screen: BuilderState,
    size: TileSize,
    name: String,
    launcherApps: LauncherAppsRepository,
    widgets: WidgetHosting,
) {
    SectionLabel(title = stringResource(R.string.builder_preview_section))
    TilePreview(
        size = size,
        draft = screen.draft.content,
        scenarioName = name,
        launcherApps = launcherApps,
        widgets = widgets,
        modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding),
    )
}

@Composable
internal fun ConditionsSection(
    draft: ScenarioDraft,
    diagnosis: ScenarioDiagnosis,
    suggestion: String,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: () -> Unit,
    onName: (String) -> Unit,
    onMatch: (ConditionMatch) -> Unit,
) {
    SectionLabel(title = stringResource(R.string.edit_section_when))
    if (draft.conditions.isEmpty()) {
        TonalRow(onClick = null) {
            RowText(
                title = stringResource(R.string.builder_fallback_title),
                modifier = Modifier.weight(1f),
                detail = stringResource(R.string.builder_fallback_detail_long),
            )
        }
    } else {
        MatchForm(draft = draft, onMatch = onMatch)
        diagnosis.conditions.forEach { row ->
            if (row.index > 0) MatchSeparator(draft.match)
            ConditionDiagnosisRow(
                row = row,
                onEdit = { onEdit(row.index) },
                onDelete = { onDelete(row.index) },
            )
        }
    }
    TonalRow(onClick = onAdd) {
        RowText(
            title = stringResource(R.string.builder_add_condition),
            modifier = Modifier.weight(1f),
        )
        GlyphIcon(
            name = GLYPH_CHEVRON,
            size = Tokens.Settings.glyph,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
    SectionLabel(title = stringResource(R.string.builder_section_name))
    OutlinedTextField(
        value = draft.name,
        onValueChange = onName,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        placeholder = { Text(text = suggestion) },
        singleLine = true,
    )
    Note(
        text = stringResource(R.string.builder_name_hint),
        modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
    )
}

@Composable
private fun ConditionDiagnosisRow(
    row: ConditionDiagnosis,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phrase = remember(row.condition) { conditionPhrase(row.condition) }
    TonalRow(onClick = onEdit, modifier = modifier, error = row.blocking) {
        RowText(
            title = conditionPhraseText(phrase),
            modifier = Modifier.weight(1f),
            detail = if (row.blocking) blockingReasonText(row.reading) else liveReadingText(row.reading),
        )
        IconButton(onClick = onDelete) {
            GlyphIcon(
                name = GLYPH_DELETE,
                contentDescription = stringResource(R.string.builder_condition_delete),
            )
        }
    }
}

@Composable
internal fun TileSection(
    draft: ScenarioDraft,
    size: TileSize,
    health: ContentHealth,
    haRead: HaStateRead?,
    onOpen: () -> Unit,
) {
    SectionLabel(title = stringResource(R.string.edit_section_tile))
    TonalRow(onClick = onOpen) {
        RowText(
            title = stringResource(
                R.string.edit_tile_value,
                tileSizeBadge(size),
                contentSummary(draft.content, size),
            ),
            modifier = Modifier.weight(1f),
        )
        GlyphIcon(
            name = GLYPH_CHEVRON,
            size = Tokens.Settings.glyph,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
    TonalRow(onClick = null, error = health != ContentHealth.READY) {
        RowText(
            title = stringResource(R.string.edit_content_loaded),
            modifier = Modifier.weight(1f),
            detail = contentHealthDetail(health, haRead),
        )
    }
}

@Composable
private fun contentHealthDetail(health: ContentHealth, haRead: HaStateRead?): String {
    val text = stringResource(contentHealthText(health))
    if (haRead == null || haRead.atMillis == 0L) return text
    return stringResource(R.string.edit_content_ha, text, relativeTimeText(haRead.atMillis))
}

private fun contentHealthText(health: ContentHealth): Int = when (health) {
    ContentHealth.READY -> R.string.edit_content_ready
    ContentHealth.WIDGET_UNBOUND -> R.string.edit_content_widget_unbound
    ContentHealth.WIDGET_UNSUPPORTED -> R.string.edit_content_widget_unsupported
    ContentHealth.HA_STATE_MISSING -> R.string.edit_content_ha_missing
    ContentHealth.GOOGLE_TV_MISSING -> R.string.edit_content_google_tv_missing
}

@Composable
internal fun PrioritySection(
    screen: BuilderState,
    name: String,
    scenarios: ScenarioRepository,
    outrankedBy: List<Scenario>,
) {
    PriorityStep(
        draft = screen.draft,
        name = name,
        scenarios = scenarios,
        onChange = { screen.draft = screen.draft.copy(priorityText = it) },
        onPin = { screen.draft = screen.draft.copy(pinnedPosition = it) },
        excludeId = screen.id,
    )
    if (outrankedBy.isNotEmpty()) {
        Note(text = stringResource(R.string.edit_outranked, outrankedBy.joinToString { it.name }))
    }
}

@Composable
internal fun StateSection(screen: BuilderState) {
    SectionLabel(title = stringResource(R.string.builder_state_section))
    EnabledRow(
        enabled = screen.draft.enabled,
        onChange = { screen.draft = screen.draft.copy(enabled = it) },
    )
}

@Composable
internal fun EditActionsSection(actions: EditActions, name: String) {
    var confirming by remember { mutableStateOf(false) }

    SectionLabel(title = stringResource(R.string.edit_section_actions))
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap)) {
        TonalRow(onClick = actions.duplicate) {
            RowText(
                title = stringResource(R.string.scenarios_menu_duplicate),
                modifier = Modifier.weight(1f),
            )
        }
        TonalRow(onClick = actions.export) {
            RowText(
                title = stringResource(R.string.scenarios_menu_export),
                modifier = Modifier.weight(1f),
            )
        }
        TonalRow(onClick = { confirming = true }) {
            RowText(
                title = stringResource(R.string.scenarios_menu_delete),
                modifier = Modifier.weight(1f),
                detail = stringResource(R.string.edit_delete_detail),
            )
        }
    }

    if (confirming) {
        DeleteScenarioDialog(
            name = name,
            onConfirm = {
                confirming = false
                actions.delete()
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun DeleteScenarioDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.edit_delete_title, name)) },
        text = { Text(text = stringResource(R.string.edit_delete_detail)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.scenarios_menu_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.edit_delete_cancel))
            }
        },
    )
}

