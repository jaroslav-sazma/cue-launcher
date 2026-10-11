package app.cue.launcher.data.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DockSeedTest {

    @Test
    fun `roles have fixed positions phone, messages, camera, browser`() {
        assertEquals(listOf(0, 1, 2, 3), DockRole.entries.map { it.dockPosition })
        assertEquals(0, DockRole.PHONE.dockPosition)
        assertEquals(3, DockRole.BROWSER.dockPosition)
        assertTrue(DockRole.entries.all { LayoutContainer.DOCK.isValidPosition(it.dockPosition) })
    }

    @Test
    fun `found apps are each placed at their own position`() {
        val placements = dockSeedPlacements(
            mapOf(
                DockRole.PHONE to component("com.android.dialer"),
                DockRole.MESSAGES to component("com.android.messaging"),
                DockRole.CAMERA to component("com.android.camera"),
                DockRole.BROWSER to component("com.android.chrome"),
            ),
        )

        assertEquals(4, placements.size)
        assertEquals("com.android.dialer", placements[0]?.packageName)
        assertEquals("com.android.messaging", placements[1]?.packageName)
        assertEquals("com.android.camera", placements[2]?.packageName)
        assertEquals("com.android.chrome", placements[3]?.packageName)
    }

    @Test
    fun `an unfound role leaves a gap, the others do not shift`() {
        val placements = dockSeedPlacements(
            mapOf(
                DockRole.PHONE to null,
                DockRole.MESSAGES to null,
                DockRole.CAMERA to component("com.android.camera"),
                DockRole.BROWSER to component("com.android.chrome"),
            ),
        )

        assertFalse(placements.containsKey(0))
        assertFalse(placements.containsKey(1))
        assertEquals("com.android.camera", placements[2]?.packageName)
        assertEquals("com.android.chrome", placements[3]?.packageName)
    }

    @Test
    fun `an app in two roles is placed only once, the earlier role wins`() {
        val both = component("com.google.android.apps.messaging")

        val placements = dockSeedPlacements(
            mapOf(
                DockRole.PHONE to both,
                DockRole.MESSAGES to both,
                DockRole.BROWSER to component("com.android.chrome"),
            ),
        )

        assertEquals(both, placements[0])
        assertFalse(placements.containsKey(1))
        assertEquals(setOf(0, 3), placements.keys)
    }

    @Test
    fun `a device without default apps produces no writes`() {
        assertTrue(dockSeedPlacements(emptyMap()).isEmpty())
        assertTrue(dockSeedPlacements(DockRole.entries.associateWith { null }).isEmpty())
    }

    private fun component(packageName: String) = ComponentRef(
        packageName = packageName,
        className = "$packageName.MainActivity",
        userSerial = 0,
    )
}
