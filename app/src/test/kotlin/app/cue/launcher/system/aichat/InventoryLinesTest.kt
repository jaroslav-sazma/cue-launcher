package app.cue.launcher.system.aichat

import app.cue.launcher.data.aichat.InventoryApp
import app.cue.launcher.data.aichat.InventoryAppAction
import app.cue.launcher.data.aichat.InventoryDevice
import app.cue.launcher.data.aichat.InventorySnapshot
import app.cue.launcher.data.aichat.InventoryWidget
import app.cue.launcher.data.ha.HaEntity
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.system.widget.WidgetBox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InventoryLinesTest {

    private val clock = InventoryApp("Hodiny", "com.google.android.deskclock", englishLabel = "Clock")
    private val weather = InventoryWidget("Počasí", "Předpověď", "com.forecast", "com.forecast.widget.Weather")
    private val clockWidget = InventoryWidget("Clock widget", "Clock", "com.clock", "com.clock.ClockWidget")

    @Test
    fun `lines carry one entry each with its identifier as the scenario spells it`() {
        val inventory = InventorySnapshot(
            apps = listOf(
                clock,
                InventoryApp("Maps\nEvil", "com.maps"),
                InventoryApp("Mapy", "cz.mapy", englishLabel = "mapy"),
            ),
            devices = listOf(InventoryDevice(null, "AA:00:00:00:00:01")),
            haEntities = listOf(HaEntity("cover.gate", "Gate"), HaEntity("light.x", null)),
            widgets = listOf(weather, clockWidget, InventoryWidget("Radar", "Radar", "com.radar", "org.other.Radar")),
        )

        assertEquals(
            listOf(
                "app Hodiny (Clock) → com.google.android.deskclock",
                "app Maps Evil → com.maps",
                "app Mapy → cz.mapy",
                "bt Unnamed device → AA:00:00:00:00:01",
                "ha Gate → cover.gate",
                "ha light.x",
                "widget Počasí (Předpověď) → com.forecast/com.forecast.widget.Weather",
                "widget Clock widget (Clock) → com.clock/com.clock.ClockWidget",
                "widget Radar → com.radar/org.other.Radar",
            ),
            inventoryLines(inventory),
        )
    }

    @Test
    fun `a widget line lists the tile sizes whose slot it fits, and one that fits none is left out`() {
        val slots = mapOf(
            TileSize.GRID_1X1 to WidgetBox(widthPx = 100, heightPx = 100),
            TileSize.GRID_2X2 to WidgetBox(widthPx = 400, heightPx = 400),
            TileSize.HERO_4X2 to WidgetBox(widthPx = 900, heightPx = 400),
        )
        val small = weather.copy(smallestWidthPx = 300, smallestHeightPx = 200)
        val wide = clockWidget.copy(smallestWidthPx = 800, smallestHeightPx = 400)
        val huge = InventoryWidget("Radar", "Radar", "com.radar", "org.other.Radar", 1000, 800)

        assertEquals(
            listOf(
                "widget Počasí (Předpověď) [2x2, 4x2] → com.forecast/com.forecast.widget.Weather",
                "widget Clock widget (Clock) [4x2] → com.clock/com.clock.ClockWidget",
            ),
            widgetLines(listOf(small, wide, huge), slots),
        )
    }

    @Test
    fun `nothing is left out for length`() {
        val apps = (1..200).map { InventoryApp("App $it", "org.app$it") }
        val entities = (1..500).map { HaEntity("sensor.s$it", "Sensor $it") }

        val lines = inventoryLines(InventorySnapshot(apps = apps, haEntities = entities))

        assertEquals(apps.size + entities.size, lines.size)
    }

    @Test
    fun `an app's actions follow its own line, shortcuts, links, capabilities, then activities`() {
        val maps = InventoryApp("Maps", "com.maps")
        val actions = listOf(
            InventoryAppAction("Settings", TapAction.ActivityLaunch("com.maps", "com.maps.SettingsActivity")),
            InventoryAppAction("Directions", TapAction.Capability("com.maps", "actions.intent.GET_DIRECTIONS")),
            InventoryAppAction("Browse", TapAction.DeepLink("geo:0,0")),
            InventoryAppAction("Explore", TapAction.Shortcut("com.maps", "explore")),
            InventoryAppAction("Spaced", TapAction.Shortcut("com.maps", "a b")),
        )
        val inventory = InventorySnapshot(
            apps = listOf(maps, InventoryApp("Clock", "com.clock")),
            appActions = mapOf("com.maps" to actions),
        )

        assertEquals(
            listOf(
                "app Maps → com.maps",
                "shortcut Explore → com.maps/explore",
                "deeplink Browse → geo:0,0",
                "capability Directions → com.maps/actions.intent.GET_DIRECTIONS",
                "activity Settings → com.maps/com.maps.SettingsActivity",
                "app Clock → com.clock",
            ),
            inventoryLines(inventory),
        )
    }

    @Test
    fun `a label is flattened to one line and capped`() {
        assertEquals("Kitchen light", tidyLabel(" Kitchen\n\tlight "))
        assertEquals(32, tidyLabel("x".repeat(100)).length)
    }

    @Test
    fun `an empty inventory gives no lines`() {
        assertTrue(inventoryLines(InventorySnapshot.EMPTY).isEmpty())
    }
}
