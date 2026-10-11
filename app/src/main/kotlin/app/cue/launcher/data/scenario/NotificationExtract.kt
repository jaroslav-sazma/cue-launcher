@file:Suppress("TooManyFunctions")

package app.cue.launcher.data.scenario

import java.text.Normalizer
import java.time.Month
import java.time.Year

// Title and body together; a code or an amount sits near the start, and the cut bounds the walk the
// rules take on every matching notification.
const val EXTRACT_MAX_INPUT = 2000

const val EXTRACT_MAX_VALUE = 200

fun extractFields(rules: List<ExtractRule>, text: String): Map<String, String> =
    extractMatches(rules, text).mapValues { (_, match) -> match.value }

data class ExtractMatch(val value: String, val start: Int) {
    val end: Int get() = start + value.length
}

// Rules come from a scenario file, possibly someone else's, so evaluation is plain loops over a
// bounded text: no regex engine is ever handed anything a file controls. A rule the validation
// would refuse is skipped rather than trusted.
fun extractMatches(rules: List<ExtractRule>, text: String): Map<String, ExtractMatch> {
    val usable = rules.take(EXTRACT_MAX_RULES).let { capped ->
        capped.filterIndexed { index, rule -> extractProblem(rule, capped.subList(0, index)) == null }
    }
    if (usable.isEmpty()) return emptyMap()
    val haystack = FoldedText.of(text.take(EXTRACT_MAX_INPUT))
    return buildMap {
        usable.forEach { rule -> ExtractScan(haystack, rule).value()?.let { put(rule.name, it) } }
    }
}

// The form a label or a marker is matched in; empty means the text matches nothing of its own.
internal fun extractKey(text: String): String = FoldedText.key(text)

// Shared with the builder's sample tokenizer, so a number it offers is the number a rule reads. A
// dash before a digit joins; a space joins only a following thousands group, so "482 913" is one
// number while "482913 2 ks", "1234 12:30" and "12345 2026" are two.
internal fun digitGroupContinues(text: CharSequence, index: Int): Boolean = when {
    index !in text.indices || !digitIn(text, index + 1) -> false
    text[index] == '-' -> true
    isSpace(text[index]) -> thousandsGroupAt(text, index + 1)
    else -> false
}

private fun thousandsGroupAt(text: CharSequence, start: Int): Boolean {
    val end = start + THOUSANDS_DIGITS
    return (start until end).all { digitIn(text, it) } && !numberGoesOn(text, end)
}

// More digits, a time or a date: the group belongs to something else.
private fun numberGoesOn(text: CharSequence, index: Int): Boolean = when (text.getOrNull(index)) {
    null -> false
    ':' -> true
    '.', '/' -> digitIn(text, index + 1)
    else -> digitIn(text, index)
}

private fun digitIn(text: CharSequence, index: Int): Boolean = text.getOrNull(index)?.let(::isDigit) == true

private class FoldedText(val original: String, val folded: String, private val origins: IntArray) {

    val length: Int get() = folded.length

    operator fun get(index: Int): Char = folded[index]

    fun getOrNull(index: Int): Char? = folded.getOrNull(index)

    fun originOf(index: Int): Int = if (index < length) origins[index] else original.length

    // Combining marks are dropped and a run of blanks shrinks to one space, so the end maps to the
    // next kept character and a mark trailing the last character of the value stays with it.
    fun original(start: Int, end: Int): String {
        val from = origins[start]
        val to = if (end < length) origins[end] else original.length
        return original.substring(from, to)
    }

    companion object {
        fun of(text: String): FoldedText {
            val folded = StringBuilder(text.length)
            val origins = IntArray(text.length)
            text.forEachIndexed { index, char ->
                val kept = if (isSpace(char)) ' '.takeUnless { folded.lastOrNull() == ' ' } else fold(char)
                kept?.let {
                    origins[folded.length] = index
                    folded.append(it)
                }
            }
            return FoldedText(text, folded.toString(), origins)
        }

        fun key(text: String): String = of(text).folded.trim()
    }
}

