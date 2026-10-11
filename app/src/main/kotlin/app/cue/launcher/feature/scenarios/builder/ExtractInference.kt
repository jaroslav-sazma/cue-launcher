package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.EXTRACT_MARKER_MAX_LENGTH
import app.cue.launcher.data.scenario.EXTRACT_MAX_INPUT
import app.cue.launcher.data.scenario.EXTRACT_NAME_MAX_LENGTH
import app.cue.launcher.data.scenario.EXTRACT_RESERVED_NAMES
import app.cue.launcher.data.scenario.ExtractKind
import app.cue.launcher.data.scenario.ExtractMatch
import app.cue.launcher.data.scenario.ExtractRule
import app.cue.launcher.data.scenario.extractKey
import app.cue.launcher.data.scenario.extractMatches
import java.text.Normalizer

// Every candidate is run through the real evaluator, so the builder never offers a rule that only
// looks right: the first one that reads exactly the tapped value at the tapped place wins.
fun inferExtractRule(sample: String, token: SampleToken, existing: List<ExtractRule>): InferredRule {
    val text = sample.take(EXTRACT_MAX_INPUT)
    val words = sampleWords(text)
    val tapped = text.substring(token.start, token.end)
    val anchors = anchorsBefore(text, words, token)
    val kinds = kindsFor(text, words, token)
    val sentence = if (ExtractKind.LINE in kinds) extractSentence(text, words, token) else null
    val untils = sentence?.let { found ->
        anchors.flatMap { anchor ->
            found.markers.map { marker ->
                Candidate(ExtractRule(PROBE, anchor?.text, ExtractKind.UNTIL, marker), anchor, found.value)
            }
        }
    }.orEmpty()
    val others = kinds.flatMap { kind ->
        anchors.map { anchor -> Candidate(ExtractRule(PROBE, anchor?.text, kind), anchor, tapped) }
    }
    val tried = (untils + others).map { candidate -> candidate to extractMatches(listOf(candidate.rule), text)[PROBE] }
    val (chosen, match) = tried.firstOrNull { (option, read) -> option.reads(read, token.start) }
        ?: tried.firstOrNull { (_, match) -> match?.start == token.start }
        ?: tried.first()
    val same = existing.firstOrNull { readsAlike(it, chosen.rule) }
    return InferredRule(
        rule = same ?: chosen.rule.copy(
            name = uniqueExtractName(nameBase(text, chosen, token), existing.map(ExtractRule::name)),
        ),
        added = same == null,
        tapped = chosen.meant,
        found = match?.value,
        reproduces = chosen.reads(match, token.start),
    )
}

// A line or a text up to a marker may run on past what was tapped and still be what was meant.
internal fun readsTapped(rule: ExtractRule, match: ExtractMatch?, start: Int, tapped: String): Boolean =
    match != null && match.start == start && when (rule.kind) {
        ExtractKind.LINE, ExtractKind.UNTIL -> match.value.startsWith(tapped)
        else -> match.value == tapped
    }

// NFKD also takes ligatures, full-width and superscript forms apart into plain letters and digits.
fun foldExtractName(text: String): String {
    val spelled = buildString { text.lowercase().forEach { append(UNDECOMPOSED_LETTERS[it] ?: it) } }
    val ascii = buildString {
        Normalizer.normalize(spelled, Normalizer.Form.NFKD).lowercase().forEach { char ->
            when {
                char in 'a'..'z' || char in '0'..'9' -> append(char)
                Character.getType(char) == Character.NON_SPACING_MARK.toInt() -> Unit
                else -> append('_')
            }
        }
    }
    return ascii.split('_').filter(String::isNotEmpty).joinToString("_")
        .take(EXTRACT_NAME_MAX_LENGTH).trimEnd('_')
}

fun uniqueExtractName(base: String, existing: Collection<String>): String {
    val taken = existing.toSet() + EXTRACT_RESERVED_NAMES
    if (base !in taken) return base
    return generateSequence(2) { it + 1 }
        .map { suffix -> "${base.take(EXTRACT_NAME_MAX_LENGTH - suffix.toString().length - 1)}_$suffix" }
        .first { it !in taken }
}

// The evaluator folds case, diacritics and blanks, so two rules equal in that form read the same.
private fun readsAlike(one: ExtractRule, other: ExtractRule): Boolean =
    one.kind == other.kind &&
        extractKey(one.anchor.orEmpty()) == extractKey(other.anchor.orEmpty()) &&
        extractKey(one.marker.orEmpty()) == extractKey(other.marker.orEmpty())

