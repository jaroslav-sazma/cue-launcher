package app.cue.launcher.feature.scenarios

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.ExtractKind
import app.cue.launcher.data.scenario.ExtractRule

@Composable
fun extractRuleText(rule: ExtractRule): String {
    val anchor = rule.anchor
    val marker = rule.marker.orEmpty()
    val kind = stringResource(extractKindLabel(rule.kind))
    return when {
        rule.kind == ExtractKind.UNTIL && anchor == null ->
            stringResource(R.string.extract_rule_until, rule.name, marker)
        rule.kind == ExtractKind.UNTIL ->
            stringResource(R.string.extract_rule_until_after, rule.name, anchor.orEmpty(), marker)
        rule.kind == ExtractKind.LINE && anchor != null ->
            stringResource(R.string.extract_rule_line_after, rule.name, anchor)
        rule.kind == ExtractKind.AMOUNT && anchor == null ->
            stringResource(R.string.extract_rule_first_amount, rule.name)
        anchor == null -> stringResource(R.string.extract_rule_first, rule.name, kind)
        else -> stringResource(R.string.extract_rule_after, rule.name, kind, anchor)
    }
}

@StringRes
fun extractKindLabel(kind: ExtractKind): Int = when (kind) {
    ExtractKind.NUMBER -> R.string.extract_kind_number
    ExtractKind.CODE -> R.string.extract_kind_code
    ExtractKind.AMOUNT -> R.string.extract_kind_amount
    ExtractKind.TIME -> R.string.extract_kind_time
    ExtractKind.DATE -> R.string.extract_kind_date
    ExtractKind.WORD -> R.string.extract_kind_word
    ExtractKind.LINE -> R.string.extract_kind_line
    ExtractKind.UNTIL -> R.string.extract_kind_until
}
