package app.cue.launcher.system

import android.content.Intent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeepLinkIntentTest {

    private val own = "app.cue.launcher"

    @Test
    fun `a link into another app is allowed`() {
        assertTrue(
            deepLinkTargetAllowed(
                packageName = "com.google.android.apps.chromecast.app",
                componentPackage = null,
                dataScheme = "googlehome",
                ownPackage = own,
            ),
        )
    }

    @Test
    fun `a link that turns back on the launcher is refused`() {
        assertFalse(deepLinkTargetAllowed(own, null, "cue", own))
        assertFalse(deepLinkTargetAllowed(null, own, "cue", own))
    }

    @Test
    fun `a file target is refused whatever the case`() {
        assertFalse(deepLinkTargetAllowed(null, null, "file", own))
        assertFalse(deepLinkTargetAllowed(null, null, "FILE", own))
    }

    @Test
    fun `a link without a target at all is allowed`() {
        assertTrue(deepLinkTargetAllowed(null, null, null, own))
    }

    @Test
    fun `uri permission grants never survive`() {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
            Intent.FLAG_ACTIVITY_NEW_TASK

        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, sanitizedDeepLinkFlags(flags))
    }

    @Test
    fun `flags a link may keep are kept`() {
        val flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        assertEquals(flags, sanitizedDeepLinkFlags(flags))
    }
}
