package app.cue.launcher.designsystem

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

// The launcher renders in the system font end to end, which only holds as long as nothing names a
// typeface of its own. Three families are allowed to: the bundled MDI glyph font, which is an icon
// set rather than text; the monospace family the Home Assistant secret field uses so a token can
// be read character by character; and the rounded display family, which is not another typeface at
// all but the system one with its variable axes set.
class TypeContractTest {

    private val allowed = setOf(
        "MdiFontFamily",
        "FontFamily.Monospace",
        "rememberRoundedDisplayFamily",
    )

    @Test
    fun `no source names a typeface beyond the glyph font and the secret field`() {
        val offenders = sources()
            .flatMap { file -> FONT_FAMILY.findAll(file.readText()).map { file to it.groupValues[1] } }
            .filterNot { (_, family) -> family in allowed }
            .map { (file, family) -> "${file.name}: $family" }
            .toList()

        assertTrue(offenders.isEmpty()) {
            "these pin a typeface instead of inheriting the system font: $offenders"
        }
    }

    @Test
    fun `the theme leaves the Material type scale at its defaults`() {
        val theme = repoFile("app/src/main/kotlin/app/cue/launcher/designsystem/CueTheme.kt").readText()

        assertTrue(!theme.contains("typography")) {
            "CueTheme passes a typography of its own — the scale has to stay on the device font"
        }
    }

    private fun sources(): Sequence<File> =
        repoFile("app/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }

    private fun repoFile(path: String): File = File(REPO_ROOT, path).also {
        assertTrue(it.exists()) { "the file $path is not in the repository" }
    }

    private companion object {

        val FONT_FAMILY = Regex("""fontFamily\s*=\s*([\w.]+)""")

        val REPO_ROOT: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "fixtures/scenarios").isDirectory }
    }
}
