package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.ConditionType
import app.cue.launcher.feature.drawer.search.FuzzyMatch
import app.cue.launcher.feature.drawer.search.SearchableText
import app.cue.launcher.feature.drawer.search.normalizeForSearch

fun searchCatalog(entries: List<CatalogEntry>, query: String): List<ConditionType> {
    val needle = normalizeForSearch(query)
    if (needle.isEmpty()) return entries.map(CatalogEntry::type)
    return entries
        .mapIndexedNotNull { order, entry ->
            catalogScore(entry, needle)?.let { score -> ScoredType(entry.type, score, order) }
        }
        .sortedWith(compareByDescending<ScoredType> { it.score }.thenBy { it.order })
        .map(ScoredType::type)
}

private fun catalogScore(entry: CatalogEntry, needle: String): Double? {
    val label = fieldScore(entry.label, needle, LABEL_WEIGHT)
    val examples = fieldScore(entry.examples, needle, EXAMPLE_WEIGHT)
    return listOfNotNull(label, examples).maxOrNull()
}

private fun fieldScore(raw: String, needle: String, weight: Double): Double? {
    if (raw.isEmpty()) return null
    return FuzzyMatch.quality(SearchableText.of(raw), needle)?.score?.times(weight)
}

private data class ScoredType(val type: ConditionType, val score: Double, val order: Int)

private const val LABEL_WEIGHT = 1.0
private const val EXAMPLE_WEIGHT = 0.7
