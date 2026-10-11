package app.cue.launcher.data.scenario

import app.cue.launcher.engine.NOTIFICATION_FIELD_TEXT
import app.cue.launcher.engine.NOTIFICATION_FIELD_TITLE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtractNumberTest {

    @Test
    fun `a number after its label`() {
        assertEquals("482913", value(ExtractKind.NUMBER, "Výdejní kód: 482913. Místo: Hlavní 5", after = "kód"))
    }

    @Test
    fun `digit groups split by a space, a no-break space or a dash stay together`() {
        assertEquals("482 913", value(ExtractKind.NUMBER, "Kód 482 913 platí do pátku", after = "kód"))
        assertEquals("482\u00A0913", value(ExtractKind.NUMBER, "Kód: 482\u00A0913", after = "kód"))
        assertEquals("482\u202F913", value(ExtractKind.NUMBER, "Kód: 482\u202F913", after = "kód"))
        assertEquals("123-456", value(ExtractKind.NUMBER, "Order #123-456 shipped", after = "order"))
    }

    @Test
    fun `a separator not followed by a digit ends the number`() {
        assertEquals("1234", value(ExtractKind.NUMBER, "PIN 1234 - use it today", after = "pin"))
        assertEquals("1234", value(ExtractKind.NUMBER, "PIN 1234. Thanks", after = "pin"))
    }

    @Test
    fun `a space joins only a thousands group that ends there`() {
        assertEquals("482913", value(ExtractKind.NUMBER, "Kód 482913 2 ks", after = "kód"))
        assertEquals("1234", value(ExtractKind.NUMBER, "Kód 1234 12:30", after = "kód"))
        assertEquals("12345", value(ExtractKind.NUMBER, "Objednávka 12345 2026", after = "objednávka"))
        assertEquals("1234", value(ExtractKind.NUMBER, "Kód 1234 567.8", after = "kód"))
        assertEquals("1234", value(ExtractKind.NUMBER, "Kód 1234 567/8", after = "kód"))
        assertEquals("482 913", value(ExtractKind.NUMBER, "Kód 482 913", after = "kód"))
        assertEquals("1 249", value(ExtractKind.NUMBER, "1 249 ks"))
    }

    // Read without it, "-5 °C" would put a wrong temperature on the tile.
    @Test
    fun `a minus touching the digits is kept`() {
        assertEquals("-5", value(ExtractKind.NUMBER, "Teplota -5 °C", after = "teplota"))
        assertEquals("−12", value(ExtractKind.NUMBER, "Zítra −12 °C", after = "zítra"))
        assertEquals("-5", value(ExtractKind.NUMBER, "Venku je -5 °C"))
        assertEquals("-5", value(ExtractKind.NUMBER, "Teplota: (-5 °C)", after = "teplota"))
    }

    @Test
    fun `a dash between spaces or after a word or a number is no minus`() {
        assertEquals("1234", value(ExtractKind.NUMBER, "Kód - 1234", after = "kód"))
        assertEquals("1234", value(ExtractKind.NUMBER, "Vlak R-1234"))
        assertEquals("12-5", value(ExtractKind.NUMBER, "Skóre 12-5"))
        assertEquals("420 777 123 456", value(ExtractKind.NUMBER, "Volejte +420 777 123 456"))
    }

    @Test
    fun `the shared digit group rule`() {
        assertTrue(digitGroupContinues("482 913", 3))
        assertTrue(digitGroupContinues("482 913.", 3))
        assertTrue(digitGroupContinues("123-4", 3))
        listOf("482913 2 ks" to 6, "1234 12:30" to 4, "1234 123:45" to 4, "12345 2026" to 5, "1 249.5" to 1)
            .forEach { (text, index) -> assertFalse(digitGroupContinues(text, index)) { text } }
        assertFalse(digitGroupContinues("12 ", 2))
        assertFalse(digitGroupContinues("12", 5))
    }

    @Test
    fun `without a label the first digit run wins`() {
        assertEquals("3", value(ExtractKind.NUMBER, "Bolt · 3 min\nDriver 2 is close"))
    }

    @Test
    fun `the value has to follow the label, not appear somewhere later`() {
        assertEquals(null, value(ExtractKind.NUMBER, "Kód zatím nemáme, zkuste to za 5 minut", after = "kód"))
    }
}

