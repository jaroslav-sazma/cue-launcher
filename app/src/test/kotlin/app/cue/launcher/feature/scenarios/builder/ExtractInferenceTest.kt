package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EXTRACT_MAX_VALUE
import app.cue.launcher.data.scenario.ExtractKind
import app.cue.launcher.data.scenario.ExtractRule
import app.cue.launcher.data.scenario.extractFields
import app.cue.launcher.data.scenario.extractMatches
import app.cue.launcher.engine.PostedNotification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val PARCEL = "Vaše zásilka je připravena. Výdejní kód: 482913. Místo: Hlavní 5, Praha"
private const val PAYMENT = "Platba kartou 1 249,00 Kč v obchodě Albert"
private const val TRAIN = "Train R 1234 departs 14:05 from platform 3"

class SampleTokensTest {

    @Test
    fun `words keep their punctuation in the label but not in the value`() {
        val code = token(PARCEL, "482913")
        assertEquals("482913.", PARCEL.substring(code.labelStart, code.labelEnd))
        assertEquals(SampleShape.NUMBER, code.shape)
        val label = token(PARCEL, "kód")
        assertEquals("kód:", PARCEL.substring(label.labelStart, label.labelEnd))
        assertEquals(SampleShape.WORD, label.shape)
    }

    @Test
    fun `an amount with its currency is one token on either side`() {
        assertEquals(SampleShape.AMOUNT, token(PAYMENT, "1 249,00 Kč").shape)
        assertEquals(SampleShape.AMOUNT, token("Zaplaceno € 12,50 dnes", "€ 12,50").shape)
        assertEquals(SampleShape.AMOUNT, token("Total: $5.99", "$5.99").shape)
        assertEquals(SampleShape.AMOUNT, token("Cena 1 249,- Kč", "1 249,- Kč").shape)
    }

    @Test
    fun `an amount without a currency is one token when its decimals show it`() {
        assertEquals(SampleShape.AMOUNT, token("Zůstatek: 12 345,67", "12 345,67").shape)
        assertEquals(SampleShape.AMOUNT, token("Zůstatek: 1.234,50", "1.234,50").shape)
        assertEquals(SampleShape.NUMBER, token("Kód 482 913 platí", "482 913").shape)
    }

    @Test
    fun `digit groups split by any blank are one token like the evaluator reads them`() {
        assertEquals(SampleShape.AMOUNT, token("Zůstatek: 12\u2009345,67 Kč", "12\u2009345,67 Kč").shape)
        assertEquals(SampleShape.AMOUNT, token("Zůstatek: 12\u2009345,67", "12\u2009345,67").shape)
        assertEquals(SampleShape.NUMBER, token("Kód 482\u2009913 platí", "482\u2009913").shape)
    }

    @Test
    fun `a sign touching an amount starts the token as it starts the value`() {
        assertEquals(SampleShape.AMOUNT, token("Příchozí platba +1 500,00 Kč", "+1 500,00 Kč").shape)
        assertEquals(SampleShape.AMOUNT, token("Vráceno –500 Kč", "–500 Kč").shape)
        assertEquals(SampleShape.AMOUNT, token("Platba -€5 dnes", "-€5").shape)
        assertEquals(SampleShape.AMOUNT, token("Platba -CZK 500 dnes", "-CZK 500").shape)
        assertEquals(SampleShape.AMOUNT, token("Zůstatek: +12,50", "+12,50").shape)
        assertEquals(SampleShape.AMOUNT, token("Částka – 500 Kč", "500 Kč").shape)
    }

    @Test
    fun `a date and a time are one token each`() {
        assertEquals(SampleShape.DATE, token("Doručíme 5. 10. 2026 dopoledne", "5. 10. 2026").shape)
        assertEquals(SampleShape.DATE, token("Due 2026-10-05.", "2026-10-05").shape)
        assertEquals(SampleShape.DATE, token("Termín 5.10.", "5.10.").shape)
        assertEquals(SampleShape.TIME, token(TRAIN, "14:05").shape)
    }

    @Test
    fun `digit groups stay together the way the evaluator reads them`() {
        assertEquals(SampleShape.NUMBER, token("Kód 482 913 platí do pátku", "482 913").shape)
        assertEquals(SampleShape.NUMBER, token("Kód 482\u00A0 913 platí", "482\u00A0 913").shape)
    }

