package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.OutlinedToggleButtonDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import app.cue.launcher.R
import app.cue.launcher.data.scenario.ExtractKind
import app.cue.launcher.data.scenario.ExtractMatch
import app.cue.launcher.data.scenario.ExtractProblem
import app.cue.launcher.data.scenario.ExtractRule
import app.cue.launcher.designsystem.GLYPH_CHECK
import app.cue.launcher.designsystem.GLYPH_DELETE
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.LocalSupportingGround
import app.cue.launcher.designsystem.Message
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.engine.NOTIFICATION_VALUE_NAMESPACE
import app.cue.launcher.feature.scenarios.extractKindLabel

@Composable
internal fun SampleTokenFlow(
    sample: String,
    matches: Map<String, ExtractMatch>,
    full: Boolean,
    onPull: (SampleToken) -> Unit,
    onRelease: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = remember(sample) { sampleTokens(sample) }
    val lines = remember(tokens) { tokens.take(MAX_SHOWN_TOKENS).groupBy(SampleToken::line).toList() }
    // The token buttons are shorter than their touch target, which already keeps the rows apart.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
    ) {
        lines.forEach { (line, lineTokens) ->
            key(line) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Scenarios.chipGap),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    lineTokens.forEach { token ->
                        val starting = rulesStartingIn(token, matches)
                        val pull = tokenPull(token, matches)
                        SampleTokenButton(
                            sample = sample,
                            token = token,
                            pull = pull,
                            pulledAs = starting,
                            enabled = pull == TokenPull.PULLED || !full,
                            onPull = { onPull(token) },
                            onRelease = { onRelease(starting) },
                        )
                    }
                }
            }
        }
        if (tokens.size > MAX_SHOWN_TOKENS) {
            Note(text = stringResource(R.string.builder_extract_tokens_capped), modifier = Modifier)
        }
        if (full) Note(text = stringResource(R.string.builder_extract_full), modifier = Modifier)
    }
}

// Checked means a rule's value starts at this token, and unchecking it drops that rule again. A
// token further inside such a value is tinted but stays unchecked, and tapping it pulls its own.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SampleTokenButton(
    sample: String,
    token: SampleToken,
    pull: TokenPull,
    pulledAs: Set<String>,
    enabled: Boolean,
    onPull: () -> Unit,
    onRelease: () -> Unit,
) {
    val label = sample.substring(token.labelStart, token.labelEnd)
    if (token.shape == null) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val value = sample.substring(token.start, token.end)
    val pulled = pull == TokenPull.PULLED
    val state = when (pull) {
        TokenPull.PULLED -> stringResource(R.string.builder_extract_token_pulled, pulledAs.joinToString())
        TokenPull.INSIDE -> stringResource(R.string.builder_extract_token_inside)
        TokenPull.FREE -> stringResource(R.string.builder_extract_token_free)
    }
    val action = stringResource(
        if (pulled) R.string.builder_extract_token_remove_action else R.string.builder_extract_token_pull_action,
    )
    val toggle = { if (pulled) onRelease() else onPull() }
    val scheme = MaterialTheme.colorScheme
    OutlinedToggleButton(
        checked = pulled,
        onCheckedChange = { toggle() },
        modifier = Modifier.semantics {
            contentDescription = value
            stateDescription = state
            onClick(label = action) {
                toggle()
                true
            }
        },
        buttonSize = ToggleButtonSize.ExtraSmall,
        enabled = enabled,
        colors = if (pull == TokenPull.INSIDE) {
            OutlinedToggleButtonDefaults.colors(
                containerColor = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
            )
        } else {
            OutlinedToggleButtonDefaults.colors(
                checkedContainerColor = scheme.primary,
                checkedContentColor = scheme.onPrimary,
            )
        },
    ) {
        Text(text = label)
    }
}

