package app.cue.launcher.data.settings

import app.cue.launcher.data.scenario.AccentSource
import app.cue.launcher.data.scenario.AppearanceConfig
import app.cue.launcher.data.scenario.LauncherConfig
import app.cue.launcher.data.scenario.StaticZoneConfig
import app.cue.launcher.system.launcherConfigOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AccentSettingsTest {

    private val repository = SettingsRepository(MemorySettingsStore())

    private suspend fun appearance() = repository.settings.first().appearance

    @Test
    fun `the system primary is the default accent`() = runTest {
        assertEquals(AccentSource.PRIMARY, appearance().accent)
        assertEquals(null, appearance().customAccent)
    }

    @Test
    fun `a custom colour stays stored while another accent is chosen`() = runTest {
        repository.setAccent(AccentSource.CUSTOM, 0x3A86FF)

        repository.setAccent(AccentSource.TERTIARY)
        assertEquals(AccentSource.TERTIARY, appearance().accent)
        assertEquals(0x3A86FF, appearance().customAccent)

        repository.setAccent(AccentSource.CUSTOM)
        assertEquals(AppearanceSettings(accent = AccentSource.CUSTOM, customAccent = 0x3A86FF), appearance())
    }

    @Test
    fun `a custom accent without a colour counts as the main one`() {
        assertEquals(AccentSource.PRIMARY, AppearanceSettings(accent = AccentSource.CUSTOM).effectiveAccent)
        assertEquals(
            AccentSource.CUSTOM,
            AppearanceSettings(accent = AccentSource.CUSTOM, customAccent = 0x3A86FF).effectiveAccent,
        )
        assertEquals(AccentSource.TERTIARY, AppearanceSettings(accent = AccentSource.TERTIARY).effectiveAccent)
    }

    @Test
    fun `a stored colour carries no alpha`() = runTest {
        repository.setAccent(AccentSource.CUSTOM, 0xFF3A86FF.toInt())

        assertEquals(0x3A86FF, appearance().customAccent)
    }

    @Test
    fun `the accent survives the settings file`() = runTest {
        val settings = LauncherSettings(
            appearance = AppearanceSettings(accent = AccentSource.CUSTOM, customAccent = 0x3A86FF),
        )
        val output = ByteArrayOutputStream()

        LauncherSettingsSerializer.writeTo(settings, output)

        assertEquals(settings, LauncherSettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray())))
    }

    @Test
    fun `an unknown accent in the settings file reads as the system one`() = runTest {
        val json = """{"appearance":{"accent":"secondary","customAccent":3835647}}"""

        val settings = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(json.encodeToByteArray()))

        assertEquals(AccentSource.PRIMARY, settings.appearance.accent)
        assertEquals(0x3A86FF, settings.appearance.customAccent)
    }

    @Test
    fun `the accent comes back from an exported configuration`() = runTest {
        val exported = LauncherSettings(
            appearance = AppearanceSettings(accent = AccentSource.TERTIARY, customAccent = 0x3A86FF),
        )
        val config = launcherConfigOf(exported, StaticZoneConfig(), emptyList(), emptyList())

        repository.applyConfig(config)

        assertEquals(AccentSource.TERTIARY, appearance().accent)
        assertEquals(0x3A86FF, appearance().customAccent)
    }

    @Test
    fun `restoring a custom accent with a broken colour falls back to the system one`() = runTest {
        repository.setAccent(AccentSource.TERTIARY, 0x112233)

        repository.applyConfig(
            LauncherConfig(appearance = AppearanceConfig(accent = AccentSource.CUSTOM, customAccent = "#12")),
        )

        assertEquals(AccentSource.PRIMARY, appearance().accent)
        assertEquals(null, appearance().customAccent) { "the restore mirrors the backup" }
    }

    @Test
    fun `restoring a configuration without an accent resets it to the system one`() = runTest {
        repository.setAccent(AccentSource.CUSTOM, 0x112233)

        repository.applyConfig(LauncherConfig())

        assertEquals(AccentSource.PRIMARY, appearance().accent)
    }
}