private fun kindsFor(text: String, words: List<SampleWord>, token: SampleToken): List<ExtractKind> =
    when (token.shape) {
        SampleShape.TIME -> listOf(ExtractKind.TIME)
        SampleShape.DATE -> listOf(ExtractKind.DATE)
        // A number would read only the digits before the decimal mark, a different value that
        // passes for the right one; an amount that reads nothing at least says so.
        SampleShape.AMOUNT -> listOf(ExtractKind.AMOUNT)
        SampleShape.NUMBER -> listOf(ExtractKind.NUMBER, ExtractKind.CODE, ExtractKind.WORD)
        SampleShape.CODE -> listOf(ExtractKind.CODE, ExtractKind.WORD)
        SampleShape.WORD, null -> if (startsRestOfLine(text, words, token)) {
            listOf(ExtractKind.LINE, ExtractKind.WORD)
        } else {
            listOf(ExtractKind.WORD)
        }
    }

// A word right after a label ("Místo: Hlavní 5, Praha") or at the head of a line under the first one
// usually begins a value that runs to the end of the line.
private fun startsRestOfLine(text: String, words: List<SampleWord>, token: SampleToken): Boolean {
    val line = words.filter { it.line == token.line }
    val continues = line.any { it.start >= token.labelEnd && it.hasCore }
    val previous = line.lastOrNull { it.end <= token.labelStart }
    return continues && when (previous) {
        null -> token.line > 0
        else -> !previous.hasCore || (previous.coreEnd until previous.end).any { text[it] in LABEL_ENDS }
    }
}

// The nearest words on the token's own line first, then the end of the line above. The first line
// is the title, typically a sender's name, so it never labels a value below it and is never saved
// into a scenario as one.
private fun anchorsBefore(text: String, words: List<SampleWord>, token: SampleToken): List<Anchor?> {
    val before = words.filter { it.line == token.line && it.end <= token.labelStart && it.hasCore }
    val above = words.filter { it.line == token.line - 1 && it.line > TITLE_LINE && it.hasCore }
    val candidates = listOf(
        lastWords(text, before, 1),
        lastWords(text, before, 2),
        lastWords(text, above, 1),
        lastWords(text, above, 2),
    )
    val found = candidates.filterNotNull()
        .distinctBy(Anchor::text)
        .filter { it.text.length <= EXTRACT_MARKER_MAX_LENGTH }
    // A label with a digit is most likely a value itself ("Platform 1 15:40"), which the next
    // notification changes; it is tried only after no label at all, as the last way to read the sample.
    val (variable, fixed) = found.partition { anchor -> anchor.text.any(Char::isDigit) }
    return fixed + null + variable
}

private fun lastWords(text: String, words: List<SampleWord>, count: Int): Anchor? {
    val first = words.getOrNull(words.size - count) ?: return null
    val last = words.last()
    // "482913. Místo" crosses the end of a sentence; such a pair is no label.
    val crossesClause = first != last && (first.coreEnd until first.end).any { text[it] in CLAUSE_ENDS }
    return if (crossesClause) null else Anchor(text.substring(first.coreStart, last.coreEnd), last.coreEnd)
}

// A code or a word is best named after its label even without a colon ("platform 3"); an amount or
// a time after a plain word ("kartou 1 249 Kč") is better named after what it is.
private fun nameBase(text: String, candidate: Candidate, token: SampleToken): String {
    val anchor = candidate.anchor
    val kind = candidate.rule.kind
    val isLabel = anchor != null && (anchor.end until token.start).any { text[it] in LABEL_SEPARATORS }
    val named = anchor != null && (kind in NAMED_BY_LABEL || isLabel)
    val folded = if (named) foldExtractName(anchor.text.split(' ', '\n', '\t').last()) else ""
    return folded.ifEmpty { kind.jsonName }
}

// end is where the anchor stands in the sample, so nothing has to look it up again.
private class Anchor(val text: String, val end: Int)

// An until rule is meant to read its whole sentence, so a marker found inside the value does not pass
// for one that ends it.
private class Candidate(val rule: ExtractRule, val anchor: Anchor?, val meant: String) {
    fun reads(match: ExtractMatch?, start: Int): Boolean = readsTapped(rule, match, start, meant)
}

private const val PROBE = "probe"
private const val TITLE_LINE = 0
private const val LABEL_ENDS = ":="
private const val LABEL_SEPARATORS = ":=#-–—"
private const val CLAUSE_ENDS = ".,;!?"

private val NAMED_BY_LABEL = setOf(
    ExtractKind.NUMBER,
    ExtractKind.CODE,
    ExtractKind.WORD,
    ExtractKind.LINE,
    ExtractKind.UNTIL,
)

// Letters NFKD leaves whole, or splits into something other than their plain spelling ("ŀ" into
// "l·"), so stripping the marks alone would drop them from a name.
private val UNDECOMPOSED_LETTERS = mapOf(
    'ł' to "l",
    'ħ' to "h",
    'ŧ' to "t",
    'ŀ' to "l",
    'ĳ' to "ij",
    'đ' to "d",
    'ø' to "o",
    'ß' to "ss",
    'æ' to "ae",
    'œ' to "oe",
    'ı' to "i",
    'þ' to "th",
)