@Composable
internal fun ExtractRuleCard(
    rule: ExtractRule,
    problem: ExtractProblem?,
    reading: RuleReading?,
    onChange: (ExtractRule) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ground = MaterialTheme.colorScheme.surfaceContainer
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        shape = RoundedCornerShape(Tokens.Settings.highlightRadius),
        color = ground,
    ) {
        CompositionLocalProvider(LocalSupportingGround provides ground) {
            Column(
                modifier = Modifier.padding(Tokens.Settings.surfacePadding),
                verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
            ) {
                ExtractRuleHeader(rule = rule, reading = reading, onRemove = onRemove)
                ExtractRuleFields(rule = rule, problem = problem, onChange = onChange)
                problem?.let { Message(text = conditionFormErrorText(ConditionFormError.of(it)), modifier = Modifier) }
            }
        }
    }
}

@Composable
private fun ExtractRuleHeader(rule: ExtractRule, reading: RuleReading?, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
        ) {
            Text(
                text = "{$NOTIFICATION_VALUE_NAMESPACE:${rule.name}}",
                style = MaterialTheme.typography.titleSmall,
            )
            reading?.let { RuleReadingLines(it) }
        }
        IconButton(onClick = onRemove) {
            GlyphIcon(
                name = GLYPH_DELETE,
                tint = LocalContentColor.current,
                contentDescription = stringResource(R.string.builder_extract_remove, rule.name),
            )
        }
    }
}

@Composable
private fun RuleReadingLines(reading: RuleReading) {
    val scheme = MaterialTheme.colorScheme
    val value = reading.value
    Text(
        text = if (value == null) {
            stringResource(R.string.builder_extract_not_found)
        } else {
            stringResource(R.string.builder_extract_result, value)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (value == null) scheme.error else scheme.primary,
    )
    reading.hits?.let { hits ->
        Note(
            text = pluralStringResource(R.plurals.builder_extract_matches, reading.of, hits, reading.of),
            modifier = Modifier,
        )
    }
}

@Composable
private fun ExtractRuleFields(rule: ExtractRule, problem: ExtractProblem?, onChange: (ExtractRule) -> Unit) {
    OutlinedTextField(
        value = rule.after.orEmpty(),
        onValueChange = { onChange(rule.copy(after = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = stringResource(R.string.builder_extract_after)) },
        isError = problem == ExtractProblem.ANCHOR_TOO_LONG,
        singleLine = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.fieldGap)) {
        ExtractKindField(
            kind = rule.kind,
            onKind = { onChange(rule.copy(kind = it)) },
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = rule.name,
            onValueChange = { onChange(rule.copy(name = it.lowercase())) },
            modifier = Modifier.weight(1f),
            label = { Text(text = stringResource(R.string.builder_extract_name)) },
            isError = problem in NAME_PROBLEMS,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            singleLine = true,
        )
    }
    if (rule.kind == ExtractKind.UNTIL) {
        OutlinedTextField(
            value = rule.until.orEmpty(),
            onValueChange = { onChange(rule.copy(until = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.builder_extract_until)) },
            isError = problem == ExtractProblem.MISSING_UNTIL || problem == ExtractProblem.MARKER_TOO_LONG,
            singleLine = true,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExtractKindField(kind: ExtractKind, onKind: (ExtractKind) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = stringResource(extractKindLabel(kind)),
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            singleLine = true,
            label = { Text(text = stringResource(R.string.builder_extract_kind)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
        )
        ExposedDropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            matchAnchorWidth = false,
            shape = MenuDefaults.standaloneGroupShape,
            containerColor = MenuDefaults.groupStandardContainerColor,
        ) {
            ExtractKind.entries.forEachIndexed { index, entry ->
                SelectableDropdownMenuItem(
                    selected = entry == kind,
                    onClick = {
                        onKind(entry)
                        open = false
                    },
                    text = { Text(text = stringResource(extractKindLabel(entry))) },
                    shapes = MenuDefaults.itemShape(index, ExtractKind.entries.size),
                    selectedLeadingIcon = {
                        GlyphIcon(
                            name = GLYPH_CHECK,
                            size = MenuDefaults.LeadingIconSize,
                            tint = LocalContentColor.current,
                        )
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

// A notification of a few thousand characters would otherwise lay out a button per word.
private const val MAX_SHOWN_TOKENS = 400

private val NAME_PROBLEMS = setOf(ExtractProblem.NAME, ExtractProblem.RESERVED_NAME, ExtractProblem.DUPLICATE_NAME)
