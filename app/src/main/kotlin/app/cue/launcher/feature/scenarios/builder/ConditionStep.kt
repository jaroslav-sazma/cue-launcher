package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.designsystem.GLYPH_CHEVRON
import app.cue.launcher.designsystem.GLYPH_DELETE
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.engine.SystemState
import app.cue.launcher.feature.scenarios.conditionPhrase
import app.cue.launcher.feature.scenarios.conditionPhraseText
import app.cue.launcher.feature.scenarios.conditionsText
import app.cue.launcher.feature.scenarios.liveReading
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Note

// The name field is left to the user: this is only what the scenario is called when they write
// nothing, and what the empty field shows as a hint.
@Composable
internal fun nameSuggestion(draft: ScenarioDraft): String = if (draft.fallback) {
    stringResource(R.string.builder_fallback_name)
} else {
    conditionsText(draft.conditions, draft.match)
}

@Composable
internal fun ConditionStep(
    draft: ScenarioDraft,
    state: SystemState,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onFallback: () -> Unit,
    onMatch: (ConditionMatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        if (draft.fallback) {
            SectionLabel(title = stringResource(R.string.builder_section_conditions))
            FallbackChosen(onToggle = onFallback)
            return@Column
        }
        SectionLabel(title = stringResource(R.string.builder_section_conditions))
        if (draft.conditions.isEmpty()) {
            EmptyConditions()
        } else {
            MatchForm(draft = draft, onMatch = onMatch)
            draft.conditions.forEachIndexed { index, condition ->

                if (index > 0) MatchSeparator(draft.match)
                ConditionRowItem(
                    condition = condition,
                    state = state,
                    onEdit = { onEdit(index) },
                    onDelete = { onDelete(index) },
                )
            }
        }
        TonalRow(onClick = onAdd) {
            RowText(
                title = stringResource(R.string.builder_add_condition),
                modifier = Modifier.weight(1f),
            )
            GlyphIcon(name = GLYPH_CHEVRON, tint = LocalContentColor.current)
        }
        if (draft.conditions.isEmpty()) {
            SectionLabel(title = stringResource(R.string.builder_section_or))
            FallbackOffer(onChoose = onFallback)
        }
    }
}

@Composable
private fun ConditionRowItem(
    condition: Condition,
    state: SystemState,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reading = remember(condition, state) { liveReading(condition, state) }
    val title = conditionTypeLabel(conditionTypeOf(condition))
    val summary = conditionPhraseText(conditionPhrase(condition))
    val verdict = liveVerdictShort(reading.verdict)
    TonalRow(onClick = onEdit, modifier = modifier) {
        RowText(
            title = title,
            modifier = Modifier.weight(1f),

            detail = if (summary.equals(title, ignoreCase = true)) {
                verdict
            } else {
                stringResource(R.string.builder_condition_detail, summary, verdict)
            },
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
private fun EmptyConditions(modifier: Modifier = Modifier) {
    TonalRow(onClick = null, modifier = modifier) {
        RowText(
            title = stringResource(R.string.builder_conditions_empty_title),
            modifier = Modifier.weight(1f),
            detail = stringResource(R.string.builder_conditions_empty_detail),
        )
    }
}

@Composable
private fun FallbackOffer(onChoose: () -> Unit, modifier: Modifier = Modifier) {
    TonalRow(onClick = onChoose, modifier = modifier) {
        RowText(
            title = stringResource(R.string.builder_fallback_title),
            modifier = Modifier.weight(1f),
            detail = stringResource(R.string.builder_fallback_detail),
        )
        GlyphIcon(name = GLYPH_CHEVRON, tint = LocalContentColor.current)
    }
}

@Composable
private fun FallbackChosen(onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        TonalRow(onClick = onToggle, selected = true) {
            RowText(
                title = stringResource(R.string.builder_fallback_title),
                modifier = Modifier.weight(1f),
                detail = stringResource(R.string.builder_fallback_detail_long),
            )
        }
        Note(
            text = stringResource(R.string.builder_fallback_note),
            modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
        )
    }
}

@Composable
internal fun MatchSeparator(match: ConditionMatch, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(if (match == ConditionMatch.ANY) R.string.builder_or else R.string.builder_and),
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

// One condition means the same thing either way, so the choice only appears once it can matter.
@Composable
internal fun MatchForm(
    draft: ScenarioDraft,
    onMatch: (ConditionMatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (draft.conditions.size < 2) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        ConnectedChoiceRow(
            options = listOf(
                ConnectedChoice(
                    label = stringResource(R.string.builder_match_all),
                    selected = draft.match == ConditionMatch.ALL,
                    enabled = draft.acceptsMatchAll,
                    onSelect = { onMatch(ConditionMatch.ALL) },
                ),
                ConnectedChoice(
                    label = stringResource(R.string.builder_match_any),
                    selected = draft.match == ConditionMatch.ANY,
                    onSelect = { onMatch(ConditionMatch.ANY) },
                ),
            ),
        )
        if (!draft.acceptsMatchAll) {
            Note(
                text = stringResource(R.string.builder_match_all_blocked),
                modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
            )
        }
    }
}

