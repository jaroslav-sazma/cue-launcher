package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.scenario.GesturesConfig
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioFormat
import app.cue.launcher.data.scenario.ScenarioRead
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.system.ActionRoute
import app.cue.launcher.system.DeepLinkTarget
import app.cue.launcher.system.actionRouteOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImportDisclosureTest {

    private val disarm = TapAction.HaAction(
        service = "alarm_control_panel.alarm_disarm",
        entity = "alarm_control_panel.home",
    )

    private val link = TapAction.DeepLink("intent:#Intent;component=com.x/.Pay;end")

    @Test
    fun `every item of a template discloses every one of its actions`() {
        val content = TileContent.Template(
            layout = TemplateLayout.TEXT_TWO_ICONS,
            items = listOf(
                TemplateItem(label = "Weather", actions = listOf(link)),
                TemplateItem(actions = listOf(TapAction.App("com.x"), disarm)),
            ),
        )

        val taps = tapDisclosuresOf(content)

        assertEquals(listOf(listOf(link), listOf(TapAction.App("com.x"), disarm)), taps.map { it.actions })
    }

    @Test
    fun `widgets carry no actions of their own`() {
        assertTrue(tapDisclosuresOf(TileContent.LauncherWidget(LauncherWidgetType.CLOCK)).isEmpty())
        assertTrue(tapDisclosuresOf(TileContent.AppWidget("com.x", "com.x.W", "{{PLACEHOLDER:widget_1}}")).isEmpty())
    }

    @Test
    fun `an HA action without a prompt is flagged as unconfirmed`() {
        assertTrue(tapDisclosureOf(listOf(disarm)).unconfirmed)
    }

    @Test
    fun `an HA action with a prompt is not flagged`() {
        assertFalse(tapDisclosureOf(listOf(disarm.copy(confirm = true))).unconfirmed)
    }

    @Test
    fun `one confirming HA action guards the whole tap`() {
        val tap = tapDisclosureOf(listOf(disarm, disarm.copy(service = "light.turn_on", confirm = true)))

        assertFalse(tap.unconfirmed)
    }

    @Test
    fun `a tap without HA actions has nothing to confirm`() {
        assertFalse(tapDisclosureOf(listOf(link, TapAction.App("com.x"))).unconfirmed)
    }

    @Test
    fun `the HA target is the entity field first`() {
        assertEquals("alarm_control_panel.home", haTargetOf(disarm))
    }

    @Test
    fun `the HA target falls back to the entity in the service data`() {
        val single = TapAction.HaAction(
            service = "lock.unlock",
            data = JsonObject(mapOf("entity_id" to JsonPrimitive("lock.front_door"))),
        )
        val many = TapAction.HaAction(
            service = "lock.unlock",
            data = JsonObject(
                mapOf("entity_id" to JsonArray(listOf(JsonPrimitive("lock.a"), JsonPrimitive("lock.b")))),
            ),
        )

        assertEquals("lock.front_door", haTargetOf(single))
        assertEquals("lock.a, lock.b", haTargetOf(many))
        assertNull(haTargetOf(TapAction.HaAction(service = "script.turn_on")))
    }

    @Test
    fun `the service data is shown without repeating the entity it already named`() {
        val action = TapAction.HaAction(
            service = "alarm_control_panel.alarm_disarm",
            data = JsonObject(mapOf("entity_id" to JsonPrimitive("a.b"), "code" to JsonPrimitive("1234"))),
        )

        assertEquals("""{"code":"1234"}""", haDataTextOf(action))
        assertNull(haDataTextOf(disarm))
    }

    @Test
    fun `long service data is cut short`() {
        val action = disarm.copy(data = JsonObject(mapOf("message" to JsonPrimitive("x".repeat(500)))))

        val text = requireNotNull(haDataTextOf(action, limit = 20))

        assertEquals(21, text.length)
        assertTrue(text.endsWith("…"))
    }

    @Test
    fun `target keys come first and in full, only the payload after them is cut`() {
        val action = TapAction.HaAction(
            service = "alarm_control_panel.alarm_disarm",
            data = JsonObject(
                mapOf(
                    "message" to JsonPrimitive("x".repeat(500)),
                    "area_id" to JsonPrimitive("house"),
                    "device_id" to JsonArray(listOf(JsonPrimitive("d1"), JsonPrimitive("d2"))),
                    "label_id" to JsonPrimitive("security"),
                    "floor_id" to JsonPrimitive("ground"),
                ),
            ),
        )

        val text = requireNotNull(haDataTextOf(action, limit = 20))

        val targets = """{"area_id":"house","device_id":["d1","d2"],"label_id":"security","floor_id":"ground""""
        assertTrue(text.startsWith(targets), text)
        assertEquals(targets.length + 20 + 1, text.length)
        assertTrue(text.endsWith("…"))
    }

    @Test
    fun `an entity_id in the data is not repeated next to the entity field that overrides it`() {
        val action = disarm.copy(data = JsonObject(mapOf("entity_id" to JsonPrimitive("alarm_control_panel.other"))))

        assertNull(haDataTextOf(action))
    }

    @Test
    fun `an intent link discloses its component before anything else`() {
        val target = DeepLinkTarget(
            component = "com.evil/.Pay",
            packageName = "com.evil",
            action = null,
            dataScheme = null,
            dataHost = null,
            fallbackHost = null,
            allowed = true,
        )

        val disclosure = linkDisclosureOf(target) as LinkDisclosure.Parsed

        assertEquals(listOf(LinkFact(LinkPart.APP, "com.evil/.Pay")), disclosure.facts)
        assertFalse(disclosure.refused)
    }

    @Test
    fun `a link lists app, action, data host and fallback host in that order`() {
        val target = DeepLinkTarget(
            component = null,
            packageName = "com.bank",
            action = "android.intent.action.CALL",
            dataScheme = "https",
            dataHost = "evil.example",
            fallbackHost = "fallback.example",
            allowed = true,
        )

        val facts = (linkDisclosureOf(target) as LinkDisclosure.Parsed).facts

        assertEquals(
            listOf(
                LinkFact(LinkPart.APP, "com.bank"),
                LinkFact(LinkPart.ACTION, "android.intent.action.CALL"),
                LinkFact(LinkPart.OPENS, "https://evil.example"),
                LinkFact(LinkPart.FALLBACK, "fallback.example"),
            ),
            facts,
        )
    }

    @Test
    fun `a plain link shows its scheme and host without the implied view action`() {
        val target = DeepLinkTarget(
            component = null,
            packageName = null,
            action = "android.intent.action.VIEW",
            dataScheme = "tel",
            dataHost = null,
            fallbackHost = null,
            allowed = true,
        )

        val facts = (linkDisclosureOf(target) as LinkDisclosure.Parsed).facts

        assertEquals(listOf(LinkFact(LinkPart.OPENS, "tel:")), facts)
    }

    @Test
    fun `a link the executor refuses is disclosed as refused`() {
        val target = DeepLinkTarget(
            component = null,
            packageName = null,
            action = "android.intent.action.VIEW",
            dataScheme = "file",
            dataHost = null,
            fallbackHost = null,
            allowed = false,
        )

        assertTrue((linkDisclosureOf(target) as LinkDisclosure.Parsed).refused)
    }

    @Test
    fun `a link the parser rejects is disclosed as invalid`() {
        assertEquals(LinkDisclosure.Invalid, linkDisclosureOf(null))
    }

    // Intent.parseUri reads " intent:…" as a plain view link while the executor trims before routing,
    // so a padded link once previewed as harmless and ran as a serialized intent. parseUri needs a
    // device; what is pinned here is that the preview gets the very string the executor starts.
    @Test
    fun `a padded intent link reaches the preview as the intent the executor starts`() {
        val padded = " intent://maps.google.com/" + " ".repeat(2_000) +
            "#Intent;scheme=https;component=com.evil/.Pay;S.x=y;end"
        val scenario = Scenario(
            id = "s",
            name = "Weather",
            size = TileSize.GRID_1X1,
            content = TileContent.Template(
                layout = TemplateLayout.ICON_TEXT,
                items = listOf(TemplateItem(label = "Weather", actions = listOf(TapAction.DeepLink(padded)))),
            ),
        )
        val read = ScenarioFormat.read(ScenarioFormat.encodeScenario(scenario)) as ScenarioRead.Ok

        val shown = tapDisclosuresOf(read.accepted.single().scenario.content).single().actions.single()

        val uri = (shown as TapAction.DeepLink).uri
        assertTrue(uri.startsWith("intent:"), uri)
        assertTrue(uri.endsWith("component=com.evil/.Pay;S.x=y;end"), uri)
        assertEquals(ActionRoute.IntentUri(uri), actionRouteOf(shown))
    }

    @Test
    fun `an app screen is named by its package and absolute class`() {
        val spoof = TapAction.ActivityLaunch(packageName = "com.evil", component = "com.android.settings.Settings")

        assertEquals("com.evil/com.android.settings.Settings", actionTargetOf(spoof))
        assertEquals("com.evil/com.evil.Pay", actionTargetOf(TapAction.ActivityLaunch(" com.evil ", ".Pay")))
        assertEquals("com.evil/com.evil.Pay", actionTargetOf(TapAction.ActivityLaunch("com.evil", "Pay")))
    }

    @Test
    fun `a shortcut and an in-app action name what they reach inside the app`() {
        assertEquals("transfer_all", actionTargetOf(TapAction.Shortcut("com.bank", " transfer_all ")))
        val capability = TapAction.Capability("com.x", "actions.intent.GET_THING")

        assertEquals("actions.intent.GET_THING", actionTargetOf(capability))
    }

    @Test
    fun `actions without an inner target name none`() {
        assertNull(actionTargetOf(TapAction.App("com.x")))
        assertNull(actionTargetOf(link))
        assertNull(actionTargetOf(disarm))
    }

    @Test
    fun `preview text past its limit is cut with an ellipsis`() {
        assertEquals("short", previewText("short"))
        assertEquals("x".repeat(500), previewText("x".repeat(500)))
        assertEquals("x".repeat(500) + "…", previewText("x".repeat(1_000_000)))
        assertEquals("ab…", previewText("abc", limit = 2))
    }

    @Test
    fun `a cut never splits a surrogate pair`() {
        assertEquals("ab…", previewText("ab🔒cd", limit = 3))
        assertEquals("ab🔒…", previewText("ab🔒cd", limit = 4))
    }

    @Test
    fun `an imported HA call names every entity id in its data, uncut`() {
        val ids = List(30) { "light.living_room_ceiling_$it" } + "lock.front_door"
        val action = TapAction.HaAction(
            service = "homeassistant.turn_on",
            data = JsonObject(mapOf("entity_id" to JsonArray(ids.map { JsonPrimitive(it) }))),
        )
        val scenario = Scenario(
            id = "s",
            name = "Lights",
            size = TileSize.GRID_1X1,
            content = TileContent.Template(
                layout = TemplateLayout.ICON_TEXT,
                items = listOf(TemplateItem(actions = listOf(action))),
            ),
        )
        val read = ScenarioFormat.read(ScenarioFormat.encodeScenario(scenario)) as ScenarioRead.Ok

        val shown = tapDisclosuresOf(read.accepted.single().scenario.content).single().actions.single()

        val target = requireNotNull(haTargetOf(shown as TapAction.HaAction))
        assertEquals(ids.joinToString(), target)
        assertFalse(target.contains("…"), target)
    }

    @Test
    fun `only the gestures the backup sets are disclosed, in a fixed order`() {
        val gestures = GesturesConfig(swipeLeft = disarm, calendarTap = link)

        val disclosed = gestureDisclosuresOf(gestures)

        assertEquals(listOf(ConfigGesture.SWIPE_LEFT, ConfigGesture.CALENDAR_TAP), disclosed.map { it.gesture })
        assertTrue(disclosed.first().tap.unconfirmed)
        assertFalse(disclosed.last().tap.unconfirmed)
    }
}