class ExtractCodeTest {

    @Test
    fun `a code is letters, digits and dashes with at least one digit`() {
        assertEquals("AB-12-CD", value(ExtractKind.CODE, "Your code: AB-12-CD.", after = "code"))
        assertEquals("X7K2Q", value(ExtractKind.CODE, "Sleva X7K2Q, platí týden", after = "sleva"))
    }

    @Test
    fun `a trailing dash is not part of the code`() {
        assertEquals("AB12", value(ExtractKind.CODE, "Code AB12- and more", after = "code"))
    }

    @Test
    fun `other punctuation ends the code`() {
        assertEquals("AB12", value(ExtractKind.CODE, "Code: AB12/34", after = "code"))
    }

    @Test
    fun `a word without a digit is not a code`() {
        assertEquals(null, value(ExtractKind.CODE, "Code: ABCD", after = "code"))
    }

    @Test
    fun `without a label the first token with a digit wins`() {
        assertEquals("ZX-81", value(ExtractKind.CODE, "Your pickup code is ZX-81, see you"))
    }
}

class ExtractAmountTest {

    @Test
    fun `a Czech amount with the currency after it`() {
        assertEquals("1 249,00 Kč", value(ExtractKind.AMOUNT, "Platba kartou 1 249,00 Kč v obchodě X"))
        assertEquals("1\u00A0249,50\u00A0Kč", value(ExtractKind.AMOUNT, "Platba 1\u00A0249,50\u00A0Kč"))
        assertEquals("349Kč", value(ExtractKind.AMOUNT, "Zaplaceno 349Kč"))
        assertEquals("349 Kc", value(ExtractKind.AMOUNT, "Zaplaceno 349 Kc"))
        assertEquals("1.249 CZK", value(ExtractKind.AMOUNT, "Prijato 1.249 CZK od Jana"))
    }

    @Test
    fun `a currency in front of the number`() {
        assertEquals("€12.50", value(ExtractKind.AMOUNT, "You paid €12.50 at Cafe"))
        assertEquals("$ 1,249.99", value(ExtractKind.AMOUNT, "Charge of $ 1,249.99 approved"))
        assertEquals("EUR 5", value(ExtractKind.AMOUNT, "Refund EUR 5 sent"))
        assertEquals("£3", value(ExtractKind.AMOUNT, "Spent £3 today"))
    }

    @Test
    fun `other currencies after the number`() {
        assertEquals("120 zł", value(ExtractKind.AMOUNT, "Zapłacono 120 zł"))
        assertEquals("120 PLN", value(ExtractKind.AMOUNT, "Kwota 120 PLN"))
        assertEquals("5 000 Ft", value(ExtractKind.AMOUNT, "Fizetés 5 000 Ft"))
        assertEquals("9.90 CHF", value(ExtractKind.AMOUNT, "Betrag 9.90 CHF"))
        assertEquals("10 USD", value(ExtractKind.AMOUNT, "Sent 10 USD"))
        assertEquals("7 GBP", value(ExtractKind.AMOUNT, "Sent 7 GBP"))
        assertEquals("1’000 HUF", value(ExtractKind.AMOUNT, "Sent 1’000 HUF"))
        assertEquals("2 EUR", value(ExtractKind.AMOUNT, "Sent 2 EUR"))
    }

    @Test
    fun `a negative amount keeps its sign, a minus sign included`() {
        assertEquals("\u22121 249,00 Kč", value(ExtractKind.AMOUNT, "Odchozí platba \u22121 249,00 Kč"))
        assertEquals("-350 Kč", value(ExtractKind.AMOUNT, "Zůstatek: -350 Kč", after = "zůstatek"))
        assertEquals("+500 Kč", value(ExtractKind.AMOUNT, "Příchozí +500 Kč"))
        assertEquals("-€5", value(ExtractKind.AMOUNT, "Fee -€5 charged"))
        assertEquals("-€5", value(ExtractKind.AMOUNT, "Zůstatek: -€5", after = "zůstatek"))
        assertEquals("+EUR 5", value(ExtractKind.AMOUNT, "Zůstatek: +EUR 5", after = "zůstatek"))
    }

