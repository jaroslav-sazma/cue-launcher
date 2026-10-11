package app.cue.launcher.system

import app.cue.launcher.data.scenario.TapAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ShortcutConfigTest {

    @Test
    fun `a legacy shortcut with a URI becomes a link`() {
        val action = legacyShortcutAction(
            legacy(action = "android.intent.action.VIEW", uri = "content://com.android.calendar/time/1"),
        )

        assertEquals(TapAction.DeepLink("content://com.android.calendar/time/1"), action)
    }

    @Test
    fun `a legacy shortcut with a component becomes an app screen`() {
        val action = legacyShortcutAction(
            legacy(
                action = "android.intent.action.MAIN",
                packageName = "com.microsoft.office.outlook",
                className = "com.microsoft.office.outlook.CalendarActivity",
            ),
        )

        assertEquals(
            TapAction.ActivityLaunch(
                packageName = "com.microsoft.office.outlook",
                component = "com.microsoft.office.outlook.CalendarActivity",
            ),
            action,
        )
    }

    @Test
    fun `a legacy shortcut carrying extras is turned down`() {
        val action = legacyShortcutAction(
            legacy(
                action = "android.intent.action.MAIN",
                packageName = "com.microsoft.office.outlook",
                className = "com.microsoft.office.outlook.CalendarActivity",
                hasExtras = true,
            ),
        )

        assertNull(action)
    }

    @Test
    fun `a legacy shortcut with an action that cannot be stored is turned down`() {
        assertNull(legacyShortcutAction(legacy(action = "android.intent.action.SEND", uri = "mailto:someone")))
        assertNull(legacyShortcutAction(legacy(action = null, uri = null)))
        assertNull(legacyShortcutAction(legacy(action = "android.intent.action.MAIN", uri = null, className = "  ")))
    }

    private fun legacy(
        action: String?,
        uri: String? = null,
        packageName: String? = null,
        className: String? = null,
        hasExtras: Boolean = false,
    ) = LegacyShortcut(
        action = action,
        uri = uri,
        packageName = packageName,
        className = className,
        hasExtras = hasExtras,
    )
}
