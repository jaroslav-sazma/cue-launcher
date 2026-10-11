package app.cue.launcher.feature.scenarios

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.ImportEntry
import app.cue.launcher.data.scenario.ImportPlan
import app.cue.launcher.data.scenario.MAX_IMPORTED_SCENARIOS
import app.cue.launcher.data.scenario.RejectedScenario
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Note

// Lazy because the file comes from anywhere: hundreds of rows, each with its actions spelled out,
// must not be composed up front.
@Composable
fun ImportPreview(
    plan: ImportPlan,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        item(key = KEY_HEADER) {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap)) {
                SectionLabel(title = stringResource(R.string.import_title))
                plan.setName?.let { name ->
                    Note(text = stringResource(R.string.import_set_name, name))
                }
            }
        }

        previewSections(plan)

        item(key = KEY_BUTTONS) { PreviewButtons(plan, onConfirm, onCancel) }
    }
}

private fun LazyListScope.previewSections(plan: ImportPlan) {
    if (plan.isEmpty) {
        item(key = KEY_NOTHING) { Note(text = stringResource(R.string.import_nothing)) }
    } else if (plan.entries.isNotEmpty()) {
        sectionLabel(R.string.import_section_added)
        items(items = plan.entries, key = { "added:${it.scenario.id}" }) { entry ->
            ScenarioSummaryRow(entry.scenario)
        }
    }

    plan.config?.let { config -> item(key = KEY_CONFIG) { ImportConfigSection(config) } }

    val collisions = plan.collisions
    if (collisions.isNotEmpty()) {
        sectionLabel(R.string.import_section_collisions)
        items(items = collisions, key = { "collision:${it.scenario.id}" }) { entry ->
            ImportDetailRow(
                title = entry.scenario.name,
                detail = stringResource(R.string.import_collision_note),
            )
        }
    }

    val incomplete = plan.incomplete
    if (incomplete.isNotEmpty()) {
        sectionLabel(R.string.import_section_incomplete)
        items(items = incomplete, key = { "incomplete:${it.scenario.id}" }) { entry -> IncompleteRow(entry) }
        item { Note(text = stringResource(R.string.import_incomplete_note)) }
    }

    if (plan.rejected.isNotEmpty() || plan.skipped > 0) {
        sectionLabel(R.string.import_section_rejected)
        itemsIndexed(items = plan.rejected, key = { index, _ -> "rejected:$index" }) { _, rejected ->
            RejectedRow(rejected)
        }
        if (plan.skipped > 0) {
            item {
                Note(
                    text = pluralStringResource(
                        R.plurals.import_skipped,
                        plan.skipped,
                        plan.skipped,
                        MAX_IMPORTED_SCENARIOS,
                    ),
                )
            }
        }
    }
}

private fun LazyListScope.sectionLabel(@StringRes title: Int) {
    item(key = "label:$title") { SectionLabel(title = stringResource(title)) }
}

@Composable
private fun PreviewButtons(plan: ImportPlan, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        if (!plan.isEmpty) {
            Button(onClick = onConfirm) {

                Text(
                    text = if (plan.hasConfig) {
                        stringResource(R.string.import_confirm_config)
                    } else {
                        pluralStringResource(
                            R.plurals.import_confirm,
                            plan.entries.size,
                            plan.entries.size,
                        )
                    },
                )
            }
        }
        TextButton(onClick = onCancel) {
            val label = if (plan.isEmpty) R.string.import_close else R.string.import_cancel
            Text(text = stringResource(label))
        }
    }
}

@Composable
private fun ScenarioSummaryRow(scenario: Scenario, modifier: Modifier = Modifier) {
    val taps = remember(scenario.content) { tapDisclosuresOf(scenario.content) }
    val extracts = remember(scenario.conditions) { extractDisclosuresOf(scenario) }
    ImportDetailRow(
        title = scenario.name.ifBlank { stringResource(R.string.import_scenario_unnamed) },
        detail = scenario.pinnedPosition?.let { position ->
            stringResource(R.string.scenarios_row_meta_pinned, scenario.size.jsonName, scenario.priority, position + 1)
        } ?: stringResource(R.string.scenarios_row_meta, scenario.size.jsonName, scenario.priority),
        modifier = modifier,
    ) {
        extracts.forEach { condition -> ImportExtractLine(condition) }
        taps.forEach { tap -> ImportTapLines(tap) }
    }
}

@Composable
private fun IncompleteRow(entry: ImportEntry, modifier: Modifier = Modifier) {
    ImportDetailRow(
        title = "${stringResource(R.string.scenarios_incomplete_badge)} ${entry.scenario.name}",
        detail = stringResource(R.string.scenarios_incomplete_hint, entry.placeholders.joinToString()),
        modifier = modifier,
    )
}

@Composable
private fun RejectedRow(rejected: RejectedScenario, modifier: Modifier = Modifier) {
    ImportDetailRow(
        title = rejected.name ?: rejected.id ?: stringResource(R.string.import_scenario_unnamed),

        detail = rejected.defects.map { defect -> defectText(defect) }.joinToString(separator = "\n"),
        modifier = modifier,
    )
}

private const val KEY_HEADER = "header"
private const val KEY_NOTHING = "nothing"
private const val KEY_CONFIG = "config"
private const val KEY_BUTTONS = "buttons"