    @Test
    fun `a dash touching the number is its sign, a dash between spaces a separator`() {
        assertEquals("–500 Kč", value(ExtractKind.AMOUNT, "Částka –500 Kč", after = "částka"))
        assertEquals("500 Kč", value(ExtractKind.AMOUNT, "Částka – 500 Kč", after = "částka"))
        assertEquals("−500 Kč", value(ExtractKind.AMOUNT, "Částka −500 Kč", after = "částka"))
        assertEquals("–500 Kč", value(ExtractKind.AMOUNT, "Vratka –500 Kč"))
    }

    @Test
    fun `a dash after a number is not a sign`() {
        assertEquals("500 Kč", value(ExtractKind.AMOUNT, "Objednávka 123-500 Kč"))
    }

    @Test
    fun `a number running into a date is not an amount`() {
        assertEquals(null, value(ExtractKind.AMOUNT, "Částka: 5.10.2026", after = "částka"))
        assertEquals(null, value(ExtractKind.AMOUNT, "Částka: 5/10/2026", after = "částka"))
        assertEquals("12.50", value(ExtractKind.AMOUNT, "Částka: 12.50.", after = "částka"))
    }

    @Test
    fun `a whole amount written with a dash`() {
        assertEquals("149,- Kč", value(ExtractKind.AMOUNT, "Cena 149,- Kč"))
    }

    @Test
    fun `without a label only an amount with a currency counts`() {
        assertEquals("349 Kč", value(ExtractKind.AMOUNT, "Karta *1234: platba 349 Kč"))
        assertEquals(null, value(ExtractKind.AMOUNT, "Účet 12 345 678, zbývá 3 dny"))
    }

    @Test
    fun `with a label the currency is optional`() {
        assertEquals("1 249,00", value(ExtractKind.AMOUNT, "Částka: 1 249,00\nObchod: X", after = "částka"))
    }

    @Test
    fun `a currency inside a word does not count`() {
        assertEquals(null, value(ExtractKind.AMOUNT, "5 Europeans and 3 Kcals"))
    }

    @Test
    fun `a thousands group needs exactly three digits`() {
        assertEquals("12 Kč", value(ExtractKind.AMOUNT, "Sleva 1 12 Kč"))
    }
}

class ExtractTimeTest {

    @Test
    fun `times with and without seconds`() {
        assertEquals("9:05", value(ExtractKind.TIME, "Odjezd v 9:05 z nástupiště 3"))
        assertEquals("18:30", value(ExtractKind.TIME, "Meeting at 18:30"))
        assertEquals("07:45:10", value(ExtractKind.TIME, "Alarm 07:45:10 set"))
    }

    @Test
    fun `an impossible time is not a time`() {
        assertEquals(null, value(ExtractKind.TIME, "Skóre 24:00"))
        assertEquals(null, value(ExtractKind.TIME, "Skóre 12:60"))
        assertEquals(null, value(ExtractKind.TIME, "Skóre 3:5"))
        assertEquals(null, value(ExtractKind.TIME, "Skóre 123:45"))
    }

    @Test
    fun `the first valid time wins`() {
        assertEquals("23:59", value(ExtractKind.TIME, "Výsledek 25:10, konec 23:59"))
        assertEquals("0:00", value(ExtractKind.TIME, "From 0:00 to 6:00"))
    }

    @Test
    fun `impossible seconds are left out`() {
        assertEquals("12:30", value(ExtractKind.TIME, "At 12:30:99"))
    }

    @Test
    fun `a time after its label`() {
        assertEquals("14:20", value(ExtractKind.TIME, "Příjezd 13:00, odjezd: 14:20", after = "odjezd"))
    }
}

