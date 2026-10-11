package app.cue.launcher.feature.drawer

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.system.HomeRoleStatus
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DrawerOwnEntryTest {

    private fun opens(component: ComponentRef, roleStatus: HomeRoleStatus = HomeRoleStatus.HELD) =
        opensLauncherSettings(component, OWN_PACKAGE, OWN_SERIAL, roleStatus)

    @Test
    fun `the own icon opens the settings while Cue is the default home`() {
        assertTrue(opens(OWN))
    }

    @Test
    fun `the own icon starts Cue as usual while it is only being tried out`() {
        assertFalse(opens(OWN, HomeRoleStatus.REQUESTABLE))
        assertFalse(opens(OWN, HomeRoleStatus.UNAVAILABLE))
    }

    @Test
    fun `another app is launched`() {
        assertFalse(opens(ComponentRef("com.android.chrome", "com.google.android.apps.chrome.Main", OWN_SERIAL)))
    }

    @Test
    fun `Cue in another profile is another installation and is launched`() {
        assertFalse(opens(OWN.copy(userSerial = WORK_SERIAL)))
    }

    private companion object {

        const val OWN_PACKAGE = "app.cue.launcher"

        const val OWN_SERIAL = 0L

        const val WORK_SERIAL = 10L

        val OWN = ComponentRef(OWN_PACKAGE, "$OWN_PACKAGE.MainActivity", OWN_SERIAL)
    }
}