private fun fold(char: Char): Char? = when {
    char.code < ASCII_LIMIT -> char.lowercaseChar()
    isMark(char) -> null
    char.isSurrogate() -> char
    else -> {
        val decomposed = Normalizer.normalize(char.toString(), Normalizer.Form.NFD)
        // Only a base letter followed by accents folds; Hangul and the like decompose into parts
        // that are letters themselves, and keeping just the first one would merge distinct syllables.
        val accented = decomposed.length > 1 && !isMark(decomposed[0]) &&
            (1 until decomposed.length).all { isMark(decomposed[it]) }
        val lower = (if (accented) decomposed[0] else char).lowercaseChar()
        if (lower == FINAL_SIGMA) SIGMA else lower
    }
}

private fun isMark(char: Char): Boolean = Character.getType(char) == Character.NON_SPACING_MARK.toInt()

private class ValueStart(val index: Int, val closers: String)

@Suppress("TooManyFunctions")
private class ExtractScan(private val text: FoldedText, private val rule: ExtractRule) {

    private val n = text.length
    private val anchor = rule.anchor?.let(FoldedText::key)?.takeIf(String::isNotEmpty)
    private val anchored = anchor != null
    private val marker = rule.marker?.let(FoldedText::key)?.takeIf(String::isNotEmpty)
    private var markerFrom = -1
    private var markerAt = -1

    fun value(): ExtractMatch? = if (anchor == null) firstValue() else anchoredValue(anchor)

    // Every occurrence of the label is tried in order; the first one directly followed by a value of
    // the kind wins. A later start is never smaller, so a missing marker ends the search for good.
    private fun anchoredValue(anchor: String): ExtractMatch? =
        generateSequence(occurrence(anchor, 0)) { at -> occurrence(anchor, at + 1) }
            .map { at -> valueStart(at + anchor.length) }
            .takeWhile { start -> rule.kind != ExtractKind.UNTIL || markerAfter(start.index) >= 0 }
            .firstNotNullOfOrNull { start -> valueAt(start.index, start.closers) }

    private fun occurrence(words: String, from: Int): Int? {
        var at = text.folded.indexOf(words, from)
        while (at >= 0 && !standsApart(words, at)) at = text.folded.indexOf(words, at + 1)
        return at.takeIf { it >= 0 }
    }

    // "od" must not be found at the end of "Kód", nor "order 12" at the start of "Order 1234"; a
    // letter meeting a digit is still a boundary, so "kód" does find "Kód123".
    private fun standsApart(words: String, at: Int): Boolean =
        !sameClass(words.first(), text.getOrNull(at - 1)) &&
            !sameClass(words.last(), text.getOrNull(at + words.length))

    private fun firstValue(): ExtractMatch? = when (rule.kind) {
        ExtractKind.UNTIL -> valueAt(0)
        ExtractKind.LINE -> firstLine()
        else -> (0 until n).asSequence().filter(::canStart).firstNotNullOfOrNull { valueAt(it) }
    }

    private fun canStart(index: Int): Boolean {
        val char = text[index]
        val previous = if (index > 0) text[index - 1] else ' '
        return when (rule.kind) {
            ExtractKind.NUMBER -> numberCanStart(index, previous)
            ExtractKind.TIME -> isDigit(char) && !isDigit(previous)
            ExtractKind.DATE -> dateCanStart(index, previous)
            ExtractKind.CODE -> char.isLetterOrDigit() && !previous.isLetterOrDigit() && previous != '-'
            ExtractKind.WORD -> char.isLetterOrDigit() && !previous.isLetterOrDigit()
            ExtractKind.AMOUNT -> amountCanStart(index, previous)
            ExtractKind.LINE, ExtractKind.UNTIL -> true
        }
    }

    private fun numberCanStart(index: Int, previous: Char): Boolean =
        (digitAt(index) && !isDigit(previous)) || numberSignAt(index)

    // "1.2.3." is a version, so no date starts at its "2" or "3".
    private fun dateCanStart(index: Int, previous: Char): Boolean =
        digitAt(index) && !isDigit(previous) && !(previous == '.' && digitAt(index - 2))

    // In "123-500 Kč" the dash joins two numbers rather than signing the second.
    private fun amountCanStart(index: Int, previous: Char): Boolean =
        (signAt(index) && !previous.isLetterOrDigit()) || (digitAt(index) && !isDigit(previous)) ||
            currencyAt(index) > 0

