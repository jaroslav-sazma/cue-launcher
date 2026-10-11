package app.cue.launcher.system.notifications

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class NotificationListenerManifestTest {

    private val declaration: String by lazy {
        val manifest = File(REPO_ROOT, "app/src/main/AndroidManifest.xml")
        assertTrue(manifest.isFile) { "the manifest is not in the repository" }
        manifest.readText()
            .substringAfter(SERVICE_NAME, missingDelimiterValue = "")
            .substringBefore("</service>")
    }

    @Test
    fun `the manifest declares the listener service`() {
        assertTrue(declaration.isNotEmpty()) { "the manifest has no $SERVICE_NAME service" }
    }

    @Test
    fun `the service listens on the listener action`() {
        assertTrue(declaration.contains("""<action android:name="$LISTENER_ACTION" />""")) {
            "the service has no $LISTENER_ACTION action"
        }
    }

    @Test
    fun `the service is exported and protected by the bind permission`() {
        assertTrue(declaration.contains("""android:exported="true"""")) {
            "the listener service must be exported=\"true\""
        }
        assertTrue(declaration.contains("""android:permission="$BIND_PERMISSION"""")) {
            "the listener service must be protected by the $BIND_PERMISSION permission"
        }
    }

    private companion object {
        const val SERVICE_NAME = """android:name=".system.notifications.CueNotificationListener""""

        const val LISTENER_ACTION = "android.service.notification.NotificationListenerService"

        const val BIND_PERMISSION = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"

        val REPO_ROOT: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "fixtures/scenarios").isDirectory }
    }
}
