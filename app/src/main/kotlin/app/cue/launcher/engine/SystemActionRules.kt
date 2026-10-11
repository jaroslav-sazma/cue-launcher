package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TileContent

fun tapActionsOf(scenario: Scenario): List<TapAction> = when (val content = scenario.content) {
    is TileContent.Template -> content.items.flatMap(TemplateItem::actions)
    is TileContent.LauncherWidget, is TileContent.AppWidget -> emptyList()
}

fun needsDndAccess(action: SystemActionType): Boolean = action in DND_ACTIONS

fun usesDndAccess(scenarios: List<Scenario>): Boolean = scenarios.any { scenario ->
    tapActionsOf(scenario).any { it is TapAction.SystemAction && needsDndAccess(it.action) }
}

// A ringer that rings or vibrates ends Do Not Disturb, so a dnd_on written before it in the same
// tap would be undone by it; it always goes last, the rest keeps its written order. dnd_off stays
// put: moved behind ringer_silent, it would end the Do Not Disturb that one is meant to bring.
fun executionOrder(actions: List<TapAction>): List<TapAction> =
    actions.sortedBy { it is TapAction.SystemAction && it.action == SystemActionType.DND_ON }

private val DND_ACTIONS = setOf(
    SystemActionType.RINGER_SILENT,
    SystemActionType.RINGER_VIBRATE,
    SystemActionType.RINGER_NORMAL,
    SystemActionType.DND_ON,
    SystemActionType.DND_OFF,
)