    private fun valueAt(start: Int, closers: String = ""): ExtractMatch? = when (rule.kind) {
        ExtractKind.UNTIL -> untilValue(start, closers)
        ExtractKind.LINE -> lineValue(start, closers)
        else -> {
            val end = if (start < n) tokenEnd(start) else -1
            if (end > start) matchOf(start, text.original(start, end)) else null
        }
    }

    // The value is the raw text trimmed at both ends and cut to length, so it is always a prefix of
    // the raw text once its leading blanks are skipped.
    private fun matchOf(start: Int, raw: String): ExtractMatch? {
        val value = cappedExtractValue(raw) ?: return null
        return ExtractMatch(value, text.originOf(start) + raw.indexOf(value))
    }

    private fun tokenEnd(start: Int): Int = when (rule.kind) {
        ExtractKind.NUMBER -> numberEnd(start)
        ExtractKind.CODE -> codeEnd(start)
        ExtractKind.AMOUNT -> amountEnd(start)
        ExtractKind.TIME -> timeEnd(start)
        ExtractKind.DATE -> dateEnd(start)
        else -> wordEnd(start)
    }

    // One dot right after the label is an abbreviation ("tel. 123"). The opening quotes skipped on
    // the way are remembered, so a line or an until value can drop the matching closing one.
    private fun valueStart(from: Int): ValueStart {
        var index = if (charIs(from, '.')) from + 1 else from
        var lineBroken = false
        val closers = StringBuilder()
        while (index < n) {
            val length = separatorLength(index, lineBroken)
            if (length == 0) break
            lineBroken = lineBroken || isLineBreak(text[index])
            OPENERS[text[index]]?.let(closers::append)
            index += length
        }
        return ValueStart(index, closers.toString())
    }

    // A value often sits on the line under its label, so one line break is skipped; a second one
    // means the label stood alone and the value belongs to something else.
    private fun separatorLength(index: Int, lineBroken: Boolean): Int {
        val char = text[index]
        return when {
            rule.kind == ExtractKind.AMOUNT && signAt(index) -> 0
            rule.kind == ExtractKind.NUMBER && numberSignAt(index) -> 0
            isSpace(char) || char in SEPARATORS || char in OPENERS -> 1
            lineBroken || !isLineBreak(char) -> 0
            char == '\r' && charIs(index + 1, '\n') -> 2
            else -> 1
        }
    }

    // A sign touches the number, or a currency in front of it: "-350", "−350", "-€5". A dash with
    // a space after it is a separator, as in "Částka – 500 Kč", and an en dash only signs a digit.
    private fun signAt(index: Int): Boolean = when {
        index !in 0 until n -> false
        text[index] in AMOUNT_SIGNS -> digitAt(index + 1) || currencyBeforeDigit(index + 1)
        text[index] == EN_DASH -> digitAt(index + 1)
        else -> false
    }

    private fun currencyBeforeDigit(index: Int): Boolean {
        val currency = currencyAt(index)
        val after = index + currency
        return currency > 0 && digitAt(if (groupSpaceAt(after)) after + 1 else after)
    }

    // A minus touching the digits is part of the number ("Teplota -5 °C"), unless it hangs on a word
    // or a number before it ("R-1234", "12-5"); one between spaces is a separator ("Kód - 1234").
    private fun numberSignAt(index: Int): Boolean =
        charIn(index, NUMBER_SIGNS) && digitAt(index + 1) && !letterOrDigitAt(index - 1)

    private fun numberEnd(start: Int): Int {
        val digits = if (numberSignAt(start)) start + 1 else start
        if (!digitAt(digits)) return -1
        var index = digitsEnd(digits)
        while (digitGroupContinues(text.folded, index)) index = digitsEnd(index + 1)
        return index
    }

    private fun codeEnd(start: Int): Int {
        if (!text[start].isLetterOrDigit()) return -1
        var index = start
        var hasDigit = false
        while (index < n && (text[index].isLetterOrDigit() || text[index] == '-')) {
            if (isDigit(text[index])) hasDigit = true
            index++
        }
        while (index > start && text[index - 1] == '-') index--
        return if (hasDigit) index else -1
    }

