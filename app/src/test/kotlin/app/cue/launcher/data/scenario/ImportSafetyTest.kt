package app.cue.launcher.data.scenario

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    private fun withActions(vararg actions: TapAction) = scenario(
        "a",
        content = TileContent.Template(
            layout = TemplateLayout.ICON_TEXT,
            items = listOf(TemplateItem(actions = actions.toList())),
        ),
    )

    private val paddedIntent = " intent://maps.google.com/#Intent;scheme=https;component=com.evil/.Pay;end \n"

    @Test
    fun `a padded link is stored trimmed`() {
        val tidied = withActions(TapAction.DeepLink(paddedIntent), TapAction.App("com.x")).tidied()

        val actions = (tidied.content as TileContent.Template).items.single().actions
        assertEquals(listOf(TapAction.DeepLink(paddedIntent.trim()), TapAction.App("com.x")), actions)
    }

    @Test
    fun `an imported padded link arrives trimmed`() {
        val text = ScenarioFormat.encodeScenario(withActions(TapAction.DeepLink(paddedIntent)))

        val imported = read(text).accepted.single()

        val link = (imported.scenario.content as TileContent.Template).items.single().primaryAction
        assertEquals(TapAction.DeepLink(paddedIntent.trim()), link)
    }

    @Test
    fun `a padded link in a backup's gestures arrives trimmed`() {
        val text = ScenarioFormat.encodeConfig(
            scenarios = emptyList(),
            config = LauncherConfig(gestures = GesturesConfig(swipeLeft = TapAction.DeepLink(paddedIntent))),
        )

        assertEquals(TapAction.DeepLink(paddedIntent.trim()), read(text).config?.gestures?.swipeLeft)
    }

    @Test
    fun `a scenario with a megabyte name is rejected by naming the field`() {
        val ok = read(ScenarioFormat.encodeScenario(scenario("big").copy(name = "x".repeat(1_000_000))))

        assertTrue(ok.accepted.isEmpty())
        assertEquals(
            listOf(ScenarioDefect.TooLong("name", SCENARIO_NAME_MAX_LENGTH)),
            ok.rejected.single().defects,
        )
    }

    @Test
    fun `a name at the cap is accepted`() {
        val edge = scenario("edge").copy(name = "x".repeat(SCENARIO_NAME_MAX_LENGTH))

        assertEquals(1, read(ScenarioFormat.encodeScenario(edge)).accepted.size)
    }

    @Test
    fun `a link and an HA service past their caps are rejected, at the caps accepted`() {
        val link = "https://example.com/?q="
        val longLink = TapAction.DeepLink(link + "x".repeat(SCENARIO_LINK_MAX_LENGTH - link.length + 1))
        val longService = TapAction.HaAction(service = "script." + "x".repeat(HA_ID_MAX_LENGTH))

        val rejected = read(ScenarioFormat.encodeScenario(withActions(longLink, longService))).rejected.single()

        assertEquals(
            listOf(
                ScenarioDefect.TooLong("deeplink.uri", SCENARIO_LINK_MAX_LENGTH),
                ScenarioDefect.TooLong("ha.service", HA_ID_MAX_LENGTH),
            ),
            rejected.defects,
        )

        val atCaps = withActions(
            TapAction.DeepLink(longLink.uri.dropLast(1)),
            TapAction.HaAction(service = longService.service.take(HA_ID_MAX_LENGTH)),
        )
        assertEquals(1, read(ScenarioFormat.encodeScenario(atCaps)).accepted.size)
    }

    @Test
    fun `long texts in conditions and on the tile are rejected`() {
        val long = "x".repeat(SCENARIO_TEXT_MAX_LENGTH + 1)
        val notification = Condition.NotificationFrom(packageName = "com.x", contains = long)
        val tile = TileContent.Template(
            layout = TemplateLayout.TEXT_ONLY,
            text = long,
            items = listOf(TemplateItem(label = long, actions = listOf(TapAction.App("com.x")))),
        )
        val hostile = scenario("t", content = tile, size = TileSize.GRID_2X2).copy(conditions = listOf(notification))

        val rejected = read(ScenarioFormat.encodeScenario(hostile)).rejected.single()

        assertEquals(
            listOf("notification.contains", "text", "items[].label"),
            rejected.defects.map { (it as ScenarioDefect.TooLong).field },
        )
    }

    private fun haWithData(chars: Int): TapAction.HaAction {
        val frame = JsonObject(mapOf("code" to JsonPrimitive(""))).toString().length
        return TapAction.HaAction(
            service = "alarm_control_panel.alarm_disarm",
            data = JsonObject(mapOf("code" to JsonPrimitive("x".repeat(chars - frame)))),
        )
    }

    @Test
    fun `HA service data past the link cap is rejected, at the cap accepted`() {
        val over = read(ScenarioFormat.encodeScenario(withActions(haWithData(SCENARIO_LINK_MAX_LENGTH + 1))))

        assertEquals(
            listOf(ScenarioDefect.TooLong("ha.data", SCENARIO_LINK_MAX_LENGTH)),
            over.rejected.single().defects,
        )
        val atCap = withActions(haWithData(SCENARIO_LINK_MAX_LENGTH))
        assertEquals(1, read(ScenarioFormat.encodeScenario(atCap)).accepted.size)
    }

    @Test
    fun `an HA entity takes a comma list up to the text cap, a state entity a single id`() {
        val ids = List(60) { "light.room_$it" }.joinToString(", ")
        val list = TapAction.HaAction(service = "light.turn_on", entity = ids)
        assertTrue(ids.length > HA_ID_MAX_LENGTH)
        assertEquals(1, read(ScenarioFormat.encodeScenario(withActions(list))).accepted.size)

        val longList = list.copy(entity = "x".repeat(SCENARIO_TEXT_MAX_LENGTH + 1))
        val longState = TapAction.HaAction(service = "light.turn_on", stateEntity = "x".repeat(HA_ID_MAX_LENGTH + 1))
        val rejected = read(ScenarioFormat.encodeScenario(withActions(longList, longState))).rejected.single()

        assertEquals(
            listOf(
                ScenarioDefect.TooLong("ha.entity", SCENARIO_TEXT_MAX_LENGTH),
                ScenarioDefect.TooLong("ha.stateEntity", HA_ID_MAX_LENGTH),
            ),
            rejected.defects,
        )
    }

    @Test
    fun `a backup whose gesture action is too long is refused by naming it`() {
        val link = TapAction.DeepLink("https://example.com/?q=" + "x".repeat(SCENARIO_LINK_MAX_LENGTH))
        val text = ScenarioFormat.encodeConfig(
            scenarios = emptyList(),
            config = LauncherConfig(gestures = GesturesConfig(searchbarTap = link)),
        )

        assertEquals(
            ScenarioRead.Failed(ScenarioDefect.TooLong("gestures.searchbarTap.deeplink.uri", SCENARIO_LINK_MAX_LENGTH)),
            ScenarioFormat.read(text),
        )
    }

    // Past the library's own switch to deep recursion, a tree like this parses and then overflows
    // the stack in toString() or validation, uncaught on a Default worker.
    @Test
    fun `deeply nested ha data is refused as unreadable instead of overflowing`() {
        val marker = "\"NEST\""
        val data = JsonObject(mapOf("x" to JsonPrimitive("NEST")))
        val action = TapAction.HaAction(service = "light.turn_on", data = data)
        val encoded = ScenarioFormat.encodeScenario(withActions(action))
        fun nested(depth: Int) = encoded.replace(marker, "[".repeat(depth) + "]".repeat(depth))

        assertEquals(ScenarioRead.Failed(ScenarioDefect.Malformed.NotJsonObject), ScenarioFormat.read(nested(300)))
        assertEquals(1, read(nested(20)).accepted.size)
    }
}
