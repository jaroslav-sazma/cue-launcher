package app.cue.launcher.system.aichat

import app.cue.launcher.data.aichat.InventoryApp
import app.cue.launcher.data.aichat.InventoryAppAction
import app.cue.launcher.data.aichat.InventoryDevice
import app.cue.launcher.data.aichat.InventoryWidget
import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.ha.HaService
import app.cue.launcher.data.ha.HaServiceList
import app.cue.launcher.data.launchlog.LaunchStats
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.system.AppActionEntry
import app.cue.launcher.system.AppActionKind
import app.cue.launcher.system.ConnectedBtDevice
import app.cue.launcher.system.InstalledApp
import app.cue.launcher.system.SearchableApp
import app.cue.launcher.system.widget.AvailableWidget
import app.cue.launcher.system.widget.WidgetSizeSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeviceInventoryTest {

    @Test
    fun `in-app actions keep what a tile can run as it is, once each`() {
        val home = TapAction.Shortcut("com.maps", "home")
        val entries = listOf(
            AppActionEntry(AppActionKind.SHORTCUT, "Home", null, home),
            AppActionEntry(AppActionKind.CONFIGURABLE, "Route", null, null, configActivity = "com.maps.Pick"),
            AppActionEntry(AppActionKind.LINK, "Map", "geo:0,0", TapAction.DeepLink("geo:0,0")),
            AppActionEntry(AppActionKind.CAPABILITY, "Home again", null, home),
            AppActionEntry(AppActionKind.ACTIVITY, "Settings", "Settings", null),
        )

        assertEquals(
            listOf(InventoryAppAction("Home", home), InventoryAppAction("Map", TapAction.DeepLink("geo:0,0"))),
            inventoryActions(entries),
        )
    }

    @Test
    fun `an empty or failed services read is as unknown as none`() {
        val services = listOf(HaService("light.toggle", targets = emptyList()))

        assertEquals(services, offeredServices(HaServiceList(HaProbe.Ok(entities = 1, millis = 5), services)))
        assertEquals(null, offeredServices(HaServiceList(HaProbe.Ok(entities = 1, millis = 5))))
        assertEquals(null, offeredServices(HaServiceList(HaProbe.Http(status = 401), services)))
        assertEquals(null, offeredServices(HaServiceList(HaProbe.Timeout)))
        assertEquals(null, offeredServices(null))
    }

    private fun app(packageName: String, label: String, profile: Long = 0, english: String? = null) =
        SearchableApp(InstalledApp(ComponentRef(packageName, "$packageName.Main", profile), label), english)

    @Test
    fun `the most recently used apps lead and never-used ones keep their order`() {
        val searchable = listOf(app("com.a", "A"), app("com.b", "B"), app("com.c", "C"), app("com.d", "D"))
        val stats = mapOf(
            ComponentRef("com.c", "com.c.Main", 0) to LaunchStats(lastLaunchedAt = 300, searchHits = 0),
            ComponentRef("com.b", "com.b.Main", 0) to LaunchStats(lastLaunchedAt = 100, searchHits = 0),
        )

        val apps = recentFirst(searchable, stats)

        assertEquals(listOf("com.c", "com.b", "com.a", "com.d"), apps.map(InventoryApp::packageName))
    }

    @Test
    fun `an app in two profiles is listed once, ranked by its latest use in either`() {
        val searchable = listOf(app("com.a", "A"), app("com.mail", "Mail"), app("com.mail", "Mail", profile = 10))
        val stats = mapOf(
            ComponentRef("com.a", "com.a.Main", 0) to LaunchStats(lastLaunchedAt = 200, searchHits = 0),
            ComponentRef("com.mail", "com.mail.Main", 0) to LaunchStats(lastLaunchedAt = 100, searchHits = 0),
            ComponentRef("com.mail", "com.mail.Main", 10) to LaunchStats(lastLaunchedAt = 500, searchHits = 0),
        )

        val apps = recentFirst(searchable, stats)

        assertEquals(listOf("com.mail", "com.a"), apps.map(InventoryApp::packageName))
    }

    @Test
    fun `the English label travels with the app`() {
        val apps = recentFirst(listOf(app("com.maps", "Mapy", english = "Maps")), emptyMap())

        assertEquals(listOf(InventoryApp("Mapy", "com.maps", "Maps")), apps)
    }

    @Test
    fun `a widget provider in two profiles is listed once, with its labels`() {
        fun widget(className: String, profile: Long) = AvailableWidget(
            provider = ComponentRef("com.forecast", className, profile),
            label = "Weather",
            appLabel = "Forecast",
            needsConfiguration = false,
            size = WidgetSizeSpec(minWidthPx = 100, minHeightPx = 100, horizontallyResizable = false),
        )

        val widgets = inventoryWidgets(
            listOf(widget("com.forecast.Big", 0), widget("com.forecast.Big", 10), widget("com.forecast.Small", 0)),
        )

        assertEquals(
            listOf(
                InventoryWidget("Weather", "Forecast", "com.forecast", "com.forecast.Big", 100, 100),
                InventoryWidget("Weather", "Forecast", "com.forecast", "com.forecast.Small", 100, 100),
            ),
            widgets,
        )
    }

    @Test
    fun `a widget keeps the smallest size it can be resized to`() {
        val resizable = AvailableWidget(
            provider = ComponentRef("com.forecast", "com.forecast.Big", 0),
            label = "Weather",
            appLabel = "Forecast",
            needsConfiguration = false,
            size = WidgetSizeSpec(
                minWidthPx = 600,
                minHeightPx = 400,
                horizontallyResizable = true,
                verticallyResizable = false,
                minResizeWidthPx = 300,
                minResizeHeightPx = 200,
            ),
        )

        val widget = inventoryWidgets(listOf(resizable)).single()

        assertEquals(300 to 400, widget.smallestWidthPx to widget.smallestHeightPx)
    }

    @Test
    fun `a device both connected and paired is listed once`() {
        val car = ConnectedBtDevice("AA:BB:CC:DD:EE:FF", "Car")
        val buds = ConnectedBtDevice("11:22:33:44:55:66", null)

        val devices = inventoryDevices(listOf(car, car, buds))

        assertEquals(
            listOf(InventoryDevice("Car", "AA:BB:CC:DD:EE:FF"), InventoryDevice(null, "11:22:33:44:55:66")),
            devices,
        )
    }
}