class ExtractDateTest {

    @Test
    fun `Czech dates with and without a year`() {
        assertEquals("5.10.2026", value(ExtractKind.DATE, "Vyzvedněte do 5.10.2026"))
        assertEquals("5. 10. 2026", value(ExtractKind.DATE, "Vyzvedněte do 5. 10. 2026."))
        assertEquals("24.12.", value(ExtractKind.DATE, "Doručíme 24.12. odpoledne"))
        assertEquals("1. 3.", value(ExtractKind.DATE, "Od 1. 3. platí nový ceník"))
    }

    @Test
    fun `ISO and slashed dates`() {
        assertEquals("2026-10-05", value(ExtractKind.DATE, "Due 2026-10-05"))
        assertEquals("5/10/2026", value(ExtractKind.DATE, "Due 5/10/2026"))
    }

    @Test
    fun `an impossible day or month is not a date`() {
        assertEquals(null, value(ExtractKind.DATE, "Verze 32.1.2026"))
        assertEquals(null, value(ExtractKind.DATE, "Verze 3.13.2026"))
        assertEquals(null, value(ExtractKind.DATE, "Build 2026-13-01"))
        assertEquals(null, value(ExtractKind.DATE, "Build 0/5/2026"))
        assertEquals(null, value(ExtractKind.DATE, "Verze 1.2"))
    }

    @Test
    fun `the day has to exist in its month`() {
        assertEquals(null, value(ExtractKind.DATE, "Dne 31.4.2026"))
        assertEquals(null, value(ExtractKind.DATE, "Dne 31.2."))
        assertEquals(null, value(ExtractKind.DATE, "Dne 30.2."))
        assertEquals(null, value(ExtractKind.DATE, "Dne 29.2.2026"))
        assertEquals(null, value(ExtractKind.DATE, "Due 2026-02-29"))
        assertEquals(null, value(ExtractKind.DATE, "Due 29/02/2026"))
        assertEquals("29.2.2028", value(ExtractKind.DATE, "Dne 29.2.2028"))
        assertEquals("29. 2.", value(ExtractKind.DATE, "Dne 29. 2. odpoledne"))
        assertEquals("2028-02-29", value(ExtractKind.DATE, "Due 2028-02-29"))
        assertEquals("30.4.", value(ExtractKind.DATE, "Dne 30.4."))
    }

    @Test
    fun `a date does not start inside a run of numbers`() {
        assertEquals(null, value(ExtractKind.DATE, "Verze v 1.2.3."))
    }

    @Test
    fun `a two digit year is not taken for a partial date`() {
        assertEquals(null, value(ExtractKind.DATE, "Dne 5.10.26"))
    }

    @Test
    fun `a date after its label`() {
        assertEquals("7. 11.", value(ExtractKind.DATE, "Objednáno 1. 11., doručení: 7. 11.", after = "doručení"))
    }
}

class ExtractWordTest {

    @Test
    fun `the next word with diacritics`() {
        assertEquals("Šárka", value(ExtractKind.WORD, "Zpráva od: Šárka Nováková", after = "od"))
    }

    @Test
    fun `inner dashes, apostrophes and dots stay, trailing punctuation goes`() {
        assertEquals("Jean-Luc", value(ExtractKind.WORD, "Driver Jean-Luc, arriving", after = "driver"))
        assertEquals("O’Brien", value(ExtractKind.WORD, "Driver O’Brien.", after = "driver"))
    }

    @Test
    fun `without a label the first word wins`() {
        assertEquals("Zásilka", value(ExtractKind.WORD, "  — Zásilka je na cestě"))
    }
}

class ExtractLineTest {

    @Test
    fun `the rest of the line without trailing punctuation`() {
        val text = "Místo: Hlavní 5, Praha.\nOtevřeno 8–20"

        assertEquals("Hlavní 5, Praha", value(ExtractKind.LINE, text, after = "místo"))
        assertEquals("Albert Karlín", value(ExtractKind.LINE, "Výdejní místo – Albert Karlín;", after = "místo"))
    }