    @Test
    fun `a space joins only a following thousands group`() {
        assertEquals(listOf("Sleva", "1", "1. 10."), values("Sleva 1 1. 10."))
        assertEquals(listOf("Cena", "10", "10 €"), values("Cena 10 10 €"))
        assertEquals(listOf("Platform", "1", "15:40"), values("Platform 1 15:40"))
        assertEquals(listOf("Objednávka", "482913", "2", "ks"), values("Objednávka 482913 2 ks"))
    }

    @Test
    fun `emoji and quotes around a word stay out of its value`() {
        val sleva = token("🎉Sleva dnes", "Sleva")
        assertEquals(SampleShape.WORD, sleva.shape)
        assertEquals("🎉Sleva", "🎉Sleva dnes".substring(sleva.labelStart, sleva.labelEnd))
        assertEquals(SampleShape.WORD, token("Heslo „Jaro“ platí", "Jaro").shape)
        assertEquals(SampleShape.NUMBER, token("Sleva 50% dnes", "50").shape)
        assertEquals(SampleShape.AMOUNT, token("Vráceno -249 Kč", "-249 Kč").shape)
    }

    @Test
    fun `only the token a value starts in counts as pulled`() {
        val rule = ExtractRule(name = "misto", after = "Místo", kind = ExtractKind.LINE)
        val matches = extractMatches(listOf(rule), PARCEL)
        assertEquals(TokenPull.PULLED, tokenPull(token(PARCEL, "Hlavní"), matches))
        assertEquals(setOf("misto"), rulesStartingIn(token(PARCEL, "Hlavní"), matches))
        assertEquals(TokenPull.INSIDE, tokenPull(token(PARCEL, "5"), matches))
        assertEquals(TokenPull.INSIDE, tokenPull(token(PARCEL, "Praha"), matches))
        assertEquals(emptySet<String>(), rulesStartingIn(token(PARCEL, "Praha"), matches))
        assertEquals(TokenPull.FREE, tokenPull(token(PARCEL, "482913"), matches))
    }

    @Test
    fun `letters mixed with digits make a code`() {
        assertEquals(SampleShape.CODE, token("Your code A7K-29Q is ready", "A7K-29Q").shape)
    }

    @Test
    fun `a lone separator cannot be tapped`() {
        val dash = sampleTokens("Kód – 482913").single { it.labelEnd - it.labelStart == 1 }
        assertNull(dash.shape)
    }

    @Test
    fun `tokens carry the line they stand on`() {
        val tokens = sampleTokens("Zásilkovna\n$PARCEL")
        assertEquals(0, tokens.first().line)
        assertEquals(1, token("Zásilkovna\n$PARCEL", "482913").line)
    }
}

class InferExtractRuleTest {

    @Test
    fun `a value that runs on into the next sentence ends with its own`() {
        val sample = "Zásilkovna\nMísto: Hlavní 5, Praha. Vyzvednout můžete do pátku."
        val inferred = infer(sample, "Hlavní")
        assertEquals(ExtractRule(name = "misto", after = "Místo", kind = ExtractKind.UNTIL, until = "."), inferred.rule)
        assertEquals("Hlavní 5, Praha", inferred.found)
    }

    @Test
    fun `an abbreviation dot inside the value makes the next sentence the marker`() {
        val sample = "Zásilkovna\nMísto: nám. Míru 5, Praha. Vyzvednout můžete do pátku."
        val inferred = infer(sample, "nám")
        assertEquals("Vyzvednout", inferred.rule.until)
        assertEquals("nám. Míru 5, Praha", inferred.found)
    }

    @Test
    fun `the documented example past an abbreviation ends at the next sentence`() {
        val sample = "Parcel\nPickup point: unit no. 5, Springfield. Collect it by Friday."
        val inferred = infer(sample, "unit")
        assertEquals("Collect", inferred.rule.until)
        assertEquals("unit no. 5, Springfield", inferred.found)
    }

    // "Na" opens the next sentence but also stands inside the value; a marker that cuts the value
    // short there is not offered as one that ends it.
    @Test
    fun `a next sentence word found inside the value takes more words of it`() {
        val sample = "Zásilkovna\nVýdejní místo: ul. Na Poříčí 5, Praha. Na shledanou"
        val inferred = infer(sample, "ul")
        assertEquals("Na shledanou", inferred.rule.until)
        assertEquals("ul. Na Poříčí 5, Praha", inferred.found)
        assertEquals("ul. Na Poříčí 5, Praha", inferred.tapped)
        assertTrue(inferred.reproduces)
    }

