package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.ha.HaStateRead
import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.engine.ConditionOutcome
import app.cue.launcher.engine.SystemState
import app.cue.launcher.engine.bentoSlots
import app.cue.launcher.engine.haStateRefs
import app.cue.launcher.engine.sizeClassQueues
import app.cue.launcher.engine.stateGateOutcome

data class ConditionDiagnosis(
    val index: Int,
    val condition: Condition,
    val reading: LiveReading,

    // Under ANY a condition that misses blames nothing as long as a sibling is holding.
    val excused: Boolean = false,
) {

    val blocking: Boolean get() = !excused && reading.verdict == LiveVerdict.FAILS
}

enum class ContentHealth {

    READY,

    WIDGET_UNBOUND,

    WIDGET_UNSUPPORTED,

    HA_STATE_MISSING,

    GOOGLE_TV_MISSING,
}

data class ScenarioDiagnosis(
    val conditions: List<ConditionDiagnosis>,
    val content: ContentHealth,

    val outrankedBy: List<Scenario>,
)

@Suppress("LongParameterList")
fun diagnoseScenario(
    scenario: Scenario,
    state: SystemState,
    scenarios: List<Scenario>,
    activations: List<Activation>,
    now: Long,
    haRead: HaStateRead? = null,
    unavailable: Set<SystemActionType> = emptySet(),
): ScenarioDiagnosis {
    val excused = scenario.match == ConditionMatch.ANY &&
        stateGateOutcome(scenario, state) == ConditionOutcome.HOLDS

    return ScenarioDiagnosis(
        conditions = scenario.conditions.mapIndexed { index, condition ->
            ConditionDiagnosis(index, condition, liveReading(condition, state), excused)
        },
        content = contentHealth(scenario.content, haRead, unavailable),
        outrankedBy = outrankedBy(scenario, scenarios, activations, now),
    )
}

fun contentHealth(
    content: TileContent,
    haRead: HaStateRead? = null,
    unavailable: Set<SystemActionType> = emptySet(),
): ContentHealth = when (content) {
    is TileContent.AppWidget ->
        if (content.boundAppWidgetId == null) ContentHealth.WIDGET_UNBOUND else ContentHealth.READY

    is TileContent.LauncherWidget ->
        if (content.widget.size == null) ContentHealth.WIDGET_UNSUPPORTED else ContentHealth.READY

    is TileContent.Template -> templateHealth(content, haRead, unavailable)
}

private fun templateHealth(
    content: TileContent.Template,
    haRead: HaStateRead?,
    unavailable: Set<SystemActionType>,
): ContentHealth {
    val refs = haStateRefs(content)
    val actions = content.items.flatMap(TemplateItem::actions)
    return when {
        refs.isNotEmpty() && haRead != null && haRead.missing(refs).isNotEmpty() -> ContentHealth.HA_STATE_MISSING
        actions.any { it is TapAction.SystemAction && it.action in unavailable } -> ContentHealth.GOOGLE_TV_MISSING
        else -> ContentHealth.READY
    }
}

private fun outrankedBy(
    scenario: Scenario,
    scenarios: List<Scenario>,
    activations: List<Activation>,
    now: Long,
): List<Scenario> {
    val queue = sizeClassQueues(scenarios, activations, now)[scenario.size]
    if (queue.entries.none { it.scenario.id == scenario.id }) return emptyList()
    // Read it off the laid out slots rather than the queue order: a pinned scenario holds its slot
    // no matter how many higher priorities are queued in front of it.
    val shown = bentoSlots(queue).mapNotNull { it.entry }
    return if (shown.any { it.scenario.id == scenario.id }) emptyList() else shown.map { it.scenario }
}
