package app.cue.launcher.engine

import app.cue.launcher.data.ha.haEntityIdOrNull
import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TileContent

fun haStateRefs(scenarios: List<Scenario>, activations: List<Activation>, now: Long): Set<String> {
    val byScenario = activations.associateBy(Activation::scenarioId)
    return visibleScenarios(scenarios, byScenario, now)
        // A payload field whose name happens to contain a dot looks exactly like an entity id. The
        // event already carried its value, so reading it over REST would be a request for nothing.
        .flatMapTo(mutableSetOf()) { scenario ->
            (haStateRefs(scenario.content) + haIconRefs(scenario.content)) - suppliedRefs(byScenario[scenario.id])
        }
}

internal fun visibleScenarios(
    scenarios: List<Scenario>,
    byScenario: Map<String, Activation>,
    now: Long,
): List<Scenario> = scenarios.filter { scenario ->
    val activation = byScenario[scenario.id]
    scenario.enabled && scenario.size in BENTO_SIZES &&
        (scenario.isFallback || activation?.isExpired(now) == false)
}

fun haGateEntities(scenarios: List<Scenario>): Set<String> = scenarios
    .filter(Scenario::enabled)
    .flatMap(Scenario::stateConditions)
    .filterIsInstance<Condition.HaState>()
    .mapNotNullTo(mutableSetOf()) { haEntityIdOrNull(it.entity) }

// Values the tile cannot be drawn without. An entity missing from here is a tile that does not
// show at all, which is why the icon's entity is deliberately not part of it: its state only
// decides a colour, and a tile whose gate will not read still renders perfectly well.
fun haStateRefs(content: TileContent): Set<String> {
    if (content !is TileContent.Template) return emptySet()
    val texts = listOf(content.text, content.text2) + content.items.map { it.label }
    return texts.flatMapTo(mutableSetOf()) { text ->
        templateValueKeys(text).mapNotNull(::haStateRefOf)
    }
}

// Every icon on a tile carries its own action, so every icon's entity is worth reading — but only
// ever to colour that one icon.
fun haIconRefs(content: TileContent): Set<String> =
    (content as? TileContent.Template)?.items?.mapNotNullTo(mutableSetOf(), ::haIconEntity).orEmpty()

// A stateEntity that is not an entity id (an unfilled placeholder, a typo) leaves the icon without an
// accent rather than falling back to entity, whose state the scenario asked to be ignored.
fun haIconEntity(item: TemplateItem): String? {
    val action = item.primaryAction as? TapAction.HaAction ?: return null
    return (action.stateEntity?.takeIf(String::isNotBlank) ?: action.entity)?.let(::haEntityIdOrNull)
}

// The accent belongs to a Home Assistant entity that has a state to show — an open gate, a lamp
// that is on. Everything else keeps the ordinary icon colour.
fun haAccentedEntities(content: TileContent, values: Map<String, String>): Set<String> =
    haIconRefs(content).filterTo(mutableSetOf()) { entity ->
        val state = values["$HA_VALUE_NAMESPACE:$entity"]?.trim()?.lowercase()
        state != null && state in ACTIVE_HA_STATES
    }

fun haStateRefOf(key: String): String? {
    val (namespace, value) = key.substringBefore(':') to key.substringAfter(':', "")
    if (!namespace.equals(HA_VALUE_NAMESPACE, ignoreCase = true)) return null
    return haEntityIdOrNull(value)
}

fun haStateValues(states: Map<String, String>): Map<String, String> =
    states.mapKeys { (entity, _) -> "$HA_VALUE_NAMESPACE:${entity.trim().lowercase()}" }

private fun suppliedRefs(activation: Activation?): Set<String> =
    activation?.payload?.fields?.keys.orEmpty().mapNotNullTo(mutableSetOf(), ::haStateRefOf)

private val ACTIVE_HA_STATES = setOf(
    "on",
    "open",
    "opening",
    "playing",
    "home",
    "unlocked",
    "heat",
    "cool",
)
