package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.ha.HA_ID_PATTERN
import app.cue.launcher.data.ha.HaEntity
import app.cue.launcher.data.ha.HaService
import app.cue.launcher.data.ha.fits
import app.cue.launcher.data.ha.fitsDomain
import app.cue.launcher.feature.drawer.search.normalizeForSearch
import app.cue.launcher.feature.drawer.search.searchWords

class HaSearchRow(val value: String, val title: String, val detail: String?, names: List<String?>) {

    internal val keys: List<String> = names.filterNotNull().map(::normalizeForSearch).filter(String::isNotEmpty)

    internal val words: List<String> = keys.flatMap(::searchWords)
}

// Built once per list, off the main thread: an instance can hold thousands of entities, and folding
// every name on each keystroke would show.
class HaEntityChoices(val entities: List<HaEntity>) {

    val rows: List<HaSearchRow> = haEntityRows(entities)

    val byId: Map<String, HaEntity> = entities.associateBy(HaEntity::id)

    companion object {
        val NONE = HaEntityChoices(emptyList())
    }
}

class HaServiceChoices(val services: List<HaService>) {

    private val rows: Map<String, HaSearchRow> = haServiceRows(services).associateBy(HaSearchRow::value)

    fun rowsFor(entity: String, entities: HaEntityChoices): List<HaSearchRow> =
        haServicesFor(services, entities.byId, entity).mapNotNull { rows[it.id] }

    companion object {
        val NONE = HaServiceChoices(emptyList())
    }
}

class HaChoices(val entities: HaEntityChoices, val services: HaServiceChoices) {

    companion object {
        val NONE = HaChoices(HaEntityChoices.NONE, HaServiceChoices.NONE)
    }
}

data class HaSuggestions(val rows: List<HaSearchRow>, val more: Int) {

    companion object {
        val NONE = HaSuggestions(emptyList(), 0)
    }
}

fun haEntityRows(entities: List<HaEntity>): List<HaSearchRow> = entities.map { entity ->
    HaSearchRow(
        value = entity.id,
        title = entity.name ?: entity.id,
        detail = entity.id.takeIf { entity.name != null },
        names = listOf(entity.name),
    )
}

fun haServiceRows(services: List<HaService>): List<HaSearchRow> = services.map { service ->
    val title = service.localName ?: service.name
    HaSearchRow(
        value = service.id,
        title = title ?: service.id,
        detail = service.id.takeIf { title != null },
        names = listOf(service.localName, service.name),
    )
}

fun haServicesFor(services: List<HaService>, entities: Map<String, HaEntity>, entity: String): List<HaService> {
    val typed = entity.trim()
    val known = entities[typed]
    val domain = known?.domain ?: typed.takeIf(HA_ID_PATTERN::matches)?.substringBefore('.')
    val relevant = when {
        known != null -> services.filter { it.fits(known) }
        domain != null -> services.filter { it.fitsDomain(domain) }
        else -> services
    }
    return relevant.sortedBy { it.domain != domain }
}

// Text that is exactly one of the values is a choice already made: the whole list comes back, that
// value first, the way a select box reopens. Anything else filters.
fun haSuggestions(rows: List<HaSearchRow>, query: String, limit: Int): HaSuggestions {
    val typed = query.trim()
    val needle = normalizeForSearch(query)
    val chosen = rows.indexOfFirst { it.value == typed }
    val ordered = when {
        chosen >= 0 -> listOf(rows[chosen]) + rows.filterIndexed { index, _ -> index != chosen }
        needle.isEmpty() -> rows
        else -> rows.filter { row -> needle in row.value || row.keys.any { needle in it } }
            .sortedBy { row -> !row.leads(needle) }
    }
    return HaSuggestions(ordered.take(limit), (ordered.size - limit).coerceAtLeast(0))
}

private fun HaSearchRow.leads(needle: String): Boolean =
    value.startsWith(needle) || value.substringAfter('.').split('_').any { it.startsWith(needle) } ||
        keys.any { it.startsWith(needle) } || words.any { it.startsWith(needle) }
