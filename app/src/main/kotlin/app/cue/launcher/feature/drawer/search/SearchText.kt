package app.cue.launcher.feature.drawer.search

import java.text.Normalizer
import java.util.Locale

fun normalizeForSearch(text: String): String =
    COMBINING_MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
        .lowercase(Locale.ROOT)
        .trim()

fun searchWords(text: String): List<String> =
    text.split(WORD_SEPARATORS).filter(String::isNotEmpty)

private val COMBINING_MARKS = Regex("\\p{Mn}+")

private val WORD_SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
