package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.ConditionType
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.pinFits
import java.util.UUID

data class ScenarioDraft(

    val match: ConditionMatch = ConditionMatch.ALL,

    val conditions: List<Condition> = emptyList(),

    val fallback: Boolean = false,

    val name: String = "",

    val size: TileSize? = null,

    val content: ContentDraft = ContentDraft(),

    val priorityText: String = DEFAULT_PRIORITY,

    val pinnedPosition: Int? = null,

    val enabled: Boolean = true,
) {

    val canContinue: Boolean get() = fallback || conditions.isNotEmpty()

    fun withSize(next: TileSize): ScenarioDraft = if (next == size) {
        this
    } else {
        copy(
            size = next,
            content = defaultContentDraft(next),
            pinnedPosition = pinnedPosition.takeIf { pinFits(next, it) },
        )
    }

    val instants: List<Condition.InstantCondition>
        get() = conditions.filterIsInstance<Condition.InstantCondition>()

    val instant: Condition.InstantCondition?
        get() = instants.firstOrNull()

    // ALL gates one event with states, so a second event would have nothing to mean; ANY lets
    // events stand side by side, so switching back to ALL has to wait for the extras to go.
    val acceptsInstant: Boolean
        get() = conditions.size < 2 || match == ConditionMatch.ANY || instant == null

    val acceptsMatchAll: Boolean
        get() = instants.size <= 1

    fun withCondition(condition: Condition): ScenarioDraft {
        val joinsInstant = condition is Condition.InstantCondition && instant != null
        return when {
            // Below two conditions the switch is hidden, so what it says is decided here anew.
            conditions.size < 2 -> copy(
                conditions = conditions + condition,
                match = if (joinsInstant) ConditionMatch.ANY else ConditionMatch.ALL,
                fallback = false,
            )
            joinsInstant && match == ConditionMatch.ALL -> copy(
                conditions = conditions.map { if (it is Condition.InstantCondition) condition else it },
                fallback = false,
            )
            else -> copy(conditions = conditions + condition, fallback = false)
        }
    }

    fun withMatch(next: ConditionMatch): ScenarioDraft =
        if (next == ConditionMatch.ALL && !acceptsMatchAll) this else copy(match = next)

    fun withConditionAt(index: Int, condition: Condition): ScenarioDraft {
        if (index !in conditions.indices) return this
        return copy(conditions = conditions.toMutableList().apply { set(index, condition) })
    }

    fun withoutConditionAt(index: Int): ScenarioDraft {
        if (index !in conditions.indices) return this
        return copy(conditions = conditions.toMutableList().apply { removeAt(index) })
    }

    fun asFallback(): ScenarioDraft = copy(conditions = emptyList(), fallback = true)
}

const val DEFAULT_PRIORITY = "0"

@Suppress("ReturnCount")
fun scenarioOf(draft: ScenarioDraft, id: String, name: String): Scenario? {
    if (!draft.canContinue) return null
    val size = draft.size ?: return null
    val content = tileContentOf(draft.content) ?: return null
    val priority = parsePriority(draft.priorityText) ?: return null
    return Scenario(
        id = id,
        name = name,
        enabled = draft.enabled,
        priority = priority,
        pinnedPosition = draft.pinnedPosition.takeIf { pinFits(size, it) },
        size = size,

        match = draft.match,
        conditions = draft.conditions,
        content = content,
    )
}

fun newScenarioId(): String = UUID.randomUUID().toString()

fun scenarioDraftOf(scenario: Scenario): ScenarioDraft = ScenarioDraft(
    match = scenario.match,
    conditions = scenario.conditions,
    fallback = scenario.isFallback,
    name = scenario.name,
    size = scenario.size,
    content = contentDraftOf(scenario.content),
    priorityText = scenario.priority.toString(),
    pinnedPosition = scenario.pinnedPosition,
    enabled = scenario.enabled,
)

fun editedScenario(original: Scenario, draft: ScenarioDraft, name: String): Scenario {
    val content = tileContentOf(draft.content)
    val size = if (content == null) original.size else draft.size ?: original.size

    // Picking a new size drops a pin the new class cannot hold, but the size itself is only written
    // once the content is complete. Until then the pin has to survive with the size it belongs to,
    // the same way the saved content does.
    val pending = draft.size != null && draft.size != size
    val pinned = if (pending) original.pinnedPosition else draft.pinnedPosition

    return original.copy(
        name = name.trim().ifEmpty { original.name },
        enabled = draft.enabled,
        priority = parsePriority(draft.priorityText) ?: original.priority,
        pinnedPosition = pinned.takeIf { pinFits(size, it) },
        size = size,
        match = draft.match,
        conditions = draft.conditions,
        content = content ?: original.content,
    )
}

enum class CatalogAvailability {

    AVAILABLE,

    BLOCKED_BY_INSTANT,
}

fun catalogAvailability(draft: ScenarioDraft, type: ConditionType): CatalogAvailability = when {
    type.isInstant && !draft.acceptsInstant -> CatalogAvailability.BLOCKED_BY_INSTANT
    else -> CatalogAvailability.AVAILABLE
}

fun draftName(draft: ScenarioDraft, suggestion: String): String =
    draft.name.trim().ifEmpty { suggestion }
