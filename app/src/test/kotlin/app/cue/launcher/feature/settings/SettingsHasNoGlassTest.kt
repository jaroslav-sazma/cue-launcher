package app.cue.launcher.feature.settings

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsHasNoGlassTest {

    @Test
    fun `settings does not touch the glass API`() {
        val sources = settingsSources()

        assertTrue(sources.size > MIN_SOURCES) { "the Settings branch was not found: ${sources.size} files." }

        val offenders = sources
            .filterNot { it.name in ALLOWED }

            .filter { file -> file.readLines().any(::importsGlass) }
            .map { it.name }

        assertTrue(offenders.isEmpty()) {
            "Sklo se vrátilo do Nastavení: $offenders. " +
                "Barvy tam berou role z MaterialTheme (M10.8 bod 13)."
        }
    }

    private fun importsGlass(line: String): Boolean =
        line.startsWith("import ") && GLASS_API.any { line.endsWith(".$it") }

    private fun settingsSources(): List<File> = SETTINGS_TREES
        .map { File(SOURCE_ROOT, it) }
        .flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" } }

    private companion object {

        const val SOURCE_ROOT = "src/main/kotlin/app/cue/launcher"

        const val MIN_SOURCES = 30

        val SETTINGS_TREES = listOf("feature/settings", "feature/scenarios", "feature/onboarding")

        val GLASS_API = listOf("LocalCueColors", "GlassLevel", "TileSurface")

        val ALLOWED = setOf("TilePreview.kt", "OnboardingScreen.kt", "ScenarioItemIcon.kt")
    }
}
