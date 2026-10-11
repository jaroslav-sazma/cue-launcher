package app.cue.launcher.feature.drawer.search

import kotlin.math.min

// `confident` means the query is literally in the text; the loose qualities only guess at it, so
// ranking never lets a guess overtake a literal hit, however often the app was launched.
enum class MatchQuality(val score: Double, val confident: Boolean) {

    EXACT(score = 1.0, confident = true),

    PREFIX(score = 0.9, confident = true),

    WORD_PREFIX(score = 0.8, confident = true),

    SUBSTRING(score = 0.6, confident = true),

    TYPO(score = 0.45, confident = false),

    SUBSEQUENCE(score = 0.3, confident = false),
}

class SearchableText(val text: String, val words: List<String>) {

    companion object {

        fun of(raw: String): SearchableText {
            val normalized = normalizeForSearch(raw)
            return SearchableText(normalized, searchWords(normalized))
        }
    }
}

object FuzzyMatch {

    const val ONE_TYPO_LENGTH = 4

    const val TWO_TYPOS_LENGTH = 8

    fun quality(text: SearchableText, query: String, allowFuzzy: Boolean = true): MatchQuality? {
        val field = text.text
        if (field.isEmpty() || query.isEmpty()) return null
        return when {
            field == query -> MatchQuality.EXACT
            field.startsWith(query) -> MatchQuality.PREFIX
            text.words.any { it.startsWith(query) } -> MatchQuality.WORD_PREFIX
            field.contains(query) -> MatchQuality.SUBSTRING
            !allowFuzzy -> null
            hasTypoPrefix(text, query) -> MatchQuality.TYPO
            isSubsequence(query, field) -> MatchQuality.SUBSEQUENCE
            else -> null
        }
    }

    fun typoAllowance(queryLength: Int): Int = when {
        queryLength < ONE_TYPO_LENGTH -> 0
        queryLength < TWO_TYPOS_LENGTH -> 1
        else -> TWO_TYPOS
    }

    @Suppress("CognitiveComplexMethod", "NestedBlockDepth", "ReturnCount")
    fun prefixDistance(query: String, candidate: String, limit: Int): Int {
        if (query.length - candidate.length > limit) return limit + 1

        val columns = min(candidate.length, query.length + limit)
        var twoRowsBack = IntArray(columns + 1)
        var previousRow = IntArray(columns + 1) { it }
        var currentRow = IntArray(columns + 1)
        for (i in 1..query.length) {
            currentRow[0] = i
            var rowMinimum = i
            for (j in 1..columns) {
                val substitution = if (query[i - 1] == candidate[j - 1]) 0 else 1
                var value = min(
                    min(previousRow[j] + 1, currentRow[j - 1] + 1),
                    previousRow[j - 1] + substitution,
                )
                val transposed = i > 1 && j > 1 &&
                    query[i - 1] == candidate[j - 2] && query[i - 2] == candidate[j - 1]
                if (transposed) value = min(value, twoRowsBack[j - 2] + 1)
                currentRow[j] = value
                rowMinimum = min(rowMinimum, value)
            }
            if (rowMinimum > limit) return limit + 1
            val spare = twoRowsBack
            twoRowsBack = previousRow
            previousRow = currentRow
            currentRow = spare
        }

        return previousRow.min()
    }

    fun isSubsequence(query: String, text: String): Boolean {
        var matched = 0
        for (character in text) {
            if (matched == query.length) break
            if (character == query[matched]) matched++
        }
        return matched == query.length
    }

    private fun hasTypoPrefix(text: SearchableText, query: String): Boolean {
        val allowance = typoAllowance(query.length)
        if (allowance == 0) return false
        val candidates = sequenceOf(text.text) + text.words.asSequence()
        return candidates.any { prefixDistance(query, it, allowance) <= allowance }
    }

    private const val TWO_TYPOS = 2
}