    @Test
    fun `without a label the first non blank line`() {
        assertEquals("Zásilkovna", value(ExtractKind.LINE, "\n  \nZásilkovna\nKód 1"))
    }

    @Test
    fun `a quote opened after the label is closed off the value`() {
        assertEquals("Hlavní 5", value(ExtractKind.LINE, "Místo: „Hlavní 5“", after = "místo"))
        assertEquals("Hlavní 5", value(ExtractKind.LINE, "Místo: (Hlavní 5).", after = "místo"))
        assertEquals("Hlavní 5\"", value(ExtractKind.LINE, "Místo: Hlavní 5\"", after = "místo"))
        assertEquals("Hlavní (5)", value(ExtractKind.LINE, "Místo: Hlavní (5)", after = "místo"))
    }
}

class ExtractUntilTest {

    @Test
    fun `text from the label up to the marker`() {
        val text = "Pozn.: zvonit dvakrát. Děkujeme"

        assertEquals("zvonit dvakrát", value(ExtractKind.UNTIL, text, after = "pozn.", until = "."))
    }

    @Test
    fun `without a label from the start up to the marker`() {
        assertEquals("Jan Novák", value(ExtractKind.UNTIL, "Jan Novák: Ahoj, jak se máš?", until = ":"))
    }

    @Test
    fun `the marker is matched without case or diacritics`() {
        assertEquals("Praha", value(ExtractKind.UNTIL, "Odkud: Praha KAM: Brno", after = "odkud", until = "kam"))
    }

    @Test
    fun `the marker is matched as whole words`() {
        val text = "Místo: ul. Dobrovského 12, Brno. Do 10. 10."

        assertEquals("ul. Dobrovského 12, Brno", value(ExtractKind.UNTIL, text, after = "místo", until = "do"))
        assertEquals(null, value(ExtractKind.UNTIL, "Místo: Údolní 5", after = "místo", until = "dol"))
    }

    @Test
    fun `a missing marker gives no value`() {
        assertEquals(null, value(ExtractKind.UNTIL, "Pozn: zvonit dvakrát", after = "pozn", until = "."))
    }

    @Test
    fun `punctuation before the marker is trimmed like at the end of a line`() {
        val text = "Místo: nám. Míru 5, Praha. Vyzvednout můžete do pátku."

        assertEquals("nám. Míru 5, Praha", value(ExtractKind.UNTIL, text, after = "místo", until = "vyzvednout"))
    }

    @Test
    fun `the text may span lines`() {
        assertEquals("a\nb", value(ExtractKind.UNTIL, "Note: a\nb END", after = "note", until = "end"))
    }

    @Test
    fun `a dot right after the label is an abbreviation`() {
        assertEquals("a", value(ExtractKind.UNTIL, "Pozn.: a. b", after = "pozn", until = "."))
    }

    @Test
    fun `a quote opened after the label is closed off the value`() {
        assertEquals("zvonit", value(ExtractKind.UNTIL, "Pozn: «zvonit». Díky", after = "pozn", until = "."))
    }
}

class ExtractAnchorTest {

    @Test
    fun `an ascii label finds a word with diacritics`() {
        assertEquals("482913", value(ExtractKind.NUMBER, "Výdejní KÓD: 482913", after = "kod"))
    }

    @Test
    fun `a label with diacritics finds an ascii word`() {
        assertEquals("482913", value(ExtractKind.NUMBER, "Vydejni kod: 482913", after = "kód"))
    }

    @Test
    fun `decomposed accents in the text are matched and returned as written`() {
        val decomposed = "Mi\u0301sto: Hlavni\u0301 5"

        assertEquals("Hlavni\u0301 5", value(ExtractKind.LINE, decomposed, after = "místo"))
    }

    @Test
    fun `the first occurrence followed by a value wins`() {
        val text = "Kód zásilky najdete níže. Kód: 1234, kód: 5678"

        assertEquals("1234", value(ExtractKind.NUMBER, text, after = "kód"))
    }