    @Test
    fun `a marker word is not found inside a longer word`() {
        val sample = "Zásilkovna\nVýdejní místo: ul. Dobrovského 12, Brno. Do 10. 10."
        val inferred = infer(sample, "ul")
        assertEquals("Do", inferred.rule.until)
        assertEquals("ul. Dobrovského 12, Brno", inferred.found)
        assertTrue(inferred.reproduces)
        assertEquals("Údolní 3, Brno", extract(inferred.rule, "Zásilkovna\nVýdejní místo: Údolní 3, Brno. Do 12. 10."))
    }

    @Test
    fun `a number in the next sentence is never part of a marker`() {
        val sample = "Zásilkovna\nVýdejní místo: ul. Do Koutů 5, Praha. Do 12. 10. ji vyzvedněte"
        val inferred = infer(sample, "ul")
        assertEquals(ExtractKind.LINE, inferred.rule.kind)
        assertTrue(inferred.reproduces)
    }

    @Test
    fun `a sentence longer than a value can be is still offered as until its end`() {
        val street = "Hlavní " + "dlouhá ".repeat(40) + "5, Praha"
        val inferred = infer("Zásilkovna\nMísto: $street. Vyzvednout můžete do pátku.", "Hlavní")
        assertEquals(ExtractKind.UNTIL, inferred.rule.kind)
        assertEquals(".", inferred.rule.until)
        assertEquals(EXTRACT_MAX_VALUE, inferred.found?.length)
        assertTrue(inferred.reproduces)
    }

    @Test
    fun `a value that ends its line stays a line`() {
        assertEquals(ExtractKind.LINE, infer("Zásilkovna\nMísto: Hlavní 5, Praha.", "Hlavní").rule.kind)
    }

    @Test
    fun `a code after its label is a number named after the label`() {
        val inferred = infer(PARCEL, "482913")
        assertEquals(ExtractRule(name = "kod", after = "kód", kind = ExtractKind.NUMBER), inferred.rule)
        assertTrue(inferred.reproduces)
        assertEquals("482913", inferred.found)
    }

    @Test
    fun `a word after a label takes the rest of the line`() {
        val inferred = infer(PARCEL, "Hlavní")
        assertEquals(ExtractRule(name = "misto", after = "Místo", kind = ExtractKind.LINE), inferred.rule)
        assertEquals("Hlavní 5, Praha", inferred.found)
        assertTrue(inferred.reproduces)
    }

    @Test
    fun `the same works with the notification title on the line above`() {
        val sample = "Zásilkovna\n$PARCEL"
        assertEquals("482913", extract(infer(sample, "482913").rule, sample))
        assertEquals("Hlavní 5, Praha", extract(infer(sample, "Hlavní").rule, sample))
    }

    @Test
    fun `an amount after a plain word is named after its kind`() {
        val inferred = infer(PAYMENT, "1 249,00 Kč")
        assertEquals(ExtractKind.AMOUNT, inferred.rule.kind)
        assertEquals("amount", inferred.rule.name)
        assertEquals("kartou", inferred.rule.after)
        assertTrue(inferred.reproduces)
    }

    @Test
    fun `an amount reads the same with or without its anchor`() {
        val anchorless = ExtractRule(name = "amount", kind = ExtractKind.AMOUNT)
        assertEquals("1 249,00 Kč", extract(anchorless, PAYMENT))
        val first = infer("1 249,00 Kč zaplaceno kartou", "1 249,00 Kč")
        assertEquals(ExtractRule(name = "amount", kind = ExtractKind.AMOUNT), first.rule)
        assertTrue(first.reproduces)
    }

    @Test
    fun `an amount after a label is named after the label`() {
        val inferred = infer("Částka k úhradě: 1 249,00 Kč", "1 249,00 Kč")
        assertEquals("uhrade", inferred.rule.name)
        assertEquals(ExtractKind.AMOUNT, inferred.rule.kind)
    }

    @Test
    fun `a time after a verb is a time named time`() {
        val inferred = infer(TRAIN, "14:05")
        assertEquals(ExtractRule(name = "time", after = "departs", kind = ExtractKind.TIME), inferred.rule)
        assertTrue(inferred.reproduces)
    }

    @Test
    fun `a bare number after a word is a number named after the word`() {
        val inferred = infer(TRAIN, "3")
        assertEquals(ExtractRule(name = "platform", after = "platform", kind = ExtractKind.NUMBER), inferred.rule)
        assertTrue(inferred.reproduces)
    }

