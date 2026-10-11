package app.cue.launcher.feature.widgets

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.system.widget.AvailableWidget
import app.cue.launcher.system.widget.WidgetSizeSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetFilterTest {

    private val calendar = widget(
        packageName = "com.google.android.calendar",
        label = "Měsíční přehled",
        appLabel = "Kalendář Google",
    )
    private val clock = widget(
        packageName = "com.android.deskclock",
        label = "Hodiny a datum",
        appLabel = "Hodiny",
    )
    private val all = listOf(calendar, clock)

    @Test
    fun `an empty query leaves the whole list`() {
        assertEquals(all, filterWidgets(all, "   "))
    }

    @Test
    fun `diacritics are ignored`() {
        assertEquals(listOf(calendar), filterWidgets(all, "mesicni"))
    }

    @Test
    fun `search also matches the app label`() {
        assertEquals(listOf(calendar), filterWidgets(all, "google"))
    }

    @Test
    fun `search also matches the package name`() {
        assertEquals(listOf(calendar), filterWidgets(all, "calen"))
    }

    @Test
    fun `only a word prefix matches`() {
        assertTrue(filterWidgets(all, "hod").contains(clock))
        assertFalse(filterWidgets(all, "odiny").contains(clock))
    }

    @Test
    fun `more words narrow the query`() {
        assertEquals(listOf(calendar), filterWidgets(all, "kalendar mesic"))
        assertEquals(emptyList<AvailableWidget>(), filterWidgets(all, "kalendar hodiny"))
    }

    @Test
    fun `a query with no match returns empty`() {
        assertEquals(emptyList<AvailableWidget>(), filterWidgets(all, "spotify"))
    }

    @Test
    fun `custom launcher widgets filter by label`() {
        val launcherWidgets = mapOf(
            LauncherWidgetType.CALENDAR to "Kalendář",
            LauncherWidgetType.CLOCK to "Hodiny",
            LauncherWidgetType.SEARCHBAR to "Vyhledávání",
        )

        assertEquals(3, filterLauncherWidgets(launcherWidgets, "").size)
        assertEquals(
            listOf(LauncherWidgetType.CALENDAR to "Kalendář"),
            filterLauncherWidgets(launcherWidgets, "kalendar"),
        )
        assertEquals(emptyList<Pair<LauncherWidgetType, String>>(), filterLauncherWidgets(launcherWidgets, "spotify"))
    }

    private fun widget(packageName: String, label: String, appLabel: String) = AvailableWidget(
        provider = ComponentRef(packageName, "$packageName.Provider", 0),
        label = label,
        appLabel = appLabel,
        needsConfiguration = false,
        size = WidgetSizeSpec(minWidthPx = 100, minHeightPx = 100, horizontallyResizable = false),
    )
}
