package app.cue.launcher.feature.scenarios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.supportingInk
import app.cue.launcher.designsystem.supportingStyle
import app.cue.launcher.feature.scenarios.builder.actionSummary
import app.cue.launcher.system.deepLinkTargetOf
import app.cue.launcher.system.unavailableSystemActions

@Composable
internal fun ImportDetailRow(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        detail?.let {
            Text(
                text = it,
                style = supportingStyle,
                color = supportingInk(),
            )
        }
        extra()
    }
}

@Composable
internal fun ImportTapLines(tap: TapDisclosure) {
    val context = LocalContext.current
    val unavailable = remember(tap, context) {
        if (tap.actions.none { it is TapAction.SystemAction }) emptySet() else unavailableSystemActions(context)
    }
    tap.actions.forEach { action ->
        when (action) {
            is TapAction.DeepLink -> ImportLinkLines(action.uri)
            is TapAction.HaAction -> ImportHaLine(action)
            else -> ImportSupportingLine(actionSummary(action))
        }
    }
    if (tap.actions.any { it is TapAction.SystemAction && it.action in unavailable }) {
        ImportWarningLine(stringResource(R.string.action_google_tv_missing))
    }
    if (tap.unconfirmed) ImportWarningLine(stringResource(R.string.import_runs_unconfirmed))
}

@Composable
internal fun ImportExtractLine(condition: Condition.NotificationFrom) {
    val rules = condition.extract.map { extractRuleText(it) }
    if (rules.isEmpty()) return
    ImportSupportingLine(
        text = stringResource(R.string.import_reads_notification, condition.packageName, joinedSummary(rules)),
        maxLines = Int.MAX_VALUE,
    )
}

@Composable
internal fun ImportSupportingLine(text: String, maxLines: Int = ACTION_MAX_LINES) {
    Text(
        text = text,
        style = supportingStyle,
        color = supportingInk(),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ImportWarningLine(text: String) {
    Text(
        text = text,
        style = supportingStyle,
        color = MaterialTheme.colorScheme.error,
    )
}

// Never the item's own label: a file can call an intent: link "Weather", and the target is the
// whole point of showing it. The target line is never cut; only the raw link under it is.
@Composable
private fun ImportLinkLines(uri: String) {
    val ownPackage = LocalContext.current.packageName
    val disclosure = remember(uri, ownPackage) { linkDisclosureOf(deepLinkTargetOf(uri, ownPackage)) }
    when (disclosure) {
        LinkDisclosure.Invalid -> ImportWarningLine(stringResource(R.string.import_link_invalid))
        is LinkDisclosure.Parsed -> {
            val facts = disclosure.facts.map { linkFactText(it) }
            if (facts.isNotEmpty()) {
                ImportSupportingLine(
                    text = stringResource(R.string.builder_action_deeplink, joinedSummary(facts)),
                    maxLines = Int.MAX_VALUE,
                )
            }
            if (disclosure.refused) ImportWarningLine(stringResource(R.string.import_link_refused))
        }
    }
    ImportSupportingLine(stringResource(R.string.builder_action_deeplink, uri))
}

// Service data is already cut in haDataTextOf after the targets; cutting the line again would hide
// exactly the targets that a long entity list pushes down.
@Composable
private fun ImportHaLine(action: TapAction.HaAction) {
    ImportSupportingLine(
        text = stringResource(
            R.string.builder_action_ha,
            joinedSummary(listOfNotNull(action.service, haTargetOf(action), haDataTextOf(action))),
        ),
        maxLines = Int.MAX_VALUE,
    )
}

@Composable
private fun joinedSummary(parts: List<String>): String =
    parts.reduce { joined, part -> stringResource(R.string.builder_summary_value, joined, part) }

@Composable
private fun linkFactText(fact: LinkFact): String = stringResource(
    when (fact.part) {
        LinkPart.APP -> R.string.import_link_app
        LinkPart.ACTION -> R.string.import_link_action
        LinkPart.OPENS -> R.string.import_link_opens
        LinkPart.FALLBACK -> R.string.import_link_fallback
    },
    fact.value,
)

private const val ACTION_MAX_LINES = 4