    // An amount followed by ".10" or "/10" is the start of a date, not money.
    @Suppress("ReturnCount")
    private fun amountEnd(start: Int): Int {
        val signed = signAt(start)
        val prefix = currencyAt(if (signed) start + 1 else start)
        val digits = amountDigitsStart(start, signed, prefix)
        if (!digitAt(digits)) return -1
        val numberEnd = amountDigitsEnd(digits)
        if ((charIs(numberEnd, '.') || charIs(numberEnd, '/')) && digitAt(numberEnd + 1)) return -1
        val suffixStart = if (groupSpaceAt(numberEnd)) numberEnd + 1 else numberEnd
        val suffix = if (prefix > 0) 0 else currencyAt(suffixStart)
        return when {
            suffix > 0 -> suffixStart + suffix
            prefix > 0 || anchored -> numberEnd
            else -> -1
        }
    }

    // The sign may stand on either side of a currency in front: "-€5" and "€-5" alike.
    private fun amountDigitsStart(start: Int, signed: Boolean, prefix: Int): Int {
        var index = if (signed) start + 1 else start
        if (prefix == 0) return index
        index += prefix
        if (groupSpaceAt(index)) index++
        if (!signed && index < n && text[index] in AMOUNT_SIGNS) index++
        return index
    }

    // Exactly three digits after a separator continue the thousands; one or two after a comma or a
    // dot are the decimals and end the number, and so does the ",-" of a whole Czech price.
    private fun amountDigitsEnd(from: Int): Int {
        var index = digitsEnd(from)
        var open = true
        while (open && index + 1 < n && text[index] in AMOUNT_GROUPS) {
            val decimal = text[index] in DECIMAL_MARKS
            val groupEnd = digitsEnd(index + 1)
            val digits = groupEnd - index - 1
            open = digits == THOUSANDS_DIGITS
            index = when {
                open -> groupEnd
                decimal && digits in 1..2 -> groupEnd
                decimal && digits == 0 && text[index + 1] in WHOLE_DASHES -> index + 2
                else -> index
            }
        }
        return index
    }

    private fun currencyAt(index: Int): Int {
        val code = CURRENCIES.firstOrNull { text.folded.startsWith(it, index) } ?: return 0
        val standsAlone = !code[0].isLetter() || (!letterAt(index - 1) && !letterAt(index + code.length))
        return if (standsAlone) code.length else 0
    }

    @Suppress("ReturnCount")
    private fun timeEnd(start: Int): Int {
        val hourEnd = digitsEnd(start)
        if (hourEnd - start !in 1..2 || !charIs(hourEnd, ':')) return -1
        val minuteEnd = digitsEnd(hourEnd + 1)
        if (minuteEnd - hourEnd - 1 != 2) return -1
        if (number(start, hourEnd) > MAX_HOUR || number(hourEnd + 1, minuteEnd) > MAX_MINUTE) return -1
        var end = minuteEnd
        if (charIs(end, ':')) {
            val secondEnd = digitsEnd(end + 1)
            if (secondEnd - end - 1 == 2 && number(end + 1, secondEnd) <= MAX_MINUTE) end = secondEnd
        }
        return if (digitAt(end)) -1 else end
    }

    private fun dateEnd(start: Int): Int {
        val firstEnd = digitsEnd(start)
        return when (firstEnd - start) {
            YEAR_DIGITS -> isoDateEnd(start, firstEnd)
            1, 2 -> when {
                charIs(firstEnd, '.') -> dottedDateEnd(start, firstEnd)
                charIs(firstEnd, '/') -> slashedDateEnd(start, firstEnd)
                else -> -1
            }
            else -> -1
        }
    }

    @Suppress("ReturnCount")
    private fun isoDateEnd(start: Int, yearEnd: Int): Int {
        if (!charIs(yearEnd, '-')) return -1
        val monthEnd = digitsEnd(yearEnd + 1)
        if (monthEnd - yearEnd - 1 !in 1..2 || !charIs(monthEnd, '-')) return -1
        val dayEnd = digitsEnd(monthEnd + 1)
        if (dayEnd - monthEnd - 1 !in 1..2) return -1
        val valid = validDate(number(monthEnd + 1, dayEnd), number(yearEnd + 1, monthEnd), number(start, yearEnd))
        return if (valid) dayEnd else -1
    }

