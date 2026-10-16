package app.cue.launcher.system.aichat

import app.cue.launcher.R
import app.cue.launcher.data.aichat.InventorySnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.util.Locale

internal val TEST_ROUTE = PasteRoute(
    settings = "Launcher settings",
    scenarios = "Scenarios",
    card = "Scenario from an AI chat",
    pasteAnswer = "Paste the answer",
    backups = "Backups",
    pasteJson = "Paste scenario JSON",
    handoff = "Create in an AI chat",
)

class PasteRouteTest {

    @ParameterizedTest
    @CsvSource("values, en-US", "values-cs, cs-CZ")
    fun `the answer's rules name the way back with the labels Cue shows in that language`(values: String, tag: String) {
        val route = routeFrom(values)

        val answer = aiChatContext(InventorySnapshot.EMPTY, Locale.forLanguageTag(tag), route)
            .substringAfter("## Your final answer")

        listOf(route.settings, route.scenarios, route.card, route.pasteAnswer, route.backups, route.pasteJson)
            .forEach { label -> assertTrue("\"$label\"" in answer, "$values: $label") }
        assertTrue("copy button" in answer && "untranslated" in answer)
    }

    @Test
    fun `the English labels are the ones the other tests quote`() {
        assertEquals(TEST_ROUTE, routeFrom("values"))
    }

    @Test
    fun `the Czech brief quotes the Czech screens and keeps its own text in English`() {
        val answer = aiChatContext(InventorySnapshot.EMPTY, Locale.forLanguageTag("cs-CZ"), routeFrom("values-cs"))
            .substringAfter("## Your final answer")

        assertTrue("\"Nastavení launcheru\" →" in answer, answer)
        assertTrue("\"Zálohy\" → \"Vložit JSON scénáře\"" in answer, answer)
        assertTrue("long-press the home screen" in answer)
    }

    private fun routeFrom(values: String): PasteRoute {
        val strings = STRING.findAll(repoFile("app/src/main/res/$values/strings.xml").readText())
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'").replace("\\\"", "\"") }
        return pasteRoute { id -> strings.getValue(nameOf(id)) }
    }

    private fun nameOf(id: Int): String = R.string::class.java.fields.first { it.getInt(null) == id }.name

    private fun repoFile(path: String): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, path) }
        .first(File::exists)

    private companion object {
        val STRING = Regex("""<string name="([^"]+)">(.*?)</string>""")
    }
}