    @Test
    fun `a single letter anchor still lands on the right occurrence`() {
        val inferred = infer(TRAIN, "1234")
        assertEquals("1234", inferred.found)
        assertTrue(inferred.reproduces)
    }

    @Test
    fun `a code with letters and digits is a code`() {
        val inferred = infer("Your code A7K-29Q is ready", "A7K-29Q")
        assertEquals(ExtractRule(name = "code", after = "code", kind = ExtractKind.CODE), inferred.rule)
        assertTrue(inferred.reproduces)
    }

    @Test
    fun `a date is a date`() {
        val inferred = infer("Doručíme 5. 10. 2026 dopoledne", "5. 10. 2026")
        assertEquals(ExtractKind.DATE, inferred.rule.kind)
        assertEquals("date", inferred.rule.name)
        assertTrue(inferred.reproduces)
    }

    @Test
    fun `a token at the very start has no anchor`() {
        val inferred = infer("482913 is your code", "482913")
        assertEquals(ExtractRule(name = "number", kind = ExtractKind.NUMBER), inferred.rule)
        assertTrue(inferred.reproduces)
    }

    @Test
    fun `a word at the very start is a word, not a line`() {
        val inferred = infer(PAYMENT, "Platba")
        assertEquals(ExtractRule(name = "word", kind = ExtractKind.WORD), inferred.rule)
        assertTrue(inferred.reproduces)
    }

    @Test
    fun `a word in the middle of a sentence stays a word`() {
        val inferred = infer(PAYMENT, "Albert")
        assertEquals(ExtractKind.WORD, inferred.rule.kind)
        assertEquals("obchodě", inferred.rule.after)
        assertEquals("obchode", inferred.rule.name)
        assertTrue(inferred.reproduces)
    }

    @Test
    fun `a line under its label on the line above uses that label`() {
        val sample = "Zásilkovna\nVýdejní místo\nHlavní 5, Praha"
        val inferred = infer(sample, "Hlavní")
        assertEquals(ExtractKind.LINE, inferred.rule.kind)
        assertEquals("místo", inferred.rule.after)
        assertEquals("Hlavní 5, Praha", inferred.found)
    }

    @Test
    fun `a value under its label with a colon uses that label`() {
        val inferred = infer("Banka\nKód:\n482913", "482913")
        assertEquals(ExtractRule(name = "kod", after = "Kód", kind = ExtractKind.NUMBER), inferred.rule)
        assertTrue(inferred.reproduces)
    }

    @Test
    fun `the title line never labels a value below it`() {
        val greeting = infer("Jan Novák\nAhoj, jak se máš?", "Ahoj")
        assertNull(greeting.rule.after)
        assertFalse(greeting.reproduces)
        val code = infer("Jan Novák\n482913", "482913")
        assertNull(code.rule.after)
        assertTrue(code.reproduces)
        assertEquals("Kód", infer("Jan Novák\nKód 1234", "1234").rule.after)
    }

    @Test
    fun `a time after a number names itself and does not crash`() {
        val platform = infer("Platform 1 15:40", "15:40")
        assertEquals(ExtractKind.TIME, platform.rule.kind)
        assertEquals("time", platform.rule.name)
        assertTrue(platform.reproduces)
        val nastupiste = infer("Nástupiště 2 21:05", "21:05")
        assertEquals("21:05", nastupiste.found)
        assertTrue(nastupiste.reproduces)
        assertEquals("odjezd", infer("Odjezd: 21:05", "21:05").rule.name)
    }

    // The number before the time is a value of the next notification too, so it makes no label.
    @Test
    fun `a label with a digit comes only after no label at all`() {
        val platform = infer("Platform 1 15:40", "15:40")
        assertNull(platform.rule.after)
        assertEquals("16:10", extract(platform.rule, "Platform 2 16:10"))
        val second = infer("14:05 – 15:30", "15:30")
        assertEquals("14:05", second.rule.after)
        assertTrue(second.reproduces)
    }

    @Test
    fun `an amount without a currency is read after its label`() {
        val inferred = infer("Zůstatek: 12 345,67", "12 345,67")
        assertEquals(ExtractRule(name = "zustatek", after = "Zůstatek", kind = ExtractKind.AMOUNT), inferred.rule)
        assertTrue(inferred.reproduces)
        assertEquals("8 901,23", extract(inferred.rule, "Zůstatek: 8 901,23"))
    }