    // "5. 10." stands without a year, "5.10.2026" carries one, and "5.10.26" is refused rather than
    // cut short to "5.10.".
    @Suppress("ReturnCount")
    private fun dottedDateEnd(start: Int, dayEnd: Int): Int {
        val monthStart = if (groupSpaceAt(dayEnd + 1)) dayEnd + 2 else dayEnd + 1
        val monthEnd = digitsEnd(monthStart)
        if (monthEnd - monthStart !in 1..2 || !charIs(monthEnd, '.')) return -1
        val afterMonth = monthEnd + 1
        val yearStart = if (groupSpaceAt(afterMonth)) afterMonth + 1 else afterMonth
        val yearEnd = digitsEnd(yearStart)
        val hasYear = yearEnd - yearStart == YEAR_DIGITS
        if (!hasYear && yearStart == afterMonth && yearEnd > yearStart) return -1
        val year = if (hasYear) number(yearStart, yearEnd) else null
        if (!validDate(number(start, dayEnd), number(monthStart, monthEnd), year)) return -1
        return if (hasYear) yearEnd else afterMonth
    }

    @Suppress("ReturnCount")
    private fun slashedDateEnd(start: Int, dayEnd: Int): Int {
        val monthEnd = digitsEnd(dayEnd + 1)
        if (monthEnd - dayEnd - 1 !in 1..2 || !charIs(monthEnd, '/')) return -1
        val yearEnd = digitsEnd(monthEnd + 1)
        if (yearEnd - monthEnd - 1 != YEAR_DIGITS) return -1
        val valid = validDate(number(start, dayEnd), number(dayEnd + 1, monthEnd), number(monthEnd + 1, yearEnd))
        return if (valid) yearEnd else -1
    }

    private fun wordEnd(start: Int): Int {
        if (!text[start].isLetterOrDigit()) return -1
        var index = start + 1
        while (index < n && (text[index].isLetterOrDigit() || joinsWord(index))) index++
        return index
    }

    private fun joinsWord(index: Int): Boolean =
        text[index] in WORD_JOINERS && index + 1 < n && text[index + 1].isLetterOrDigit()

    private fun lineValue(start: Int, closers: String = ""): ExtractMatch? {
        val end = lineEnd(start)
        if (end == start) return null
        val trimmed = text.original(start, end).trimEnd { it in LINE_TRAILERS }
        return matchOf(start, unquoted(trimmed, closers).trimEnd { it in LINE_TRAILERS })
    }

    private fun firstLine(): ExtractMatch? =
        generateSequence(0) { start -> (lineEnd(start) + 1).takeIf { it < n } }
            .takeWhile { it < n }
            .firstNotNullOfOrNull { lineValue(it) }

    private fun lineEnd(start: Int): Int {
        var end = start
        while (end < n && !isLineBreak(text[end])) end++
        return end
    }

    private fun untilValue(start: Int, closers: String): ExtractMatch? {
        val end = markerAfter(start)
        if (end <= start) return null
        val trimmed = text.original(start, end).trimEnd { it in LINE_TRAILERS }
        return matchOf(start, unquoted(trimmed, closers).trimEnd { it in LINE_TRAILERS })
    }

    // Matched as whole words like the label, so "Do" does not end a value inside "Dobrovského".
    // Starts only grow, so the first marker after the previous start is still the first one after
    // this start unless this start has already passed it.
    private fun markerAfter(start: Int): Int {
        val wanted = marker ?: return -1
        if (markerFrom < 0 || start < markerFrom || markerAt in 0 until start) {
            markerAt = occurrence(wanted, start) ?: -1
            markerFrom = start
        }
        return markerAt
    }

    private fun digitsEnd(from: Int): Int {
        var index = from
        while (digitAt(index)) index++
        return index
    }

    private fun number(start: Int, end: Int): Int = text.folded.substring(start, end).toInt()

    private fun charIs(index: Int, char: Char): Boolean = index in 0 until n && text[index] == char

    private fun digitAt(index: Int): Boolean = index in 0 until n && isDigit(text[index])

    private fun letterAt(index: Int): Boolean = index in 0 until n && text[index].isLetter()

    private fun letterOrDigitAt(index: Int): Boolean = index in 0 until n && text[index].isLetterOrDigit()

    private fun charIn(index: Int, chars: String): Boolean = index in 0 until n && text[index] in chars

