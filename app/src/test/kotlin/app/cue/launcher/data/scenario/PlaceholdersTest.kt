package app.cue.launcher.data.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaceholdersTest {

    @Test
    fun `keys are read in order of occurrence`() {
        assertEquals(
            listOf("wifi_home", "bt_car"),
            placeholderKeysIn("síť {{PLACEHOLDER:wifi_home}} a auto {{PLACEHOLDER:bt_car}}"),
        )
    }

    @Test
    fun `text without a placeholder returns nothing`() {
        listOf("", "Domov", "{{PLACEHOLDER}}", "{PLACEHOLDER:x}", "{{placeholder:x}}").forEach { text ->
            assertTrue(placeholderKeysIn(text).isEmpty()) { "vstup: $text" }
            assertFalse(hasPlaceholder(text)) { "vstup: $text" }
        }
    }

    @Test
    fun `a placeholder is found in the provider package and in the HA entity too`() {
        val scenario = Scenario(
            id = "s1",
            name = "Scénář",
            size = TileSize.HERO_4X2,
            conditions = listOf(
                Condition.Wifi(mode = WifiMode.SSID, ssid = "{{PLACEHOLDER:wifi_home}}"),
                Condition.BtDisconnected(mac = "{{PLACEHOLDER:bt_car}}"),
            ),
            content = TileContent.AppWidget(
                packageName = "{{PLACEHOLDER:widget_hero_pkg}}",
                provider = "{{PLACEHOLDER:widget_hero_provider}}",
                appWidgetId = "{{PLACEHOLDER:widget_hero}}",
            ),
        )

        assertEquals(
            setOf(
                "wifi_home",
                "bt_car",
                "widget_hero_pkg",
                "widget_hero_provider",
                "widget_hero",
            ),
            placeholderKeysIn(scenario),
        )
    }

    @Test
    fun `a placeholder is found in a template item too`() {
        val scenario = Scenario(
            id = "s1",
            name = "Scénář",
            size = TileSize.GRID_1X1,
            content = TileContent.Template(
                layout = TemplateLayout.ICON_TEXT,
                items = listOf(
                    TemplateItem(
                        label = "Brána",
                        actions = listOf(
                            TapAction.HaAction(
                                service = "cover.open_cover",
                                entity = "{{PLACEHOLDER:ha_brana}}",
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(setOf("ha_brana"), placeholderKeysIn(scenario))
    }

    @Test
    fun `a scenario without placeholders has none`() {
        val scenario = Scenario(
            id = "s1",
            name = "Scénář",
            size = TileSize.GRID_1X1,
            conditions = listOf(Condition.Charging()),
            content = TileContent.Template(
                layout = TemplateLayout.ICON_TEXT,
                items = listOf(TemplateItem(actions = listOf(TapAction.App("com.x")))),
            ),
        )

        assertTrue(placeholderKeysIn(scenario).isEmpty())
    }

    @Test
    fun `a bound widget ID is read as a number`() {
        assertEquals(
            8,
            TileContent.AppWidget(packageName = "com.x", provider = "com.x.W", appWidgetId = "8")
                .boundAppWidgetId,
        )
        assertEquals(
            null,
            TileContent.AppWidget(
                packageName = "com.x",
                provider = "com.x.W",
                appWidgetId = "{{PLACEHOLDER:widget_x}}",
            ).boundAppWidgetId,
        )
    }

    @Test
    fun `a widget placeholder key is derived from the ID`() {
        assertEquals("widget_8", widgetPlaceholderKey(8))
    }
}
