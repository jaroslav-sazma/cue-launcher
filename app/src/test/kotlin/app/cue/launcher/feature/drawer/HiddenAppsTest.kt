package app.cue.launcher.feature.drawer

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.system.InstalledApp
import app.cue.launcher.system.SearchableApp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class HiddenAppsTest {

    private fun app(packageName: String, userSerial: Long = 0L) = SearchableApp(
        InstalledApp(
            component = ComponentRef(packageName, "$packageName.Main", userSerial),
            label = packageName,
        ),
    )

    @Test
    fun `a hidden app drops out of the list`() {
        val apps = listOf(app("com.a"), app("com.b"))

        val visible = visibleApps(apps, setOf(apps[0].app.component))

        assertEquals(listOf(apps[1]), visible)
    }

    @Test
    fun `hiding only applies to its own profile`() {
        val personal = app("com.a", userSerial = 0L)
        val work = app("com.a", userSerial = 10L)

        val visible = visibleApps(listOf(personal, work), setOf(personal.app.component))

        assertEquals(listOf(work), visible)
    }

    @Test
    fun `an empty set returns the same list`() {
        val apps = listOf(app("com.a"))

        assertSame(apps, visibleApps(apps, emptySet()))
    }
}
