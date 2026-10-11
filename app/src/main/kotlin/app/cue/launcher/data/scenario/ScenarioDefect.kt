package app.cue.launcher.data.scenario

import app.cue.launcher.data.ha.haEntityIdOrNull

sealed interface ScenarioDefect {

    data class UnsupportedSchemaVersion(val found: Int, val supported: Int = SCENARIO_SCHEMA_VERSION) :
        ScenarioDefect

    sealed interface Malformed : ScenarioDefect {

        data object NotJsonObject : Malformed

        data object ScenariosNotList : Malformed

        data object ConfigUnreadable : Malformed

        data object ScenarioNotObject : Malformed

        data class UnknownVersion(val version: Int) : Malformed

        data class Unreadable(val detail: String?) : Malformed
    }

    data class UnknownEnum(val field: String, val value: String) : ScenarioDefect

    data class MissingField(val field: String) : ScenarioDefect

    data object OverlayUnsupported : ScenarioDefect

    data class BlankId(val name: String) : ScenarioDefect

    data class NegativePriority(val priority: Int) : ScenarioDefect

    data class PinnedPositionOutOfRange(val position: Int, val size: TileSize) : ScenarioDefect

    data class MultipleInstantConditions(val count: Int) : ScenarioDefect

    data class DurationOnStateCondition(val type: String) : ScenarioDefect

    data class InvalidDuration(val durationSec: Int) : ScenarioDefect

    data class LayoutSizeMismatch(val layout: TemplateLayout, val size: TileSize) : ScenarioDefect

    data class ItemCountMismatch(val layout: TemplateLayout, val expected: Int, val actual: Int) :
        ScenarioDefect

    data class WidgetSizeMismatch(val widget: LauncherWidgetType, val size: TileSize) : ScenarioDefect

    data class InvalidTime(val field: String, val value: String) : ScenarioDefect

    data class InvalidDays(val days: List<Int>) : ScenarioDefect

    data class TooManyExtractRules(val count: Int, val max: Int = EXTRACT_MAX_RULES) : ScenarioDefect

    data class InvalidExtractRule(val name: String, val problem: ExtractProblem) : ScenarioDefect

    data class InvalidHaEntity(val entity: String) : ScenarioDefect
}

private val TIME_PATTERN = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")

fun validateScenario(scenario: Scenario): List<ScenarioDefect> {
    val defects = mutableListOf<ScenarioDefect>()

    if (scenario.id.isBlank()) defects += ScenarioDefect.BlankId(scenario.name)
    if (scenario.size == TileSize.OVERLAY) defects += ScenarioDefect.OverlayUnsupported
    if (scenario.priority < 0) defects += ScenarioDefect.NegativePriority(scenario.priority)
    scenario.pinnedPosition?.takeUnless { pinFits(scenario.size, it) }?.let { position ->
        defects += ScenarioDefect.PinnedPositionOutOfRange(position, scenario.size)
    }

    val instants = scenario.instantConditions
    if (instants.size > 1 && scenario.match == ConditionMatch.ALL) {
        defects += ScenarioDefect.MultipleInstantConditions(instants.size)
    }
    instants.forEach { instant ->
        val duration = instant.durationSec
        if (duration != null && duration <= 0) defects += ScenarioDefect.InvalidDuration(duration)
    }

    scenario.conditions.forEach { defects += validateCondition(it) }
    defects += validateContent(scenario)

    return defects
}

private fun validateCondition(condition: Condition): List<ScenarioDefect> = when (condition) {
    is Condition.TimeWindow -> buildList {
        addAll(timeDefects("time_window.from", condition.from))
        addAll(timeDefects("time_window.to", condition.to))
        addAll(dayDefects(condition.days))
    }

    is Condition.TimeInstant -> timeDefects("time_instant.at", condition.at) + dayDefects(condition.days)

    is Condition.Wifi ->
        if (condition.mode == WifiMode.SSID && condition.ssid.isNullOrBlank()) {
            listOf(ScenarioDefect.MissingField("wifi.ssid"))
        } else {
            emptyList()
        }

    is Condition.NotificationFrom -> extractDefects(condition.extract)

    is Condition.NotificationEnded ->
        if (condition.packageName.isBlank()) {
            listOf(ScenarioDefect.MissingField("notification_ended.package"))
        } else {
            emptyList()
        }

    is Condition.HaState -> haStateDefects(condition)

    else -> emptyList()
}

private fun haStateDefects(condition: Condition.HaState): List<ScenarioDefect> = buildList {
    val entity = condition.entity
    when {
        entity.isBlank() -> add(ScenarioDefect.MissingField("ha_state.entity"))
        haEntityIdOrNull(entity) == null && !hasPlaceholder(entity) -> add(ScenarioDefect.InvalidHaEntity(entity))
    }
    if (tidyHaStates(condition.states).isEmpty()) add(ScenarioDefect.MissingField("ha_state.states"))
}

private fun extractDefects(rules: List<ExtractRule>): List<ScenarioDefect> = buildList {
    if (rules.size > EXTRACT_MAX_RULES) add(ScenarioDefect.TooManyExtractRules(rules.size))
    extractProblems(rules).forEach { (rule, problem) -> add(ScenarioDefect.InvalidExtractRule(rule.name, problem)) }
}

private fun timeDefects(field: String, value: String): List<ScenarioDefect> =
    if (TIME_PATTERN.matches(value)) emptyList() else listOf(ScenarioDefect.InvalidTime(field, value))

private fun dayDefects(days: List<Int>): List<ScenarioDefect> =
    if (days.all { it in WEEK_DAYS }) emptyList() else listOf(ScenarioDefect.InvalidDays(days))

private fun validateContent(scenario: Scenario): List<ScenarioDefect> = when (val content = scenario.content) {
    is TileContent.Template -> buildList {
        if (content.layout.size != scenario.size) {
            add(ScenarioDefect.LayoutSizeMismatch(content.layout, scenario.size))
        }
        if (content.items.size != content.layout.itemCount) {
            add(
                ScenarioDefect.ItemCountMismatch(
                    layout = content.layout,
                    expected = content.layout.itemCount,
                    actual = content.items.size,
                ),
            )
        }

        content.items.forEachIndexed { index, item ->
            if (item.actions.isEmpty()) add(ScenarioDefect.MissingField("items[$index].action"))
        }
    }

    is TileContent.LauncherWidget ->
        if (content.widget.size != scenario.size) {
            listOf(ScenarioDefect.WidgetSizeMismatch(content.widget, scenario.size))
        } else {
            emptyList()
        }

    is TileContent.AppWidget -> emptyList()
}
