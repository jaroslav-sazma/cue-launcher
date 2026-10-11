package app.cue.launcher.feature.drawer.search

import app.cue.launcher.data.launchlog.LaunchStats
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.system.InstalledApp
import app.cue.launcher.system.SearchableApp
import app.cue.launcher.system.installedAppOrder
import java.text.Collator

enum class SearchField(val weight: Double, val allowFuzzy: Boolean) {

    LABEL(weight = 1.0, allowFuzzy = true),

    ENGLISH_LABEL(weight = 0.7, allowFuzzy = true),

    PACKAGE(weight = 0.6, allowFuzzy = false),
}

data class AppSearchResult(
    val app: InstalledApp,
    val matchedField: SearchField,
    val quality: MatchQuality,

    val lastLaunchedAt: Long? = null,

    val searchHits: Int = 0,
) {
    val score: Double get() = quality.score * matchedField.weight
}

class AppSearchIndex internal constructor(
    private val entries: List<Entry>,
    order: Comparator<InstalledApp>,
) {

    private val tiebreak: Comparator<AppSearchResult> =
        compareByDescending<AppSearchResult> { it.lastLaunchedAt ?: NEVER_LAUNCHED }
            .thenBy { it.app.label.length }
            .then(Comparator { first, second -> order.compare(first.app, second.app) })

    // A single letter is how a name starts, not a fragment of one, so there the match leads and the
    // count only breaks ties.
    private val byMatch: Comparator<AppSearchResult> =
        compareByDescending<AppSearchResult> { it.score }
            .thenByDescending { it.searchHits }
            .then(tiebreak)

    // From two letters up the count leads instead, bounded by two tiers: a fully typed name stays
    // on top, and a guessed match never overtakes a literal one.
    private val byFrequency: Comparator<AppSearchResult> =
        compareByDescending<AppSearchResult> { it.quality == MatchQuality.EXACT }
            .thenByDescending { it.quality.confident }
            .thenByDescending { it.searchHits }
            .thenByDescending { it.score }
            .then(tiebreak)

    fun search(query: String): List<AppSearchResult> {
        val normalized = normalizeForSearch(query)
        if (normalized.isEmpty()) return emptyList()
        val ranking = if (normalized.length < FREQUENCY_LEADS_FROM) byMatch else byFrequency
        return entries.mapNotNull { it.match(normalized) }.sortedWith(ranking)
    }

    fun bestMatch(query: String): AppSearchResult? = search(query).firstOrNull()

    internal class Entry(
        private val app: InstalledApp,
        private val fields: List<IndexedField>,
        private val stats: LaunchStats?,
    ) {

        fun match(query: String): AppSearchResult? = fields
            .mapNotNull { indexed ->
                FuzzyMatch.quality(indexed.text, query, indexed.field.allowFuzzy)
                    ?.let {
                        AppSearchResult(
                            app = app,
                            matchedField = indexed.field,
                            quality = it,
                            lastLaunchedAt = stats?.lastLaunchedAt,
                            searchHits = stats?.searchHits ?: 0,
                        )
                    }
            }
            .maxWithOrNull(
                compareBy<AppSearchResult> { it.score }.thenByDescending { it.matchedField.ordinal },
            )
    }

    internal class IndexedField(val field: SearchField, val text: SearchableText)

    companion object {

        val EMPTY = AppSearchIndex(entries = emptyList(), order = Comparator { _, _ -> 0 })

        private const val NEVER_LAUNCHED = Long.MIN_VALUE

        private const val FREQUENCY_LEADS_FROM = 2
    }
}

fun appSearchIndexOf(
    apps: List<SearchableApp>,
    stats: Map<ComponentRef, LaunchStats> = emptyMap(),
    collator: Collator = Collator.getInstance(),
): AppSearchIndex = AppSearchIndex(
    entries = apps.map { entryOf(it, stats[it.app.component]) },
    order = installedAppOrder(collator),
)

private fun entryOf(searchable: SearchableApp, stats: LaunchStats?): AppSearchIndex.Entry {
    val label = SearchableText.of(searchable.app.label)
    val english = searchable.englishLabel?.let(SearchableText::of)
    val fields = buildList {
        add(AppSearchIndex.IndexedField(SearchField.LABEL, label))

        if (english != null && english.text.isNotEmpty() && english.text != label.text) {
            add(AppSearchIndex.IndexedField(SearchField.ENGLISH_LABEL, english))
        }
        val packageText = SearchableText.of(searchable.app.component.packageName)
        add(AppSearchIndex.IndexedField(SearchField.PACKAGE, packageText))
    }
    return AppSearchIndex.Entry(searchable.app, fields, stats)
}