    @Test
    fun `a negative number reads with its minus`() {
        val inferred = infer("Počasí\nTeplota -5 °C", "-5")
        assertEquals(ExtractRule(name = "teplota", after = "Teplota", kind = ExtractKind.NUMBER), inferred.rule)
        assertTrue(inferred.reproduces)
        assertEquals("−12", extract(inferred.rule, "Počasí\nTeplota −12 °C"))
    }

    @Test
    fun `an amount split by a thin space reads whole`() {
        val sample = "Banka\nZůstatek: 12\u2009345,67 Kč"
        val inferred = infer(sample, "12\u2009345,67 Kč")
        assertEquals(ExtractKind.AMOUNT, inferred.rule.kind)
        assertTrue(inferred.reproduces)
    }

    @Test
    fun `a signed amount reads with its sign`() {
        val plus = infer("Příchozí platba +1 500,00 Kč", "+1 500,00 Kč")
        assertEquals(ExtractKind.AMOUNT, plus.rule.kind)
        assertTrue(plus.reproduces)
        assertEquals("+250,00 Kč", extract(plus.rule, "Příchozí platba +250,00 Kč"))
        assertTrue(infer("Vráceno –500 Kč", "–500 Kč").reproduces)
        assertTrue(infer("Platba -€5 dnes", "-€5").reproduces)
        assertTrue(infer("Platba -CZK 500 dnes", "-CZK 500").reproduces)
        assertTrue(infer("Zůstatek: +12,50", "+12,50").reproduces)
    }

    @Test
    fun `an occurrence no rule can reach adds nothing that is already there`() {
        val sample = "Kód 1234. Kód 5678"
        val first = infer(sample, "5678")
        assertTrue(first.added)
        assertFalse(first.reproduces)
        assertEquals("1234", first.found)
        val again = infer(sample, "5678", existing = listOf(first.rule))
        assertFalse(again.added)
        assertEquals(first.rule, again.rule)
        val alike = first.rule.copy(name = "other", after = " kód ")
        assertFalse(infer(sample, "5678", existing = listOf(alike)).added)
    }

    @Test
    fun `a word inside a pulled line is a value of its own`() {
        val line = ExtractRule(name = "misto", after = "Místo", kind = ExtractKind.LINE)
        val number = infer(PARCEL, "5", existing = listOf(line))
        assertTrue(number.added)
        assertEquals(ExtractRule(name = "hlavni", after = "Hlavní", kind = ExtractKind.NUMBER), number.rule)
        assertTrue(number.reproduces)
        assertTrue(infer(PARCEL, "Praha", existing = listOf(line)).added)
    }

    @Test
    fun `an amount without currency or label stays an amount that reads nothing`() {
        val inferred = infer("12,50 zaplaceno", "12,50")
        assertEquals(ExtractKind.AMOUNT, inferred.rule.kind)
        assertFalse(inferred.reproduces)
        assertNull(inferred.found)
    }

    @Test
    fun `a word wrapped in emoji is tapped as the word`() {
        val inferred = infer("Akce\n🎉Sleva dnes", "Sleva")
        assertEquals("Sleva", inferred.tapped)
    }

    @Test
    fun `a label that is not unique falls back to two words`() {
        val sample = "Kód 1111 neplatí. Výdejní kód: 482913"
        val inferred = infer(sample, "482913")
        assertTrue(inferred.reproduces)
        assertEquals("482913", inferred.found)
        assertEquals("Výdejní kód", inferred.rule.after)
        assertEquals("kod", inferred.rule.name)
    }

    @Test
    fun `a name already taken gets a suffix`() {
        val kod = ExtractRule(name = "kod", kind = ExtractKind.WORD)
        val kod2 = ExtractRule(name = "kod_2", kind = ExtractKind.TIME)
        assertEquals("kod_2", infer(PARCEL, "482913", existing = listOf(kod)).rule.name)
        assertEquals("kod_3", infer(PARCEL, "482913", existing = listOf(kod, kod2)).rule.name)
    }

    @Test
    fun `the reserved names are never given out`() {
        val inferred = infer("Text: 4821", "4821")
        assertEquals("text_2", inferred.rule.name)
    }

    @Test
    fun `a value no rule reads exactly is kept with what it reads instead`() {
        val inferred = infer("Kód 1234. Kód 5678", "5678")
        assertFalse(inferred.reproduces)
        assertEquals("1234", inferred.found)
        assertEquals("5678", inferred.tapped)
    }
}

class ExtractNameTest {

