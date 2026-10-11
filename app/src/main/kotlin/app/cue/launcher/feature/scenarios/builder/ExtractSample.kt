package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.AMOUNT_SIGNS
import app.cue.launcher.data.scenario.CURRENCIES
import app.cue.launcher.data.scenario.DIGIT_SIGNS
import app.cue.launcher.data.scenario.EXTRACT_MAX_INPUT
import app.cue.launcher.data.scenario.ExtractRule
import app.cue.launcher.data.scenario.NUMBER_SIGNS
import app.cue.launcher.data.scenario.digitGroupContinues

enum class SampleShape { TIME, DATE, AMOUNT, NUMBER, CODE, WORD }

// start and end bound the value a tap stands for; the label adds the punctuation glued to it, so
// the sample still reads as text. A token without a shape is punctuation and cannot be tapped.
data class SampleToken(
    val start: Int,
    val end: Int,
    val labelStart: Int,
    val labelEnd: Int,
    val line: Int,
    val shape: SampleShape?,
)

// tapped is what the rule is meant to read: the tapped value, or for an until rule the rest of its
// sentence. found is what the chosen rule reads from the sample; when reproduces is false it is not
// what was meant and the user has to adjust the rule. A rule that would only repeat an existing one
// is not added again: rule is then that existing rule and added is false.
data class InferredRule(
    val rule: ExtractRule,
    val added: Boolean,
    val tapped: String,
    val found: String?,
    val reproduces: Boolean,
)


internal data class SampleWord(
    val start: Int,
    val end: Int,
    val coreStart: Int,
    val coreEnd: Int,
    val line: Int,
    val amountStart: Int,
) {
    val hasCore: Boolean get() = coreEnd > coreStart

    fun valueStart(shape: SampleShape?): Int = if (shape == SampleShape.AMOUNT) amountStart else coreStart
}

// The evaluator only ever sees the start of a notification, so a value past the cut is not offered.
fun sampleTokens(sample: String): List<SampleToken> {
    val text = sample.take(EXTRACT_MAX_INPUT)
    return sampleWords(text).groupBy(SampleWord::line).values.flatMap { tokensOfLine(text, it) }
}

internal fun sampleWords(text: String): List<SampleWord> {
    val words = mutableListOf<SampleWord>()
    var line = 0
    var start = -1
    text.forEachIndexed { index, char ->
        val blank = char.isWhitespace()
        if (blank && start >= 0) {
            words += wordOf(text, start, index, line)
            start = -1
        }
        if (char == '\n') line++
        if (!blank && start < 0) start = index
    }
    if (start >= 0) words += wordOf(text, start, text.length, line)
    return words
}

// The core runs from the first to the last letter, digit or currency sign, so quotes, emoji and a
// trailing "%" stay in the label only; a minus touching the number is its sign. A dash or a colon
// standing alone separates a label from its value and has no core at all. An amount starts where the
// evaluator starts reading one, with any sign touching it: "+1 500 Kč", "–500 Kč", "-€5".
private fun wordOf(text: String, start: Int, end: Int, line: Int): SampleWord {
    fun isCore(index: Int) = text[index].isLetterOrDigit() || text[index] in CURRENCY_SIGNS
    val first = (start until end).firstOrNull(::isCore) ?: return SampleWord(start, end, start, start, line, start)
    val last = (start until end).last(::isCore)
    val signed = first > start && text[first - 1] in NUMBER_SIGNS && text[first].isDigit()
    // Only an amount starts at amountStart, and one whose core does not start with a digit starts
    // with its currency.
    val signs = if (text[first].isDigit()) DIGIT_SIGNS else AMOUNT_SIGNS
    val amountStart = if (first > start && text[first - 1] in signs) first - 1 else first
    return SampleWord(start, end, if (signed) first - 1 else first, last + 1, line, amountStart)
}


private fun tokensOfLine(text: String, words: List<SampleWord>): List<SampleToken> {
    val tokens = mutableListOf<SampleToken>()
    var index = 0
    while (index < words.size) {
        val group = groupAt(text, words, index)
        if (group != null) {
            tokens += group.first
            index = group.second + 1
        } else {
            tokens += singleToken(text, words[index])
            index++
        }
    }
    return tokens
}

// An amount, a date or a run of digit groups spans several words; it is offered as one token, as
// long as it ends where a word does and not halfway through one.
private fun groupAt(text: String, words: List<SampleWord>, index: Int): Pair<SampleToken, Int>? {
    val first = words[index]
    if (!first.hasCore) return null
    return GROUPS.firstNotNullOfOrNull { (pattern, shape) ->
        val end = pattern.matchAt(text, first.coreStart)?.range?.last?.plus(1) ?: return@firstNotNullOfOrNull null
        groupOf(words, index, end, shape)
    } ?: groupOf(words, index, numberGroupsEnd(text, first.coreStart), SampleShape.NUMBER)
}

