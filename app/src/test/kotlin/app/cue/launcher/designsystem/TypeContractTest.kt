package app.cue.launcher.designsystem

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

// The launcher draws its text in the phone's own typeface, which only holds as long as one place
// picks it: designsystem/Type.kt. Two other families are allowed: the bundled MDI glyph font,
// which is an icon set rather than text, and the monospace family the Home Assistant secret field
// uses so a token can be read character by character.
class TypeContractTest {

    @Test
    fun `only Type kt names a typeface`() {
        val offenders = sources()
            .filterNot { it.name == "Type.kt" || it.name == "MdiFont.kt" }
            .flatMap { file ->
                TYPEFACE.findAll(file.readText()).map { "${file.name}: ${it.value}" }
            }
            .toList()

        assertTrue(offenders.isEmpty()) {
            "these pick a typeface instead of going through TypeSpec or the theme: $offenders"
        }
    }

    @Test
    fun `nothing pins a font family beyond the glyph font and the secret field`() {
        val offenders = sources()
            .filterNot { it.name == "Type.kt" }
            .flatMap { file ->
                FONT_FAMILY.findAll(file.readText()).map { file to it.groupValues[1] }
            }
            .filterNot { (_, family) -> family in ALLOWED_FAMILIES }
            .map { (file, family) -> "${file.name}: $family" }
            .toList()

        assertTrue(offenders.isEmpty()) { "these pin a family of their own: $offenders" }
    }

    @Test
    fun `the theme takes its type scale from the design system`() {
        val theme = repoFile("app/src/main/kotlin/app/cue/launcher/designsystem/CueTheme.kt").readText()

        val passed = Regex("""typography = (\w+)""").find(theme)?.groupValues?.get(1)

        assertTrue(passed != null && theme.contains("val $passed = brandTypography()")) {
            "CueTheme has to pass the typography brandTypography() builds"
        }
    }

    private fun sources(): Sequence<File> =
        repoFile("app/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }

    private fun repoFile(path: String): File = File(REPO_ROOT, path).also {
        assertTrue(it.exists()) { "the file $path is not in the repository" }
    }

    private companion object {

        val ALLOWED_FAMILIES = setOf("MdiFontFamily", "FontFamily.Monospace")

        val TYPEFACE = Regex(
            """\bDeviceFontFamilyName\b|\bFontFamily\(|\bFont\(|\bTypeface\b|""" +
                """\bFontFamily\.(SansSerif|Serif|Cursive|Default)\b|\bFontVariation\b""",
        )

        val FONT_FAMILY = Regex("""fontFamily\s*=\s*([\w.]+)""")

        val REPO_ROOT: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "fixtures/scenarios").isDirectory }
    }
}
