package app.cue.launcher.data.settings

import androidx.datastore.core.CorruptionException
import app.cue.launcher.data.aichat.AiChatShare
import app.cue.launcher.data.scenario.GesturesConfig
import app.cue.launcher.data.scenario.IconMode
import app.cue.launcher.data.scenario.TapAction
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LauncherSettingsSerializerTest {

    @Test
    fun `default values are conservative`() {
        assertEquals(LauncherSettings(dockSeeded = false), LauncherSettingsSerializer.defaultValue)
    }

    @Test
    fun `settings roundtrip`() = runTest {
        val settings = LauncherSettings(dockSeeded = true)
        val output = ByteArrayOutputStream()

        LauncherSettingsSerializer.writeTo(settings, output)

        assertEquals(settings, LauncherSettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray())))
    }

    @Test
    fun `missing and unknown fields are ignored`() = runTest {
        val json = """{"neznamePoleZNovejsiVerze":42}"""

        val settings = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(json.encodeToByteArray()))

        assertEquals(LauncherSettings(), settings)
    }

    @Test
    fun `contact search is off by default`() = runTest {
        val beforeM85 = """{"dockSeeded":true}"""

        val settings = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(beforeM85.encodeToByteArray()))

        assertEquals(DrawerSettings(contacts = false), settings.drawer)
    }

    @Test
    fun `old settings with a pack read as native mode`() = runTest {
        val beforeM102 = """{"appearance":{"iconPack":"com.pack.icons"}}"""

        val settings = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(beforeM102.encodeToByteArray()))

        assertEquals(IconMode.NATIVE, settings.appearance.iconMode)
        assertNull(settings.appearance.selectedPack)
    }

    @Test
    fun `a pack only applies in its own mode`() {
        val pack = AppearanceSettings(iconMode = IconMode.ICONPACK, iconPack = "com.pack.icons")

        assertEquals("com.pack.icons", pack.selectedPack)
        assertNull(pack.copy(iconMode = IconMode.TINTED).selectedPack)
        assertNull(pack.copy(iconMode = IconMode.NATIVE).selectedPack)
    }

    @Test
    fun `drawer rows have default values from MODEL`() = runTest {
        val beforeM103 = """{"drawer":{"contacts":true}}"""

        val settings = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(beforeM103.encodeToByteArray()))

        assertEquals(DrawerSettings(contacts = true, aiRow = false, webRow = true), settings.drawer)
    }

    @Test
    fun `a gesture is unset by default`() = runTest {
        val beforeM103 = """{"dockSeeded":true}"""

        val settings = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(beforeM103.encodeToByteArray()))

        assertNull(settings.gestures.swipeLeft)
    }

    @Test
    fun `gesture action roundtrip`() = runTest {
        val settings = LauncherSettings(
            gestures = GesturesConfig(swipeLeft = TapAction.App("com.spotify.music")),
        )
        val output = ByteArrayOutputStream()

        LauncherSettingsSerializer.writeTo(settings, output)

        val read = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
        assertEquals(TapAction.App("com.spotify.music"), read.gestures.swipeLeft)
    }

    @Test
    fun `the searchbar action is unset by default and survives a roundtrip`() = runTest {
        val beforeM107 = """{"gestures":{"swipeLeft":{"kind":"app","package":"com.spotify.music"}}}"""

        val old = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(beforeM107.encodeToByteArray()))
        assertNull(old.gestures.searchbarTap)

        val output = ByteArrayOutputStream()
        LauncherSettingsSerializer.writeTo(
            old.copy(gestures = old.gestures.copy(searchbarTap = TapAction.App("com.duckduckgo.mobile.android"))),
            output,
        )

        val read = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
        assertEquals(TapAction.App("com.duckduckgo.mobile.android"), read.gestures.searchbarTap)
        assertEquals(TapAction.App("com.spotify.music"), read.gestures.swipeLeft)
    }

    @Test
    fun `the calendar action is unset by default and survives a roundtrip`() = runTest {
        val before = """{"gestures":{"swipeLeft":{"kind":"app","package":"com.spotify.music"}}}"""

        val old = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(before.encodeToByteArray()))
        assertNull(old.gestures.calendarTap)

        val output = ByteArrayOutputStream()
        LauncherSettingsSerializer.writeTo(
            old.copy(gestures = old.gestures.copy(calendarTap = TapAction.App("com.google.android.calendar"))),
            output,
        )

        val read = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
        assertEquals(TapAction.App("com.google.android.calendar"), read.gestures.calendarTap)
        assertEquals(TapAction.App("com.spotify.music"), read.gestures.swipeLeft)
    }

    @Test
    fun `the media widget follows YouTube Music until another player is picked`() = runTest {
        val before = """{"appearance":{"iconMode":"tinted"}}"""

        val old = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(before.encodeToByteArray()))
        assertEquals("com.google.android.apps.youtube.music", old.mediaWidget.app)

        val output = ByteArrayOutputStream()
        LauncherSettingsSerializer.writeTo(old.copy(mediaWidget = MediaWidgetSettings("com.spotify.music")), output)

        val read = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
        assertEquals("com.spotify.music", read.mediaWidget.app)
    }

    @Test
    fun `reduced transparency is off by default`() = runTest {
        val beforeM107 = """{"appearance":{"iconMode":"tinted"}}"""

        val settings = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(beforeM107.encodeToByteArray()))

        assertEquals(IconMode.TINTED, settings.appearance.iconMode)
        assertEquals(false, settings.appearance.reducedTransparency)
    }

    @Test
    fun `layout lock is off by default and survives a roundtrip`() = runTest {
        val beforeM108 = """{"dockSeeded":true}"""

        val old = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(beforeM108.encodeToByteArray()))
        assertEquals(false, old.layoutLocked)

        val output = ByteArrayOutputStream()
        LauncherSettingsSerializer.writeTo(old.copy(layoutLocked = true), output)

        val read = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
        assertEquals(true, read.layoutLocked)
    }

    @Test
    fun `AI chat scenarios are off by default and survive a roundtrip`() = runTest {
        val before = """{"dockSeeded":true}"""
        val old = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(before.encodeToByteArray()))
        assertEquals(false, old.aiChatScenarios)

        val output = ByteArrayOutputStream()
        LauncherSettingsSerializer.writeTo(old.copy(aiChatScenarios = true), output)

        val read = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
        assertEquals(true, read.aiChatScenarios)
    }

    @Test
    fun `the AI chat choice shares everything and nothing is pending by default, and both survive a roundtrip`() =
        runTest {
            val before = """{"aiChatScenarios":true}"""
            val old = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(before.encodeToByteArray()))
            assertEquals(AiChatShare(bluetooth = true, homeAssistant = true, shortcuts = true), old.aiChatShare)
            assertEquals(false, old.aiChatPending)

            val chosen = AiChatShare(bluetooth = false, homeAssistant = true, shortcuts = false)
            val output = ByteArrayOutputStream()
            LauncherSettingsSerializer.writeTo(old.copy(aiChatShare = chosen, aiChatPending = true), output)

            assertTrue(""""shortcuts":false""" in output.toByteArray().decodeToString())
            val read = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
            assertEquals(chosen, read.aiChatShare)
            assertEquals(true, read.aiChatPending)
        }

    @Test
    fun `a choice stored under an earlier name is dropped for the default`() = runTest {
        val before = """{"aiChatShare":{"bluetooth":false,"appActions":false}}"""

        val read = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(before.encodeToByteArray()))

        assertEquals(AiChatShare(bluetooth = false, homeAssistant = true, shortcuts = true), read.aiChatShare)
    }

    @Test
    fun `last backup roundtrip`() = runTest {
        val summary = BackupSummary(name = "cue-zaloha.json", scenarios = 12, bytes = 18_432, at = 1_758_000_000_000)
        val output = ByteArrayOutputStream()

        LauncherSettingsSerializer.writeTo(LauncherSettings(lastBackup = summary), output)

        val read = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
        assertEquals(summary, read.lastBackup)
        assertNull(LauncherSettings().lastBackup)
    }

    @Test
    fun `an unknown icon mode and a stray null fall back to the defaults`() = runTest {
        val json = """{"dockSeeded":true,"appearance":{"iconMode":"holographic"},"drawer":null}"""

        val settings = LauncherSettingsSerializer.readFrom(ByteArrayInputStream(json.encodeToByteArray()))

        assertEquals(true, settings.dockSeeded)
        assertEquals(IconMode.NATIVE, settings.appearance.iconMode)
        assertEquals(DrawerSettings(), settings.drawer)
    }

    @Test
    fun `a corrupted file reports CorruptionException`() = runTest {
        val broken = ByteArrayInputStream("{tohle není JSON".encodeToByteArray())

        assertThrows<CorruptionException> { LauncherSettingsSerializer.readFrom(broken) }
    }
}
