package app.cue.launcher.system.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ProcessTextManifestTest {

    private val declaration: String by lazy {
        val manifest = File(BridgeFixtures.REPO_ROOT, "app/src/main/AndroidManifest.xml")
        assertTrue(manifest.isFile) { "the manifest is not in the repository" }
        manifest.readText()
            .substringAfter(ACTIVITY_NAME, missingDelimiterValue = "")
            .substringBefore("</activity>")
    }

    private val themes: String by lazy {
        File(BridgeFixtures.REPO_ROOT, "app/src/main/res/values/themes.xml").readText()
    }

    @Test
    fun `the manifest offers both text selection and the share sheet on plain text alone`() {
        assertTrue(declaration.isNotEmpty()) { "the manifest has no $ACTIVITY_NAME activity" }
        val actions = Regex("""<action android:name="([^"]+)" />""").findAll(declaration).map { it.groupValues[1] }
        assertEquals(
            listOf("android.intent.action.PROCESS_TEXT", "android.intent.action.SEND"),
            actions.toList(),
        )
        val mimeTypes = Regex("""<data android:mimeType="([^"]+)" />""").findAll(declaration).map { it.groupValues[1] }
        assertEquals(listOf("text/plain", "text/plain"), mimeTypes.toList())
    }

    @Test
    fun `the activity is disabled until a Mac is paired`() {
        assertTrue(declaration.contains("""android:enabled="false"""")) {
            "Send to Mac must not show in text selection menus while the bridge is off"
        }
    }

    @Test
    fun `the activity is exported, or other apps' selection toolbars cannot start it`() {
        assertTrue(declaration.contains("""android:exported="true""""))
    }

    // Behind a translucent window the home screen would only pause, and its session would stay up.
    @Test
    fun `the activity is opaque`() {
        val theme = Regex("""android:theme="@style/([^"]+)"""").find(declaration)?.groupValues?.get(1)
        assertEquals("Theme.Cue", theme)
        val style = themes.substringAfter("""<style name="Theme.Cue"""").substringBefore("</style>")
        assertFalse(style.contains("windowIsTranslucent"))
    }

    private companion object {
        const val ACTIVITY_NAME = """android:name=".system.bridge.ProcessTextActivity""""
    }
}
