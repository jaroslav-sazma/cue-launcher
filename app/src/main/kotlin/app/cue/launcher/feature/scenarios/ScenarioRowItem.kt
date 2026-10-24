package app.cue.launcher.feature.scenarios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.cue.launcher.R
import app.cue.launcher.data.scenario.ActionApp
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.placeholderKeysIn
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.home.templateGlyphName
import app.cue.launcher.feature.home.templateIconApp
import app.cue.launcher.feature.home.templateItem
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.designsystem.RowSwitch
import app.cue.launcher.designsystem.rowContentPadding
import app.cue.launcher.designsystem.selectedSupportingInk
import app.cue.launcher.designsystem.supportingInk
import app.cue.launcher.designsystem.supportingStyle

@Composable
internal fun ScenarioRow(
    scenario: Scenario,
    actions: ScenarioActions,
    launcherApps: LauncherAppsRepository,
    drag: ScenarioDrag,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val item = remember(scenario) { scenario.templateItem() }
    val glyph = remember(item) { item?.let(::templateGlyphName) }

    val iconApp = remember(item) { item?.let(::templateIconApp) }
    val toggleLabel = stringResource(R.string.scenarios_enabled_toggle)
    val dragged = drag.state.isDragged(drag.key, drag.index)
    val move = if (dragged) priorityMove(drag.group, drag.index, drag.state.target) else null

    val toned = highlighted || dragged
    val scheme = MaterialTheme.colorScheme
    ListItem(
        onClick = { actions.edit(scenario) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.rowMargin)

            .priorityDrag(drag, actions)
            .clip(RoundedCornerShape(Tokens.Settings.highlightRadius))
            .heightIn(min = Tokens.Scenarios.rowHeight)
            .alpha(if (scenario.enabled) 1f else Tokens.Scenarios.DISABLED_ALPHA),
        supportingContent = { ScenarioRowSummary(scenario = scenario, move = move) },
        leadingContent = {
            ScenarioLeadingIcon(
                glyph = glyph,
                iconApp = iconApp,
                launcherApps = launcherApps,
                toned = toned,
            )
        },
        trailingContent = {
            ScenarioRowTrailing(
                scenario = scenario,
                previewPriority = move?.priority,
                actions = actions,
                toggleLabel = toggleLabel,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = if (toned) scheme.secondaryContainer else Color.Transparent,
            headlineColor = if (toned) scheme.onSecondaryContainer else scheme.onSurface,
            supportingColor = if (toned) selectedSupportingInk() else supportingInk(),
        ),
        contentPadding = rowContentPadding,
    ) {
        Text(
            text = scenario.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (toned) FontWeight.Medium else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ScenarioRowSummary(
    scenario: Scenario,
    move: PriorityMove?,
    modifier: Modifier = Modifier,
) {
    Text(
        text = move
            ?.let { stringResource(R.string.scenarios_drag_change, scenario.priority, it.priority) }
            ?: conditionSummary(scenario),
        modifier = modifier,
        style = supportingStyle,
        color = if (move == null) LocalContentColor.current else MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ScenarioRowTrailing(
    scenario: Scenario,
    previewPriority: Int?,
    actions: ScenarioActions,
    toggleLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.highlightPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScenarioBadge(scenario = scenario, preview = previewPriority)
        RowSwitch(
            checked = scenario.enabled,
            onCheckedChange = { enabled -> actions.toggle(scenario, enabled) },
            modifier = Modifier.semantics { contentDescription = toggleLabel },
        )
        ScenarioRowMenu(scenario = scenario, actions = actions)
    }
}

@Composable
private fun ScenarioLeadingIcon(
    glyph: String?,
    iconApp: ActionApp?,
    launcherApps: LauncherAppsRepository,
    toned: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(Tokens.Settings.leadingBox)
            .background(
                color = if (toned) scheme.primary else scheme.surfaceContainerHigh,
                shape = RoundedCornerShape(Tokens.Settings.leadingRadius),
            ),
        contentAlignment = Alignment.Center,
    ) {
        ScenarioItemIcon(
            glyph = glyph,
            iconApp = iconApp,
            launcherApps = launcherApps,
            size = Tokens.Settings.leadingGlyph,
            tint = if (toned) scheme.onPrimary else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Modifier.priorityDrag(drag: ScenarioDrag, actions: ScenarioActions): Modifier {
    val up = stringResource(R.string.scenarios_move_up)
    val down = stringResource(R.string.scenarios_move_down)
    val apply: (Int, Int) -> Unit = { from, to ->
        priorityMove(drag.group, from, to)?.let { actions.applyPriorities(it.updates) }
    }
    return this
        .reorderable(
            state = drag.state,
            group = drag.key,
            index = drag.index,
            count = drag.group.size,
            onDrop = apply,
        )
        .semantics {
            customActions = buildList {
                if (drag.index > 0) {
                    add(CustomAccessibilityAction(up) { apply(drag.index, drag.index - 1); true })
                }
                if (drag.index < drag.group.lastIndex) {
                    add(CustomAccessibilityAction(down) { apply(drag.index, drag.index + 1); true })
                }
            }
        }
}

@Composable
private fun ScenarioRowMenu(
    scenario: Scenario,
    actions: ScenarioActions,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { open = true }) {
            GlyphIcon(
                name = GLYPH_MENU,
                size = Tokens.Settings.glyph,
                tint = LocalContentColor.current,
                contentDescription = stringResource(R.string.scenarios_row_menu),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem(title = stringResource(R.string.scenarios_menu_edit)) {
                open = false
                actions.edit(scenario)
            }
            MenuItem(title = stringResource(R.string.scenarios_menu_duplicate)) {
                open = false
                actions.duplicate(scenario)
            }
            MenuItem(title = stringResource(R.string.scenarios_menu_export)) {
                open = false
                actions.export(scenario)
            }
            MenuItem(title = stringResource(R.string.scenarios_menu_delete)) {
                open = false
                actions.delete(scenario)
            }
        }
    }
}

@Composable
private fun ScenarioBadge(
    scenario: Scenario,
    modifier: Modifier = Modifier,
    preview: Int? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val incomplete = remember(scenario) { placeholderKeysIn(scenario).isNotEmpty() }
    val text = when {
        preview != null -> preview.toString()
        incomplete -> stringResource(R.string.scenarios_incomplete_badge)
        else -> scenario.priority.toString()
    }

    val highlighted = preview != null
    // The pin is deliberately invisible on the home screen, but here the list is ordered by priority
    // and a pinned scenario no longer follows that order — without the mark the row would lie.
    val pinned = scenario.pinnedPosition?.takeIf { !incomplete }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Tokens.Scenarios.badgeRadius),
        color = if (highlighted) scheme.surface else scheme.surfaceContainer,
        contentColor = if (highlighted) scheme.primary else scheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Tokens.Scenarios.badgePadding,
                vertical = Tokens.Settings.rowGap,
            ),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pinned?.let { position ->
                GlyphIcon(
                    name = GLYPH_PIN,
                    size = Tokens.Settings.badgeGlyph,
                    tint = LocalContentColor.current,
                    contentDescription = stringResource(R.string.scenarios_pinned_badge, position + 1),
                )
            }
            Text(text = text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun MenuItem(
    title: String,
    reason: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                reason?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        onClick = onClick,
        enabled = enabled,
    )
}

internal const val GLYPH_MENU = "dots-vertical"
private const val GLYPH_PIN = "pin"
