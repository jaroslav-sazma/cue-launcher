package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.EXTRACT_MARKER_MAX_LENGTH
import app.cue.launcher.data.scenario.cappedExtractValue

// Most notifications are one line of sentences ("Místo: Hlavní 5, Praha. Vyzvednout můžete do …"),
// so a value that would run on into the next sentence is offered as ending with its own. A dot after
// a short lowercase word ("nám.", "ul.") abbreviates rather than ends. value runs from the tapped
// word to the end of its sentence, cut like a value a rule reads. The markers come simplest first —
// the sentence's own stop, then one, two or three words of the next one — because the stop or a
// short word like "Na" may stand inside the value too; the evaluator keeps the first marker that
// really ends the value there. A number in the next sentence ("Do 12. 10.") ends the words taken,
// since the next notification changes it.
internal class ExtractSentence(val value: String, val markers: List<String>)

internal fun extractSentence(text: String, words: List<SampleWord>, token: SampleToken): ExtractSentence? {
    fun endsSentence(word: SampleWord): Boolean {
        val core = text.substring(word.coreStart, word.coreEnd)
        val abbreviation = core.length <= ABBREVIATION_MAX && core.all(Char::isLowerCase)
        return !abbreviation && (word.coreEnd until word.end).any { text[it] in SENTENCE_ENDS }
    }
    val line = words.filter { it.line == token.line && it.start >= token.labelStart && it.hasCore }
    val at = line.indices.firstOrNull { index ->
        val next = line.getOrNull(index + 1)
        next != null && endsSentence(line[index]) && text[next.coreStart].isUpperCase()
    } ?: return null
    val ending = line[at]
    val stop = (ending.coreEnd until ending.end).map { text[it] }.first { it in SENTENCE_ENDS }
    val next = line.drop(at + 1)
        .take(MARKER_WORDS_MAX)
        .takeWhile { word -> (word.coreStart until word.coreEnd).none { text[it].isDigit() } }
    val opening = next.map { last -> text.substring(next.first().coreStart, last.coreEnd) }
    val markers = (listOf(stop.toString()) + opening).filter { it.length <= EXTRACT_MARKER_MAX_LENGTH }
    return cappedExtractValue(text.substring(token.start, ending.coreEnd))?.let { ExtractSentence(it, markers) }
}

private const val SENTENCE_ENDS = ".!?"

private const val ABBREVIATION_MAX = 3

private const val MARKER_WORDS_MAX = 3
