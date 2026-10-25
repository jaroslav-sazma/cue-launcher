package app.cue.launcher.engine

import app.cue.launcher.data.ha.haStateGlyphs
import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TileContent

fun haGlyphRefs(scenarios: List<Scenario>, activations: List<Activation>, now: Long): Set<String> {
    val byScenario = activations.associateBy(Activation::scenarioId)
    // An event that brings a glyph puts it on every Home Assistant icon left empty (TemplatePayload).
    return visibleScenarios(scenarios, byScenario, now)
        .filter { scenario -> scenario.isFallback || byScenario[scenario.id]?.payload?.icon == null }
        .flatMapTo(mutableSetOf()) { haGlyphEntities(it.content) }
}

// An icon the user chose stays as it is; only one left empty follows its entity the way Home
// Assistant draws it.
fun haGlyphEntities(content: TileContent): Set<String> =
    (content as? TileContent.Template)?.items?.mapNotNullTo(mutableSetOf(), ::haGlyphEntity).orEmpty()

fun haGlyphEntity(item: TemplateItem): String? = if (item.icon.isNullOrBlank()) haIconEntity(item) else null

fun haTileGlyphs(
    content: TileContent,
    values: Map<String, String>,
    icons: HaIconContext,
): Map<String, List<String>> = haGlyphEntities(content)
    .associateWith { entity ->
        val state = values["$HA_VALUE_NAMESPACE:$entity"]?.trim()?.lowercase()
        haStateGlyphs(icons.table, entity, state, icons.attributes[entity])
    }
    .filterValues(List<String>::isNotEmpty)