private fun groupOf(words: List<SampleWord>, index: Int, end: Int, shape: SampleShape): Pair<SampleToken, Int>? {
    val first = words[index]
    val last = (index until words.size).firstOrNull { words[it].end >= end }
        ?.takeIf { end >= words[it].coreEnd }
        ?: return null
    return SampleToken(first.valueStart(shape), end, first.start, words[last].end, first.line, shape) to last
}

// The evaluator sees a run of blanks as one space, so the rule is asked at the last blank of the run.
private fun numberGroupsEnd(text: String, start: Int): Int {
    fun blank(index: Int) = text[index] != '\n' && text[index] != '\r' && text[index].isWhitespace()
    fun lastBlankFrom(from: Int): Int {
        var at = from
        while (at + 1 < text.length && blank(at) && blank(at + 1)) at++
        return at
    }
    if (text.getOrNull(start) !in '0'..'9') return start
    var index = digitsEnd(text, start)
    var gap = lastBlankFrom(index)
    while (digitGroupContinues(text, gap)) {
        index = digitsEnd(text, gap + 1)
        gap = lastBlankFrom(index)
    }
    return index
}

private fun digitsEnd(text: String, from: Int): Int {
    var index = from
    while (index < text.length && text[index] in '0'..'9') index++
    return index
}


private fun singleToken(text: String, word: SampleWord): SampleToken {
    val shape = if (word.hasCore) shapeOf(text.substring(word.coreStart, word.coreEnd)) else null
    return SampleToken(
        start = word.valueStart(shape),
        end = word.coreEnd,
        labelStart = word.start,
        labelEnd = word.end,
        line = word.line,
        shape = shape,
    )
}

internal fun shapeOf(core: String): SampleShape? = when {
    TIME_SHAPE.matches(core) -> SampleShape.TIME
    DATE_PATTERN.matches(core) -> SampleShape.DATE
    AMOUNT_PATTERN.matches(core) || BARE_AMOUNT.matches(core) || DECIMAL_SHAPE.matches(core) -> SampleShape.AMOUNT
    core.any(Char::isDigit) && core.none(Char::isLetter) -> SampleShape.NUMBER
    core.any(Char::isDigit) && core.all { it.isLetterOrDigit() || it == '-' } -> SampleShape.CODE
    core.any(Char::isLetterOrDigit) -> SampleShape.WORD
    else -> null
}

// These patterns only ever see the user's own sample in the builder; the rules that run on real
// notifications are evaluated without any regex.
// Any blank, since the evaluator folds every one into a plain space before it reads digit groups.
private const val BLANK = "\\p{Zs}\\t"
private const val GAP = "[$BLANK]"
private const val SIGN = "[$AMOUNT_SIGNS]"
private const val THOUSANDS = "\\d{1,3}(?:[$BLANK.,’']\\d{3})+"
private const val DECIMALS = "(?:[.,]\\d{1,2}|,[-–])"
private const val AMOUNT_NUMBER = "(?:$THOUSANDS|\\d+)$DECIMALS?"
private val CURRENCY = (CURRENCIES + "kč").joinToString(
    separator = "|",
    prefix = "(?<!\\p{L})(?:",
    postfix = ")(?!\\p{L})",
    transform = Regex::escape,
)

private val DATE_PATTERN =
    Regex("\\d{1,2}\\.$GAP?\\d{1,2}\\.(?:$GAP?\\d{4})?|\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}/\\d{1,2}/\\d{4}")
private val AMOUNT_PATTERN = Regex(
    "(?iu)$CURRENCY$GAP?$SIGN?$AMOUNT_NUMBER|$SIGN?$CURRENCY$GAP?$AMOUNT_NUMBER|$SIGN?$AMOUNT_NUMBER$GAP?$CURRENCY",
)
// Without a currency only the decimals tell a sum from digit groups like the code "482 913"; the
// evaluator reads such a sum when its rule has a label.
private val BARE_AMOUNT = Regex("$SIGN?$THOUSANDS$DECIMALS")
private val TIME_SHAPE = Regex("\\d{1,2}:\\d{2}(?::\\d{2})?")
private val DECIMAL_SHAPE = Regex("$SIGN?\\d+$DECIMALS")

private val GROUPS = listOf(
    DATE_PATTERN to SampleShape.DATE,
    AMOUNT_PATTERN to SampleShape.AMOUNT,
    BARE_AMOUNT to SampleShape.AMOUNT,
)

private const val CURRENCY_SIGNS = "€$£"