    @Test
    fun `every kind of separator after the label is skipped`() {
        listOf("Kód 1", "Kód:1", "Kód - 1", "Kód – 1", "Kód — 1", "Kód #1", "Kód = 1", "Kód\t:\u00A01").forEach {
            assertEquals("1", value(ExtractKind.NUMBER, it, after = "kód")) { it }
        }
    }

    @Test
    fun `the value may sit on the next line, but not two lines down`() {
        assertEquals("482913", value(ExtractKind.NUMBER, "Kód:\n482913", after = "kód"))
        assertEquals("482913", value(ExtractKind.NUMBER, "Kód:\r\n482913", after = "kód"))
        assertEquals(null, value(ExtractKind.NUMBER, "Kód:\n\n482913", after = "kód"))
    }

    @Test
    fun `a label is not found inside another word or number`() {
        assertEquals("Jan", value(ExtractKind.WORD, "Kód od: Jan", after = "od"))
        assertEquals("abc", value(ExtractKind.LINE, "Heslování selhalo\nHeslo: abc", after = "heslo"))
        assertEquals(null, value(ExtractKind.CODE, "Heslo ABC1", after = "slo"))
        assertEquals(null, value(ExtractKind.NUMBER, "Order 1234", after = "order 12"))
    }

    @Test
    fun `a letter meeting a digit is a boundary`() {
        assertEquals("123", value(ExtractKind.NUMBER, "Kód123", after = "kód"))
    }

    @Test
    fun `a label in a script without spaces is found inside a run of letters`() {
        assertEquals("100", value(ExtractKind.NUMBER, "支付金额 100", after = "金额"))
    }

    @Test
    fun `any run of blanks matches one space in the label`() {
        listOf(
            "Kód k vyzvednutí: 12",
            "Kód k  vyzvednutí: 12",
            "Kód k\u00A0vyzvednutí: 12",
            "Kód\u202F\u2007k vyzvednutí: 12",
            "Kód\tk vyzvednutí 12",
        ).forEach { assertEquals("12", value(ExtractKind.NUMBER, it, after = "kód k vyzvednutí")) { it } }
        assertEquals("12", value(ExtractKind.NUMBER, "Kód k vyzvednutí: 12", after = "kód \u00A0k vyzvednutí"))
    }

    @Test
    fun `a value spanning a run of blanks is returned as written`() {
        assertEquals("Hlavní  5", value(ExtractKind.LINE, "Místo: Hlavní  5", after = "místo"))
    }

    @Test
    fun `an opening quote or bracket after the label is skipped`() {
        assertEquals("Jan", value(ExtractKind.WORD, "Od: 'Jan'", after = "od"))
        assertEquals("1234", value(ExtractKind.NUMBER, "Kód (1234)", after = "kód"))
        assertEquals("ABC1", value(ExtractKind.CODE, "Kód „ABC1“", after = "kód"))
        assertEquals("ABC1", value(ExtractKind.CODE, "Kód: [ABC1]", after = "kód"))
    }

    @Test
    fun `a dot right after the label is skipped`() {
        assertEquals("123", value(ExtractKind.NUMBER, "tel. 123", after = "tel"))
    }

    @Test
    fun `a final sigma matches a sigma`() {
        assertEquals("5", value(ExtractKind.NUMBER, "Κωδικός 5", after = "ΚΩΔΙΚΟΣ"))
    }

    @Test
    fun `a blank label behaves as no label`() {
        assertEquals("7", value(ExtractKind.NUMBER, "Za 7 minut", after = "  "))
    }

    @Test
    fun `a label that folds to nothing behaves as no label`() {
        assertEquals("7", value(ExtractKind.NUMBER, "Za 7 minut", after = "\u0301"))
        assertEquals(null, value(ExtractKind.AMOUNT, "Za 7 minut", after = "\u0301"))
        assertEquals("349 Kč", value(ExtractKind.AMOUNT, "Za 7 minut 349 Kč", after = " \u0301 "))
    }

