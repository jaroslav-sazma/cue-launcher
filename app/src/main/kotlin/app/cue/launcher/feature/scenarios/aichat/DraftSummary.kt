package app.cue.launcher.feature.scenarios.aichat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.scenarios.builder.ContentKind
import app.cue.launcher.feature.scenarios.builder.contentKindLabel
import app.cue.launcher.feature.scenarios.builder.launcherWidgetLabel
import app.cue.launcher.feature.scenarios.builder.templateLayoutLabel
import app.cue.launcher.feature.scenarios.builder.tileSizeBadge
import app.cue.launcher.feature.scenarios.conditionsText

@Composable
internal fun DraftSummary(scenario: Scenario) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        shape = RoundedCornerShape(Tokens.Settings.highlightRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(Tokens.Settings.surfacePadding),
            verticalArrangement = Arrangement.spacedBy(Tokens.Settings.cardGap),
        ) {
            Text(text = scenario.name, style = MaterialTheme.typography.titleMediumEmphasized)
            SummaryLine(
                label = stringResource(R.string.builder_summary_when),
                value = if (scenario.isFallback) {
                    stringResource(R.string.scenarios_no_conditions)
                } else {
                    conditionsText(scenario.conditions, scenario.match)
                },
            )
            SummaryLine(label = stringResource(R.string.builder_summary_tile), value = tileSizeBadge(scenario.size))
            SummaryLine(
                label = stringResource(R.string.builder_summary_content),
                value = contentLine(scenario.content, scenario.size),
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun contentLine(content: TileContent, size: TileSize): String = when (content) {
    is TileContent.Template -> templateLine(content)

    is TileContent.AppWidget -> stringResource(
        R.string.builder_summary_value,
        contentKindLabel(ContentKind.APP_WIDGET, size),
        content.packageName,
    )

    is TileContent.LauncherWidget -> launcherWidgetLabel(content.widget)
}

@Composable
private fun templateLine(template: TileContent.Template): String {
    val labels = template.items.mapNotNull { item -> item.label?.takeIf(String::isNotBlank) }
    val layout = templateLayoutLabel(template.layout)
    return when {
        labels.isEmpty() -> layout
        else -> stringResource(R.string.builder_summary_value, layout, labels.joinToString())
    }
}
