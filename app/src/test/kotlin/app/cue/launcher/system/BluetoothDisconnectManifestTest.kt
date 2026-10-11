package app.cue.launcher.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BluetoothDisconnectManifestTest {

    private val declaration: String by lazy {
        val manifest = File(REPO_ROOT, "app/src/main/AndroidManifest.xml")
        assertTrue(manifest.isFile) { "the manifest is not in the repository" }
        manifest.readText()
            .substringAfter(RECEIVER_NAME, missingDelimiterValue = "")
            .substringBefore("</receiver>")
    }

    @Test
    fun `the manifest declares the receiver`() {
        assertTrue(declaration.isNotEmpty()) { "the manifest has no $RECEIVER_NAME receiver" }
    }

    @Test
    fun `the receiver listens only for ACL disconnects`() {
        val actions = ACTION.findAll(declaration).map { it.groupValues[1] }.toList()
        assertEquals(listOf(ACL_DISCONNECTED), actions) { "the receiver must listen for $ACL_DISCONNECTED alone" }
    }

    @Test
    fun `the receiver is exported, or the Bluetooth module cannot reach it`() {
        assertTrue(declaration.contains("""android:exported="true"""")) {
            "the Bluetooth module sends $ACL_DISCONNECTED under its own uid, the receiver must be exported"
        }
    }

    @Test
    fun `the receiver requires no permission of its sender`() {
        assertFalse(declaration.contains("android:permission=")) {
            "a permission the Bluetooth module may not hold would silently cut the receiver off"
        }
    }

    private companion object {
        const val RECEIVER_NAME = """android:name=".system.BluetoothDisconnectReceiver""""

        const val ACL_DISCONNECTED = "android.bluetooth.device.action.ACL_DISCONNECTED"

        val ACTION = Regex("""<action android:name="([^"]+)" />""")

        val REPO_ROOT: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "fixtures/scenarios").isDirectory }
    }
}