    private fun groupSpaceAt(index: Int): Boolean = index in 0 until n && isGroupSpace(text[index])
}

// Scripts written without spaces between words have no boundary to look for, so a label there
// matches inside a run of letters.
private fun sameClass(edge: Char, neighbour: Char?): Boolean = when {
    neighbour == null -> false
    edge.isDigit() -> neighbour.isDigit()
    edge.isLetter() -> neighbour.isLetter() && spacedScript(edge) && spacedScript(neighbour)
    else -> false
}

private fun spacedScript(char: Char): Boolean =
    char.isSurrogate() || Character.UnicodeScript.of(char.code) !in UNSPACED_SCRIPTS

private fun unquoted(value: String, closers: String): String {
    val trimmed = value.trimEnd()
    return if (trimmed.isNotEmpty() && trimmed.last() in closers) trimmed.dropLast(1) else value
}

// A cut through a surrogate pair would leave half a character behind. Shared with the builder, so a
// long value it expects is cut the way a rule cuts the one it reads.
internal fun cappedExtractValue(value: String): String? {
    val cut = value.trim().take(EXTRACT_MAX_VALUE)
    val whole = if (cut.lastOrNull()?.isHighSurrogate() == true) cut.dropLast(1) else cut
    return whole.trimEnd().takeIf(String::isNotEmpty)
}

private fun validDate(day: Int, month: Int, year: Int?): Boolean =
    month in 1..MAX_MONTH && day in 1..Month.of(month).length(year == null || Year.isLeap(year.toLong()))

private fun isDigit(char: Char): Boolean = char in '0'..'9'

private fun isLineBreak(char: Char): Boolean = char == '\n' || char == '\r'

private fun isSpace(char: Char): Boolean =
    char == '\t' || Character.getType(char) == Character.SPACE_SEPARATOR.toInt()

private fun isGroupSpace(char: Char): Boolean = char in GROUP_SPACES

private const val ASCII_LIMIT = 0x80
private const val THOUSANDS_DIGITS = 3
private const val YEAR_DIGITS = 4
private const val MAX_HOUR = 23
private const val MAX_MINUTE = 59
private const val MAX_MONTH = 12
private const val NO_BREAK_SPACE = 0x00A0
private const val NARROW_NO_BREAK_SPACE = 0x202F
private const val FINAL_SIGMA = 'ς'
private const val SIGMA = 'σ'
private const val EN_DASH = '–'

private val GROUP_SPACES = String(charArrayOf(' ', Char(NO_BREAK_SPACE), Char(NARROW_NO_BREAK_SPACE)))
private const val SEPARATORS = ":-–—#="
private val AMOUNT_GROUPS = "$GROUP_SPACES.,’'"
private const val DECIMAL_MARKS = ".,"
private const val WHOLE_DASHES = "-–"
private const val WORD_JOINERS = "-'’._"
private val LINE_TRAILERS = ".,;:\t$GROUP_SPACES"

// An opening quote or bracket between the label and its value, with the closing ones it may get.
private val OPENERS = mapOf(
    '„' to "“”", '“' to "”“", '"' to "\"", '\'' to "'", '‚' to "‘’", '‘' to "’‘", '«' to "»", '(' to ")", '[' to "]",
)

private val UNSPACED_SCRIPTS = setOf(
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA,
    Character.UnicodeScript.THAI,
    Character.UnicodeScript.LAO,
    Character.UnicodeScript.KHMER,
    Character.UnicodeScript.MYANMAR,
)

// Shared with the builder's sample tokenizer, so a number or an amount it offers starts where a rule
// starts reading it. These sign an amount's digit or currency, and an en dash signs a digit only; a
// plain number takes a minus only, so "+420" stays a phone number and "+5 bodů" a count.
internal const val AMOUNT_SIGNS = "-−+"
internal const val DIGIT_SIGNS = "$AMOUNT_SIGNS$EN_DASH"
internal const val NUMBER_SIGNS = "-−"

// Folded: lowercase and without diacritics, so "kc" also stands for Kč and Kc.
internal val CURRENCIES = listOf(
    "kc", "czk", "€", "eur", "$", "usd", "£", "gbp", "zł", "pln", "ft", "huf", "chf",
)
