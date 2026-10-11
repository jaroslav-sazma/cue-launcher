package app.cue.launcher

import app.cue.launcher.data.scenario.AccentSource
import app.cue.launcher.data.settings.AppearanceSettings
import app.cue.launcher.designsystem.ThemeAccent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThemeAccentTest {

    @Test
    fun `each choice reaches the theme`() {
        assertEquals(ThemeAccent.Primary, themeAccentOf(AppearanceSettings()))
        assertEquals(ThemeAccent.Tertiary, themeAccentOf(AppearanceSettings(accent = AccentSource.TERTIARY)))
        assertEquals(
            ThemeAccent.Custom(0x3A86FF),
            themeAccentOf(AppearanceSettings(accent = AccentSource.CUSTOM, customAccent = 0x3A86FF)),
        )
    }

    @Test
    fun `a kept custom colour does not apply while another accent is chosen`() {
        val kept = AppearanceSettings(customAccent = 0x3A86FF)

        assertEquals(ThemeAccent.Primary, themeAccentOf(kept))
        assertEquals(ThemeAccent.Tertiary, themeAccentOf(kept.copy(accent = AccentSource.TERTIARY)))
    }

    @Test
    fun `a custom accent without a colour falls back to the system one`() {
        assertEquals(ThemeAccent.Primary, themeAccentOf(AppearanceSettings(accent = AccentSource.CUSTOM)))
    }
}
