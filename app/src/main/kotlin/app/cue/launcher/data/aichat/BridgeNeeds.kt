package app.cue.launcher.data.aichat

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TileContent

fun bridgeNeededBy(scenario: Scenario, bridgePaired: Boolean): Boolean =
    !bridgePaired && (scenario.conditions.any(::readsMac) || locksMac(scenario.content))

private fun readsMac(condition: Condition): Boolean =
    condition is Condition.MacNearby || condition is Condition.MacLocked

private fun locksMac(content: TileContent): Boolean =
    (content as? TileContent.Template)?.items.orEmpty().any { item ->
        item.actions.any { it is TapAction.SystemAction && it.action == SystemActionType.MAC_LOCK }
    }
