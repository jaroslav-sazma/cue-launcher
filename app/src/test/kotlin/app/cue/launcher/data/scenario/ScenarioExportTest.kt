package app.cue.launcher.data.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScenarioExportTest {

    private fun scenario(
        id: String = "s1",
        conditions: List<Condition> = emptyList(),
        content: TileContent = TileContent.LauncherWidget(LauncherWidgetType.CALENDAR),
        size: TileSize = TileSize.HERO_4X2,
    ) = Scenario(id = id, name = "Scénář", size = size, conditions = conditions, content = content)

    @Test
    fun `a bt mac is replaced with a placeholder`() {
        val portable = portableScenario(
            scenario(conditions = listOf(Condition.BtConnected(mac = "AA:BB:CC:DD:EE:FF"))),
        )

        val condition = portable.conditions.single() as Condition.BtConnected
        assertEquals("{{PLACEHOLDER:bt_1}}", condition.mac)
    }

    @Test
    fun `the original value does not remain anywhere in the export`() {
        val json = ScenarioFormat.encodeScenario(
            portableScenario(
                scenario(
                    conditions = listOf(
                        Condition.BtConnected(mac = "AA:BB:CC:DD:EE:FF"),
                        Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma-5G"),
                    ),
                ),
            ),
        )

        assertFalse(json.contains("AA:BB"))
        assertFalse(json.contains("Doma-5G"))
    }

    @Test
    fun `the same value in two scenarios gets the same key`() {
        val portable = portableScenarios(
            listOf(
                scenario(id = "a", conditions = listOf(Condition.BtConnected(mac = "AA:BB"))),
                scenario(id = "b", conditions = listOf(Condition.BtDisconnected(mac = "AA:BB"))),
                scenario(id = "c", conditions = listOf(Condition.BtConnected(mac = "CC:DD"))),
            ),
        )

        assertEquals("{{PLACEHOLDER:bt_1}}", (portable[0].conditions.single() as Condition.BtConnected).mac)
        assertEquals("{{PLACEHOLDER:bt_1}}", (portable[1].conditions.single() as Condition.BtDisconnected).mac)
        assertEquals("{{PLACEHOLDER:bt_2}}", (portable[2].conditions.single() as Condition.BtConnected).mac)
    }

    @Test
    fun `a bt mac an end event names is replaced on every instant condition, sharing the device's key`() {
        val portable = portableScenarios(
            listOf(
                scenario(
                    id = "a",
                    conditions = listOf(
                        Condition.BtDisconnected(
                            mac = "AA:BB:CC:DD:EE:FF",
                            until = EndEvent.BtDisconnected(mac = "11:22:33:44:55:66"),
                        ),
                    ),
                ),
                scenario(
                    id = "b",
                    conditions = listOf(
                        Condition.HaEvent(
                            code = "doorbell",
                            until = EndEvent.BtDisconnected(mac = "AA:BB:CC:DD:EE:FF"),
                        ),
                        Condition.Alarm(until = EndEvent.BtDisconnected()),
                        Condition.PlaceSaved(until = EndEvent.BtDisconnected(mac = "{{PLACEHOLDER:bt_car}}")),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                Condition.BtDisconnected(
                    mac = "{{PLACEHOLDER:bt_1}}",
                    until = EndEvent.BtDisconnected(mac = "{{PLACEHOLDER:bt_2}}"),
                ),
                Condition.HaEvent(code = "doorbell", until = EndEvent.BtDisconnected(mac = "{{PLACEHOLDER:bt_1}}")),
                Condition.Alarm(until = EndEvent.BtDisconnected()),
                Condition.PlaceSaved(until = EndEvent.BtDisconnected(mac = "{{PLACEHOLDER:bt_car}}")),
            ),
            portable.flatMap { it.conditions },
        )
        val json = ScenarioFormat.encodeSet(name = "Sada", scenarios = portable)
        assertFalse(json.contains("AA:BB")) { json }
        assertFalse(json.contains("11:22")) { json }
    }

    @Test
    fun `an end event mac comes back through import as a placeholder to fill in`() {
        val original = scenario(
            conditions = listOf(
                Condition.NotificationFrom(
                    packageName = "com.example.parcel",
                    until = EndEvent.BtDisconnected(mac = "AA:BB:CC:DD:EE:FF"),
                ),
            ),
        )
        val text = ScenarioFormat.encodeSet(name = "Sada", scenarios = portableScenarios(listOf(original)))

        val plan = importPlanOf(ScenarioFormat.read(text) as ScenarioRead.Ok, existingIds = emptySet())
        val entry = plan.entries.single()
        assertEquals(setOf("bt_1"), entry.placeholders)
        assertFalse(entry.scenario.enabled)

        val imported = entry.scenario.conditions.single() as Condition.NotificationFrom
        assertEquals(EndEvent.BtDisconnected(mac = "{{PLACEHOLDER:bt_1}}"), imported.until)
        val filled = entry.scenario.copy(
            enabled = original.enabled,
            conditions = listOf(imported.withUntil(EndEvent.BtDisconnected(mac = "AA:BB:CC:DD:EE:FF"))),
        )
        assertEquals(original, filled)
        assertTrue(validateScenario(filled).isEmpty())
        assertTrue(placeholderKeysIn(filled).isEmpty())
    }

    @Test
    fun `an ssid is replaced and the number is its own series`() {
        val portable = portableScenario(
            scenario(
                conditions = listOf(
                    Condition.BtConnected(mac = "AA:BB"),
                    Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma"),
                ),
            ),
        )

        assertEquals("{{PLACEHOLDER:bt_1}}", (portable.conditions[0] as Condition.BtConnected).mac)
        assertEquals("{{PLACEHOLDER:wifi_1}}", (portable.conditions[1] as Condition.Wifi).ssid)
    }

    @Test
    fun `a missing value stays empty`() {
        val portable = portableScenario(
            scenario(
                conditions = listOf(
                    Condition.BtConnected(mac = null),
                    Condition.Wifi(mode = WifiMode.ANY),
                ),
            ),
        )

        assertNull((portable.conditions[0] as Condition.BtConnected).mac)
        assertNull((portable.conditions[1] as Condition.Wifi).ssid)
    }

    @Test
    fun `a value that is already a placeholder is left as is`() {
        val portable = portableScenario(
            scenario(conditions = listOf(Condition.BtConnected(mac = "{{PLACEHOLDER:bt_car}}"))),
        )

        assertEquals("{{PLACEHOLDER:bt_car}}", (portable.conditions.single() as Condition.BtConnected).mac)
    }

    @Test
    fun `a bound widget id is replaced with a key by number`() {
        val portable = portableScenario(
            scenario(
                content = TileContent.AppWidget(
                    packageName = "com.example",
                    provider = "com.example.Widget",
                    appWidgetId = "8",
                ),
            ),
        )

        val content = portable.content as TileContent.AppWidget
        assertEquals("{{PLACEHOLDER:widget_8}}", content.appWidgetId)
        assertEquals("com.example", content.packageName)
        assertEquals("com.example.Widget", content.provider)
    }

    @Test
    fun `an unbound widget id stays`() {
        val content = TileContent.AppWidget(
            packageName = "com.example",
            provider = "com.example.Widget",
            appWidgetId = "{{PLACEHOLDER:widget_hero}}",
        )

        assertEquals(content, portableScenario(scenario(content = content)).content)
    }

    @Test
    fun `export followed by import leave the scenario disabled`() {
        val text = ScenarioFormat.encodeSet(
            name = "Sada",
            scenarios = portableScenarios(
                listOf(scenario(conditions = listOf(Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma")))),
            ),
        )

        val plan = importPlanOf(ScenarioFormat.read(text) as ScenarioRead.Ok, existingIds = emptySet())
        val entry = plan.entries.single()
        assertEquals(setOf("wifi_1"), entry.placeholders)
        assertFalse(entry.scenario.enabled)
        assertTrue(plan.rejected.isEmpty())
    }

    @Test
    fun `a portable scenario passes validation`() {
        val portable = portableScenario(
            scenario(conditions = listOf(Condition.Wifi(mode = WifiMode.SSID, ssid = "Doma"))),
        )

        assertTrue(validateScenario(portable).isEmpty())
    }
}