    @Test
    fun `diacritics fold to plain ascii`() {
        assertEquals("kod", foldExtractName("kód"))
        assertEquals("misto", foldExtractName("Místo"))
        assertEquals("vydejni_kod", foldExtractName("Výdejní kód"))
        assertEquals("prijezd", foldExtractName("Příjezd"))
    }

    @Test
    fun `letters without a decomposition still fold`() {
        assertEquals("lodz", foldExtractName("Łódź"))
        assertEquals("strasse", foldExtractName("Straße"))
        assertEquals("hamrun", foldExtractName("Ħamrun"))
        assertEquals("tomas", foldExtractName("Ŧomas"))
        assertEquals("parallel", foldExtractName("Paraŀlel"))
        assertEquals("ijs", foldExtractName("Ĳs"))
        assertEquals("dorde", foldExtractName("Đorđe"))
        assertEquals("oresund", foldExtractName("Øresund"))
        assertEquals("aero", foldExtractName("Ærø"))
        assertEquals("oeuvre", foldExtractName("Œuvre"))
        assertEquals("thor", foldExtractName("Þór"))
    }

    @Test
    fun `compatibility forms fold to their plain letters`() {
        assertEquals("file2", foldExtractName("ﬁle²"))
        assertEquals("abc", foldExtractName("ＡＢＣ"))
    }

    @Test
    fun `anything else becomes a single underscore and is trimmed`() {
        assertEquals("order_no", foldExtractName("Order #no."))
        assertEquals("", foldExtractName("—"))
    }

    @Test
    fun `a name is never longer than the format allows`() {
        assertEquals(32, foldExtractName("a".repeat(40)).length)
        assertEquals(32, uniqueExtractName("a".repeat(32), listOf("a".repeat(32))).length)
    }
}

private fun token(sample: String, value: String): SampleToken =
    sampleTokens(sample).first { sample.substring(it.start, it.end) == value }

private fun values(sample: String): List<String> =
    sampleTokens(sample).filter { it.shape != null }.map { sample.substring(it.start, it.end) }

private fun infer(sample: String, value: String, existing: List<ExtractRule> = emptyList()): InferredRule =
    inferExtractRule(sample, token(sample, value), existing)

private fun extract(rule: ExtractRule, sample: String): String? = extractFields(listOf(rule), sample)[rule.name]

class ExtractMatchTest {

    @Test
    fun `a match points at the value in the original text`() {
        val rule = ExtractRule(name = "kod", after = "kód", kind = ExtractKind.NUMBER)
        val match = extractMatches(listOf(rule), PARCEL).getValue("kod")
        assertEquals("482913", PARCEL.substring(match.start, match.end))
    }

    @Test
    fun `a trimmed line still points at its first character`() {
        val rule = ExtractRule(name = "misto", after = "Místo", kind = ExtractKind.LINE)
        val match = extractMatches(listOf(rule), "Místo:   Hlavní 5, Praha.").getValue("misto")
        assertEquals("Hlavní 5, Praha", match.value)
        assertEquals(9, match.start)
    }
}

class NotificationCountsTest {

    // The worst the builder meets: the most notifications a busy app keeps, each cut at the input
    // limit, with rules that walk the whole text without finding anything.
    @Test
    fun `fifty notifications at the input limit are counted off the main thread in time`() {
        val body = ("Výdejní kód: 482913. " + "Lorem ipsum dolor sit amet, 12 345 Kč. ".repeat(60)).take(2000)
        val notifications = (1..50).map { PostedNotification(key = "$it", packageName = "cz.app", text = body) }
        val rules = listOf(
            ExtractRule(name = "kod", after = "kód", kind = ExtractKind.NUMBER),
            ExtractRule(name = "datum", after = "ipsum", kind = ExtractKind.DATE),
            ExtractRule(name = "cas", kind = ExtractKind.TIME),
            ExtractRule(name = "konec", after = "dolor", kind = ExtractKind.UNTIL, until = "nikde"),
        )
        val condition = Condition.NotificationFrom(packageName = "cz.app", extract = rules)
        notificationCounts(condition, notifications)
        val started = System.nanoTime()
        val counts = notificationCounts(condition, notifications)
        val millis = (System.nanoTime() - started) / NANOS_PER_MILLI
        assertEquals(50, counts?.of)
        assertEquals(50, counts?.hits?.get(rules[0]))
        assertEquals(0, counts?.hits?.get(rules[2]))
        assertTrue(millis < BUDGET_MS, "took $millis ms")
    }
}

private const val NANOS_PER_MILLI = 1_000_000L
private const val BUDGET_MS = 2_000L