    @Test
    fun `a missing label gives no value`() {
        assertEquals(null, value(ExtractKind.NUMBER, "Výdejní místo 5", after = "kód"))
    }
}

class ExtractFieldsTest {

    @Test
    fun `each rule fills its own field`() {
        val text = "Zásilkovna\nVaše zásilka je připravena. Výdejní kód: 482913. Místo: Hlavní 5, Praha"
        val rules = listOf(
            ExtractRule(name = "kod", after = "kód", kind = ExtractKind.NUMBER),
            ExtractRule(name = "misto", after = "místo", kind = ExtractKind.LINE),
            ExtractRule(name = "nic", after = "částka", kind = ExtractKind.AMOUNT),
        )

        assertEquals(mapOf("kod" to "482913", "misto" to "Hlavní 5, Praha"), extractFields(rules, text))
    }

    @Test
    fun `only the start of a long text is searched`() {
        val rule = ExtractRule(name = "kod", after = "kód", kind = ExtractKind.NUMBER)
        val padding = "x".repeat(EXTRACT_MAX_INPUT)

        assertEquals(emptyMap<String, String>(), extractFields(listOf(rule), padding + "kód 1234"))
        assertEquals(mapOf("kod" to "1234"), extractFields(listOf(rule), "kód 1234$padding"))
    }

    @Test
    fun `a value is capped`() {
        val rule = ExtractRule(name = "zbytek", kind = ExtractKind.LINE)

        assertEquals(EXTRACT_MAX_VALUE, extractFields(listOf(rule), "y".repeat(500)).getValue("zbytek").length)
    }

    @Test
    fun `the cap never splits a character`() {
        val rule = ExtractRule(name = "zbytek", kind = ExtractKind.LINE)
        val text = "y".repeat(EXTRACT_MAX_VALUE - 1) + "😀".repeat(2)

        assertEquals("y".repeat(EXTRACT_MAX_VALUE - 1), extractFields(listOf(rule), text).getValue("zbytek"))
    }

    @Test
    fun `rules the validation would refuse are skipped`() {
        val long = "x".repeat(EXTRACT_MARKER_MAX_LENGTH + 1)
        val text = "Kód 12 $long"
        val invalid = listOf(
            ExtractRule(name = "Kod", kind = ExtractKind.NUMBER),
            ExtractRule(name = NOTIFICATION_FIELD_TITLE, kind = ExtractKind.NUMBER),
            ExtractRule(name = "b", kind = ExtractKind.UNTIL, until = long),
            ExtractRule(name = "a", kind = ExtractKind.NUMBER),
        )
        val duplicate = listOf(
            ExtractRule(name = "a", kind = ExtractKind.NUMBER),
            ExtractRule(name = "a", kind = ExtractKind.WORD),
        )

        assertEquals(mapOf("a" to "12"), extractFields(invalid, text))
        assertEquals(mapOf("a" to "12"), extractFields(duplicate, text))
    }

    @Test
    fun `no more than four rules are evaluated`() {
        val rules = List(EXTRACT_MAX_RULES + 1) { ExtractRule(name = "v$it", kind = ExtractKind.WORD) }

        assertEquals(EXTRACT_MAX_RULES, extractFields(rules, "Ahoj").size)
    }

    @Test
    fun `the reserved names are the notification's own fields`() {
        assertEquals(setOf(NOTIFICATION_FIELD_TITLE, NOTIFICATION_FIELD_TEXT), EXTRACT_RESERVED_NAMES)
    }

    @Test
    fun `a pathological text and label still finish quickly`() {
        val text = "a".repeat(EXTRACT_MAX_INPUT)
        val anchor = "a".repeat(EXTRACT_MARKER_MAX_LENGTH)
        val rules = listOf(
            ExtractRule(name = "n", after = anchor, kind = ExtractKind.NUMBER),
            ExtractRule(name = "u", after = anchor, kind = ExtractKind.UNTIL, until = anchor),
            ExtractRule(name = "d", after = anchor, kind = ExtractKind.DATE),
            ExtractRule(name = "m", after = anchor, kind = ExtractKind.UNTIL, until = "b"),
        )
        val started = System.nanoTime()

        repeat(REPEATS) { extractFields(rules, text) }

        val elapsedMillis = (System.nanoTime() - started) / NANOS_PER_MILLI
        assertTrue(elapsedMillis < BUDGET_MILLIS) { "took $elapsedMillis ms" }
    }

