package app.cue.launcher.data.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImportSafetyTest {

    private fun scenario(id: String, content: TileContent = template, size: TileSize = TileSize.GRID_1X1) =
        Scenario(id = id, name = "Scénář $id", size = size, content = content)

    private val template = TileContent.Template(
        layout = TemplateLayout.ICON_TEXT,
        items = listOf(TemplateItem(actions = listOf(TapAction.App(packageName = "com.example")))),
    )

    private fun read(text: String): ScenarioRead.Ok {
        val read = ScenarioFormat.read(text)
        assertTrue(read is ScenarioRead.Ok) { "expected Ok, got $read" }
        return read as ScenarioRead.Ok
    }

    @Test
    fun `a file at the cap is read whole`() {
        val scenarios = List(MAX_IMPORTED_SCENARIOS) { scenario("s$it") }

        val ok = read(ScenarioFormat.encodeSet("Sada", scenarios))

        assertEquals(MAX_IMPORTED_SCENARIOS, ok.accepted.size)
        assertEquals(0, ok.skipped)
    }

    @Test
    fun `scenarios past the cap are counted as skipped and never decoded`() {
        val scenarios = List(MAX_IMPORTED_SCENARIOS + 3) { scenario("s$it") }

        val ok = read(ScenarioFormat.encodeSet("Sada", scenarios))

        assertEquals(MAX_IMPORTED_SCENARIOS, ok.accepted.size)
        assertEquals(3, ok.skipped)
        assertEquals("s${MAX_IMPORTED_SCENARIOS - 1}", ok.accepted.last().scenario.id)
    }

    @Test
    fun `rejected scenarios inside the cap still count against it`() {
        val notObjects = List(MAX_IMPORTED_SCENARIOS + 1) { "42" }.joinToString(",")
        val text = """{"schemaVersion":1,"scenarios":[$notObjects]}"""

        val ok = read(text)

        assertEquals(MAX_IMPORTED_SCENARIOS, ok.rejected.size)
        assertEquals(1, ok.skipped)
    }

    @Test
    fun `the skipped count travels into the plan`() {
        val plan = importPlanOf(ScenarioRead.Ok(skipped = 7), existingIds = emptySet())

        assertEquals(7, plan.skipped)
    }

    @Test
    fun `a concrete widget id in a file is imported unbound and disabled`() {
        val widget = TileContent.AppWidget(packageName = "com.x", provider = "com.x.Widget", appWidgetId = "42")
        val text = ScenarioFormat.encodeScenario(scenario("w", content = widget, size = TileSize.HERO_4X2))

        val imported = read(text).accepted.single()
        val content = imported.scenario.content as TileContent.AppWidget

        assertNull(content.boundAppWidgetId, "the tile must not point at widget 42 this launcher may host")
        assertEquals(placeholderFor(widgetPlaceholderKey(42)), content.appWidgetId)
        assertEquals(setOf("widget_42"), imported.placeholders)
        assertFalse(imported.asStored().enabled)
    }

    @Test
    fun `a concrete widget id is unbound the same way the exported placeholder is`() {
        val bound = scenario(
            "w",
            content = TileContent.AppWidget(packageName = "com.x", provider = "com.x.Widget", appWidgetId = "42"),
            size = TileSize.HERO_4X2,
        )
        val fromNumber = read(ScenarioFormat.encodeScenario(bound)).accepted.single()
        val fromExport = read(ScenarioFormat.encodeScenario(portableScenario(bound))).accepted.single()

        assertEquals(fromExport, fromNumber)
    }

    @Test
    fun `a widget placeholder is left as it is`() {
        val widget = TileContent.AppWidget(
            packageName = "com.x",
            provider = "com.x.Widget",
            appWidgetId = placeholderFor("widget_hero"),
        )
        val text = ScenarioFormat.encodeScenario(scenario("w", content = widget, size = TileSize.HERO_4X2))

        val imported = read(text).accepted.single()

        assertEquals(widget, imported.scenario.content)
        assertEquals(setOf("widget_hero"), imported.placeholders)
    }
}
