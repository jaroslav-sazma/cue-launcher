package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.designsystem.glyph.mdiGlyphName
import app.cue.launcher.feature.drawer.search.FuzzyMatch
import app.cue.launcher.feature.drawer.search.SearchableText
import app.cue.launcher.feature.drawer.search.normalizeForSearch

class GlyphIndex(private val entries: List<GlyphEntry>) {

    val names: List<String> = entries.map(GlyphEntry::name)

    fun search(query: String): List<String> {
        val needle = normalizeForSearch(searchableGlyphForm(mdiGlyphName(query) ?: query))
        if (needle.isEmpty()) return names
        return entries
            .mapNotNull { entry ->
                FuzzyMatch.quality(entry.text, needle)?.let { ScoredGlyph(entry.name, it.score) }
            }
            .sortedWith(
                compareByDescending<ScoredGlyph> { it.score }
                    .thenBy { it.name.length }
                    .thenBy { it.name },
            )
            .map(ScoredGlyph::name)
    }
}

class GlyphEntry(val name: String, val text: SearchableText)

fun glyphIndexOf(names: Collection<String>): GlyphIndex = GlyphIndex(
    names.sorted().map { name -> GlyphEntry(name, SearchableText.of(searchableGlyphForm(name))) },
)

private fun searchableGlyphForm(raw: String): String = raw.replace('-', ' ')

private data class ScoredGlyph(val name: String, val score: Double)