    @Test
    fun `a pathological amount text without a currency finishes quickly`() {
        val text = "111 ".repeat(EXTRACT_MAX_INPUT / 4)
        val started = System.nanoTime()

        val fields = extractFields(listOf(ExtractRule(name = "a", kind = ExtractKind.AMOUNT)), text)

        val elapsedMillis = (System.nanoTime() - started) / NANOS_PER_MILLI
        assertTrue(fields.isEmpty())
        assertTrue(elapsedMillis < BUDGET_MILLIS) { "took $elapsedMillis ms" }
    }

    private companion object {
        const val REPEATS = 10
        const val NANOS_PER_MILLI = 1_000_000L

        // Generous for a loaded CI machine; a backtracking matcher on the same input never finishes.
        const val BUDGET_MILLIS = 1_000L
    }
}

// Every row of the kinds table in docs/scenario-format.md "Values from a notification", word for
// word, so the table cannot promise what the evaluator does not do.
class ExtractDocumentedExamplesTest {

    @Test
    fun `number`() {
        val text = "Your pickup code: 482 913. Pickup point: 5 Main St"

        assertEquals("482 913", value(ExtractKind.NUMBER, text, after = "pickup code"))
    }

    @Test
    fun `code`() {
        assertEquals("A7K-29Q", value(ExtractKind.CODE, "Your code A7K-29Q is ready", after = "code"))
    }

    @Test
    fun `amount`() {
        assertEquals("−€1,249.00", value(ExtractKind.AMOUNT, "Card payment of −€1,249.00 at Albert"))
    }

    @Test
    fun `time`() {
        assertEquals("14:05", value(ExtractKind.TIME, "Train R 1234 departs 14:05 from platform 3", after = "departs"))
    }

    @Test
    fun `date`() {
        assertEquals("12/10/2026", value(ExtractKind.DATE, "Collect it by 12/10/2026", after = "by"))
    }

    @Test
    fun `word`() {
        assertEquals("Albert", value(ExtractKind.WORD, "Card payment of −€1,249.00 at Albert", after = "at"))
    }

    @Test
    fun `line`() {
        val text = "Pickup point: 5 Main St, Springfield"

        assertEquals("5 Main St, Springfield", value(ExtractKind.LINE, text, after = "pickup point"))
    }

    @Test
    fun `until`() {
        val text = "Pickup point: 5 Main St, Springfield. Collect it by Friday."

        assertEquals("5 Main St, Springfield", value(ExtractKind.UNTIL, text, after = "pickup point", until = "."))
    }

    @Test
    fun `until past an abbreviation`() {
        val text = "Pickup point: unit no. 5, Springfield. Collect it by Friday."

        val atNextSentence = value(ExtractKind.UNTIL, text, after = "pickup point", until = "Collect")

        assertEquals("unit no", value(ExtractKind.UNTIL, text, after = "pickup point", until = "."))
        assertEquals("unit no. 5, Springfield", atNextSentence)
    }

    @Test
    fun `matching ignores diacritics but not word boundaries`() {
        assertEquals("12", value(ExtractKind.NUMBER, "Café: 12", after = "cafe"))
        assertEquals(null, value(ExtractKind.NUMBER, "Cafeteria 12", after = "cafe"))
    }

    @Test
    fun `the opening example`() {
        assertEquals("482913", value(ExtractKind.NUMBER, "Your pickup code: 482913", after = "pickup code"))
    }
}

private fun value(kind: ExtractKind, text: String, after: String? = null, until: String? = null): String? =
    extractFields(listOf(ExtractRule(name = "v", after = after, kind = kind, until = until)), text)["v"]
