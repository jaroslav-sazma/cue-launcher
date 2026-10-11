package app.cue.launcher.data.aichat

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TileContent

// An event arrives as a broadcast from the blueprint and needs no connection between Cue: and Home
// Assistant; actions and states read from it do.
data class HaNeeds(val blueprint: Boolean, val connection: Boolean) {
    val any: Boolean get() = blueprint || connection
}

fun haNeedsOf(scenario: Scenario, haConfigured: Boolean): HaNeeds {
    val event = scenario.conditions.any { it is Condition.HaEvent }
    val state = scenario.conditions.any { it is Condition.HaState }
    val action = (scenario.content as? TileContent.Template)?.items.orEmpty()
        .flatMap { it.actions }
        .any { it is TapAction.HaAction }
    return HaNeeds(blueprint = event, connection = (action || state) && !haConfigured)
}
