package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.cue.launcher.R
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.canPin
import app.cue.launcher.designsystem.GLYPH_CHEVRON
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.scenarios.conditionsText
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.RowSwitch

@Composable
internal fun SummaryStep(
    draft: ScenarioDraft,
    name: String,
    launcherApps: LauncherAppsRepository,
    widgets: WidgetHosting,
    onEditStep: (Int) -> Unit,
    onEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val size = draft.size ?: TileSize.GRID_2X2
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.builder_preview_section))
        TilePreview(
            size = size,
            draft = draft.content,
            scenarioName = name,
            launcherApps = launcherApps,
            widgets = widgets,
        )
        SectionLabel(title = stringResource(R.string.builder_summary_section))
        SummaryRow(
            label = stringResource(R.string.builder_summary_when),
            value = if (draft.fallback) {
                stringResource(R.string.scenarios_no_conditions)
            } else {
                conditionsText(draft.conditions, draft.match)
            },
            onClick = { onEditStep(FIRST_STEP) },
        )
        SummaryRow(
            label = stringResource(R.string.builder_summary_tile),
            value = tileSizeBadge(size),
            onClick = { onEditStep(TYPE_STEP) },
        )
        SummaryRow(
            label = stringResource(R.string.builder_summary_content),
            value = contentSummary(draft.content, size),
            onClick = { onEditStep(CONTENT_STEP) },
        )
        SummaryRow(
            label = stringResource(R.string.builder_summary_priority),
            value = draft.priorityText.trim(),
            onClick = { onEditStep(PRIORITY_STEP) },
        )
        if (canPin(size)) {
            SummaryRow(
                label = stringResource(R.string.builder_summary_position),
                value = pinSummary(draft.pinnedPosition),
                onClick = { onEditStep(PRIORITY_STEP) },
            )
        }
        SectionLabel(title = stringResource(R.string.builder_state_section))
        EnabledRow(enabled = draft.enabled, onChange = onEnabled)
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TonalRow(onClick = onClick, modifier = modifier) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
        GlyphIcon(
            name = GLYPH_CHEVRON,
            size = Tokens.Settings.glyph,
            tint = LocalContentColor.current,
        )
    }
}

@Composable
internal fun EnabledRow(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.builder_state_enabled)
    TonalRow(onClick = { onChange(!enabled) }, modifier = modifier) {
        RowText(
            title = label,
            modifier = Modifier.weight(1f),
            detail = stringResource(R.string.builder_state_enabled_detail),
        )
        RowSwitch(
            checked = enabled,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}
