package app.cue.launcher.data.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScenarioFormatTest {

    private val item = TemplateItem(label = "X", actions = listOf(TapAction.App("com.x")))

    private val musicScenario = Scenario(
        id = "headphones-music",
        name = "Headphones → music",
        priority = 500,
        size = TileSize.GRID_1X1,
        conditions = listOf(Condition.AudioOutput()),
        content = TileContent.Template(
            layout = TemplateLayout.ICON_TEXT,
            items = listOf(
                TemplateItem(
                    label = "Hudba",
                    actions = listOf(TapAction.App("com.google.android.apps.youtube.music")),
                ),
            ),
        ),
    )

    @Test
    fun `canonical JSON of a scenario has a given shape`() {
        assertEquals(
            oneLine(
                """
                {"schemaVersion":1,"id":"headphones-music","name":"Headphones → music","enabled":true,
                "priority":500,"size":"1x1","match":"all",
                "conditions":[{"type":"audio_output","negate":false}],
                "content":{"kind":"template","layout":"icon_text","text":null,"text2":null,
                "items":[{"label":"Hudba","icon":null,"action":{"kind":"app",
                "package":"com.google.android.apps.youtube.music"}}]}}
                """,
            ),
            ScenarioFormat.canonicalJson(musicScenario),
        )
    }

    @Test
    fun `a floating scenario carries no pin field at all`() {
        assertTrue("pinnedPosition" !in ScenarioFormat.canonicalJson(musicScenario))
    }

    @Test
    fun `a pinned scenario writes its position next to the priority`() {
        val pinned = musicScenario.copy(pinnedPosition = 2)

        assertTrue(""""priority":500,"pinnedPosition":2,"size":"1x1"""" in ScenarioFormat.canonicalJson(pinned))
        assertEquals(listOf(pinned), ok(ScenarioFormat.encodeScenario(pinned)).accepted.map { it.scenario })
    }

    @Test
    fun `a single scenario roundtrips through write and read without loss`() {
        val ok = ok(ScenarioFormat.encodeScenario(musicScenario))

        assertEquals(listOf(musicScenario), ok.accepted.map { it.scenario })
        assertTrue(ok.rejected.isEmpty())
        assertNull(ok.setName)
        assertNull(ok.config)
    }

    @Test
    fun `all state conditions roundtrip`() {
        val conditions = listOf(
            Condition.TimeWindow(from = "18:00", to = "23:00", days = listOf(1, 2, 3)),
            Condition.BtConnected(mac = "AA:BB:CC:DD:EE:FF"),
            Condition.BtConnected(),
            Condition.Wifi(mode = WifiMode.SSID, ssid = "Home", negate = true),
            Condition.Wifi(mode = WifiMode.ANY),
            Condition.Charging(),
            Condition.AudioOutput(),
            Condition.MediaPlaying(),
            Condition.MediaPlaying(packages = listOf("com.google.android.apps.youtube.music", "com.spotify.music")),
            Condition.RingerMode(RingerModeState.DND),
            Condition.CalendarEvent(),
            Condition.HaState(entity = "media_player.living_room_tv", states = listOf("playing", "paused")),
        )

        conditions.forEach { condition ->
            val scenario = musicScenario.copy(conditions = listOf(condition))
            assertEquals(scenario, roundtrip(scenario)) { "condition $condition" }

            val negated = musicScenario.copy(conditions = listOf(condition.withNegate(true)))
            assertEquals(negated, roundtrip(negated)) { "negated condition $condition" }
        }
    }

    @Test
    fun `an imported app list is tidied`() {
        val padded = Condition.MediaPlaying(packages = listOf(" com.spotify.music ", "", "com.spotify.music"))
        val json = ScenarioFormat.encodeScenario(musicScenario.copy(conditions = listOf(padded)))

        val condition = ok(json).accepted.single().scenario.conditions.single()

        assertEquals(Condition.MediaPlaying(packages = listOf("com.spotify.music")), condition)
    }

    @Test
    fun `the end of any notification carries neither the ongoing flag nor a minimum`() {
        val json = ScenarioFormat.encodeScenario(
            musicScenario.copy(conditions = listOf(Condition.NotificationEnded(packageName = "cz.alza"))),
        )

        assertFalse("ongoing" in json) { json }
        assertFalse("minLastedSec" in json) { json }
    }

    @Test
    fun `the end of a notification without an app is a defect`() {
        val read = ScenarioFormat.read(
            oneLine(
                """
                {"schemaVersion":1,"id":"p","name":"Parking","size":"1x1",
                "conditions":[{"type":"notification_ended","package":" "}],
                "content":{"kind":"template","layout":"icon_text","items":[
                {"action":{"kind":"system","action":"remember_place"}}]}}
                """,
            ),
        ) as ScenarioRead.Ok

        assertTrue(read.accepted.isEmpty())
        assertEquals(1, read.rejected.size)
    }

    @Test
    fun `a media condition for any app carries no app list`() {
        val json = ScenarioFormat.encodeScenario(musicScenario.copy(conditions = listOf(Condition.MediaPlaying())))

        assertFalse("packages" in json) { json }
    }

    @Test
    fun `extract rules write only the fields they use`() {
        val condition = Condition.NotificationFrom(
            packageName = "cz.zasilkovna.app",
            extract = listOf(
                ExtractRule(name = "kod", after = "kód", kind = ExtractKind.NUMBER),
                ExtractRule(name = "castka", kind = ExtractKind.AMOUNT),
                ExtractRule(name = "note", after = "pozn", kind = ExtractKind.UNTIL, until = "."),
            ),
        )

        assertEquals(
            oneLine(
                """
                [{"type":"notification","package":"cz.zasilkovna.app","contains":null,
                "extract":[{"name":"kod","after":"kód","kind":"number"},{"name":"castka","kind":"amount"},
                {"name":"note","after":"pozn","kind":"until","until":"."}],"durationSec":null}]
                """,
            ),
            ScenarioFormat.encodeConditions(listOf(condition)),
        )
    }

    @Test
    fun `a notification condition without rules carries no extract field`() {
        val json = ScenarioFormat.encodeConditions(listOf(Condition.NotificationFrom(packageName = "cz.alza")))

        assertFalse("extract" in json) { json }
    }

    @Test
    fun `an extract field on another condition is ignored like any unknown key`() {
        val json = """
            {"schemaVersion":1,"id":"x","name":"X","size":"1x1",
             "conditions":[{"type":"charging","extract":[{"name":"kod","kind":"regex"}]}],
             "content":{"kind":"template","layout":"icon_text",
               "items":[{"label":"X","action":{"kind":"app","package":"cz.alza"}}]}}
        """

        val accepted = ok(json).accepted.single().scenario

        assertEquals(1, accepted.conditions.size)
        assertFalse(accepted.conditions.single() is Condition.NotificationFrom)
    }

    @Test
    fun `an import drops what an extract rule does not read`() {
        val long = "x".repeat(EXTRACT_MARKER_MAX_LENGTH + 1)
        val json = """
            {"schemaVersion":1,"id":"x","name":"X","size":"1x1",
             "conditions":[{"type":"notification","package":"cz.alza",
               "extract":[{"name":"kod","after":" \u0301 ","kind":"number","until":"$long"},
                          {"name":"note","after":" pozn ","kind":"until","until":" . "}]}],
             "content":{"kind":"template","layout":"icon_text",
               "items":[{"label":"X","action":{"kind":"app","package":"cz.alza"}}]}}
        """

        val condition = ok(json).accepted.single().scenario.conditions.single() as Condition.NotificationFrom

        assertEquals(
            listOf(
                ExtractRule(name = "kod", kind = ExtractKind.NUMBER),
                ExtractRule(name = "note", after = "pozn", kind = ExtractKind.UNTIL, until = "."),
            ),
            condition.extract,
        )
    }

    @Test
    fun `an unknown extract kind is named rather than reported as unreadable`() {
        val json = """
            {"schemaVersion":1,"id":"x","name":"X","size":"1x1",
             "conditions":[{"type":"notification","package":"cz.alza",
               "extract":[{"name":"kod","kind":"regex"}]}],
             "content":{"kind":"template","layout":"icon_text",
               "items":[{"label":"X","action":{"kind":"app","package":"cz.alza"}}]}}
        """

        assertEquals(
            listOf(ScenarioDefect.UnknownEnum("conditions[].extract[].kind", "regex")),
            ok(json).rejected.single().defects,
        )
    }

    @Test
    fun `the match mode roundtrips and defaults to all`() {
        val any = musicScenario.copy(
            match = ConditionMatch.ANY,
            conditions = listOf(Condition.Charging(), Condition.HaEvent(code = "zvonek")),
        )

        assertEquals(any, roundtrip(any))
        assertEquals(ConditionMatch.ALL, roundtrip(musicScenario)?.match)
        assertEquals(
            ConditionMatch.ALL,
            ScenarioFormat.decodeCanonical(
                """{"id":"s1","name":"S","size":"1x1","conditions":[],""" +
                    """"content":{"kind":"template","layout":"text_only","items":[]}}""",
            )?.match,
        )
    }

    @Test
    fun `all instant conditions roundtrip along with a duration`() {
        val conditions = listOf(
            Condition.TimeInstant(at = "17:00", days = listOf(1, 5), durationSec = 60),
            Condition.BtDisconnected(mac = "{{PLACEHOLDER:bt_car}}", durationSec = 120),
            Condition.NotificationFrom(packageName = "cz.alza", contains = "kód", durationSec = 300),
            Condition.NotificationFrom(
                packageName = "cz.alza",
                extract = listOf(
                    ExtractRule(name = "kod", after = "kód", kind = ExtractKind.NUMBER),
                    ExtractRule(name = "ref", kind = ExtractKind.CODE),
                    ExtractRule(name = "castka", after = "Částka", kind = ExtractKind.AMOUNT),
                    ExtractRule(name = "cas", kind = ExtractKind.TIME),
                ),
                durationSec = 300,
            ),
            Condition.NotificationFrom(
                packageName = "cz.alza",
                contains = "kód",
                extract = listOf(
                    ExtractRule(name = "den", kind = ExtractKind.DATE),
                    ExtractRule(name = "kdo", after = "od", kind = ExtractKind.WORD),
                    ExtractRule(name = "misto", after = "místo", kind = ExtractKind.LINE),
                    ExtractRule(name = "note", after = "pozn", kind = ExtractKind.UNTIL, until = "."),
                ),
            ),
            Condition.HaEvent(code = "zvonek", durationSec = 60),
            Condition.HaEvent(code = "zvonek"),
            Condition.NotificationEnded(
                packageName = "com.google.android.apps.maps",
                ongoing = true,
                minLastedSec = 120,
                durationSec = 1800,
            ),
            Condition.NotificationEnded(packageName = "com.google.android.apps.maps"),
            Condition.PlaceSaved(durationSec = 86_400),
            Condition.PlaceSaved(),
        )

        conditions.forEach { condition ->
            val scenario = musicScenario.copy(conditions = listOf(condition))
            assertEquals(scenario, roundtrip(scenario)) { "condition $condition" }
        }
    }

    @Test
    fun `all content types roundtrip`() {
        val contents = listOf(
            TileContent.Template(TemplateLayout.ICON_TEXT, items = listOf(item)) to TileSize.GRID_1X1,
            TileContent.Template(TemplateLayout.TEXT_ONLY, text = "Ahoj", items = listOf(item)) to
                TileSize.GRID_2X2,
            TileContent.Template(
                layout = TemplateLayout.TEXT_TWO_ICONS,
                text = "Nadpis",
                text2 = "Podtitul",
                items = listOf(item, item),
            ) to TileSize.GRID_2X2,
            TileContent.Template(TemplateLayout.FOUR_ICONS, items = List(4) { item }) to TileSize.GRID_2X2,
            TileContent.LauncherWidget(LauncherWidgetType.CALENDAR) to TileSize.HERO_4X2,
            TileContent.LauncherWidget(LauncherWidgetType.CLOCK) to TileSize.HERO_4X2,
            TileContent.AppWidget(
                packageName = "io.homeassistant.companion.android",
                provider = "io.homeassistant.companion.android.widgets.DashboardWidget",
                appWidgetId = "{{PLACEHOLDER:widget_ha}}",
            ) to TileSize.HERO_4X2,
        )

        contents.forEach { (content, size) ->
            val scenario = musicScenario.copy(size = size, content = content)
            assertEquals(scenario, roundtrip(scenario)) { "obsah $content" }
        }
    }

    @Test
    fun `all action types roundtrip`() {
        val actions = listOf(
            TapAction.App("com.spotify.music"),
            TapAction.Shortcut("com.google.android.dialer", shortcutId = "favorite-1"),
            TapAction.DeepLink("google.navigation:q=Home"),
            TapAction.Capability("com.example", capability = "actions.intent.OPEN_APP_FEATURE"),
            TapAction.ActivityLaunch("com.android.chrome", component = "org.chromium.SearchActivity"),
            TapAction.SystemAction(SystemActionType.DND_ON),
            TapAction.HaAction(service = "light.turn_off", entity = "light.living_room"),
            TapAction.HaAction(service = "lock.open", entity = "lock.front_door", stateEntity = "binary_sensor.door"),
        )

        actions.forEach { action ->
            val scenario = musicScenario.copy(
                content = TileContent.Template(
                    layout = TemplateLayout.ICON_TEXT,
                    items = listOf(TemplateItem(label = "X", actions = listOf(action))),
                ),
            )
            assertEquals(scenario, roundtrip(scenario)) { "action $action" }
        }
    }

    @Test
    fun `system actions keep their names in JSON`() {
        SystemActionType.entries.forEach { action ->
            val json = ScenarioFormat.canonicalJson(
                musicScenario.copy(
                    content = TileContent.Template(
                        layout = TemplateLayout.ICON_TEXT,
                        items = listOf(TemplateItem(actions = listOf(TapAction.SystemAction(action)))),
                    ),
                ),
            )

            assertTrue(json.contains(""""action":"${action.jsonName}"""")) { json }
        }
    }

    @Test
    fun `the TV remote reads as a system action and writes back under its name`() {
        val scenario = accepted(scenarioWith(""""action":{"kind":"system","action":"tv_remote"}""")).single().scenario
        val action = (scenario.content as TileContent.Template).items.single().actions.single()

        assertEquals(TapAction.SystemAction(SystemActionType.TV_REMOTE), action)
        assertTrue(ScenarioFormat.canonicalJson(scenario).contains(""""kind":"system","action":"tv_remote""""))
    }

    @Test
    fun `the HA action payload stays intact`() {
        val json = oneLine(
            """
            {"schemaVersion":1,"id":"a","name":"a","enabled":true,"priority":0,"size":"1x1",
            "conditions":[],"content":{"kind":"template","layout":"icon_text","items":[
            {"label":"Light","action":{"kind":"ha","service":"light.turn_on",
            "entity":"light.x","data":{"brightness":200,"rgb_color":[255,0,0]}}}]}}
            """,
        )

        val scenario = accepted(json).single().scenario
        val action = (scenario.content as TileContent.Template).items.single().actions.single()

        assertEquals(scenario, roundtrip(scenario))
        assertEquals(
            """{"brightness":200,"rgb_color":[255,0,0]}""",
            (action as TapAction.HaAction).data.toString(),
        )
    }

    @Test
    fun `an HA action may ask for confirmation, and a file that does not know the field still reads`() {
        val confirmed = oneLine(
            """
            {"schemaVersion":1,"id":"a","name":"a","enabled":true,"priority":0,"size":"1x1",
            "conditions":[],"content":{"kind":"template","layout":"icon_text","items":[
            {"label":"Gate","action":{"kind":"ha","service":"cover.open_cover","confirm":true}}]}}
            """,
        )
        val plain = confirmed.replace(""","confirm":true""", "")

        val scenario = accepted(confirmed).single().scenario
        val action = (scenario.content as TileContent.Template).items.single().actions.single()
        val older = accepted(plain).single().scenario
        val olderAction = (older.content as TileContent.Template).items.single().actions.single()

        assertTrue((action as TapAction.HaAction).confirm)
        assertEquals(scenario, roundtrip(scenario))
        assertFalse((olderAction as TapAction.HaAction).confirm)
    }

    @Test
    fun `an HA action may name the entity its icon shows, and a file without the field still reads`() {
        val door = oneLine(
            """
            {"schemaVersion":1,"id":"a","name":"a","enabled":true,"priority":0,"size":"1x1",
            "conditions":[],"content":{"kind":"template","layout":"icon_text","items":[
            {"label":"Door","action":{"kind":"ha","service":"lock.open","entity":"lock.front_door",
            "stateEntity":"binary_sensor.front_door_contact"}}]}}
            """,
        )
        val plain = door.replace(""","stateEntity":"binary_sensor.front_door_contact"""", "")

        val scenario = accepted(door).single().scenario
        val action = (scenario.content as TileContent.Template).items.single().actions.single()
        val older = accepted(plain).single().scenario
        val olderAction = (older.content as TileContent.Template).items.single().actions.single()

        assertEquals("binary_sensor.front_door_contact", (action as TapAction.HaAction).stateEntity)
        assertEquals("lock.front_door", action.entity)
        assertEquals(scenario, roundtrip(scenario))
        assertNull((olderAction as TapAction.HaAction).stateEntity)
        assertTrue("stateEntity" in ScenarioFormat.canonicalJson(scenario))
        assertFalse("stateEntity" in ScenarioFormat.canonicalJson(older))
    }

    @Test
    fun `a named set carries the name and all scenarios`() {
        val second = musicScenario.copy(id = "second", name = "Second")

        val ok = ok(ScenarioFormat.encodeSet("My set", listOf(musicScenario, second)))

        assertEquals("My set", ok.setName)
        assertEquals(listOf(musicScenario, second), ok.accepted.map { it.scenario })
    }

    @Test
    fun `a scenario in a set carries its own format version`() {
        val text = ScenarioFormat.encodeSet("Set", listOf(musicScenario))

        assertEquals(2, Regex(""""schemaVersion"""").findAll(text).count()) { text }
    }

    @Test
    fun `unknown fields are ignored`() {
        val json = oneLine(
            """
            {"schemaVersion":1,"id":"a","name":"a","enabled":true,"priority":0,"size":"1x1",
            "cooldownSec":30,"conditions":[{"type":"charging","futureField":true}],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"label":"X","action":{"kind":"app","package":"com.x","extra":1}}]}}
            """,
        )

        assertEquals(listOf(Condition.Charging()), accepted(json).single().scenario.conditions)
    }

    @Test
    fun `an unknown enum rejects only its own scenario`() {
        val text = oneLine(
            """
            {"schemaVersion":1,"name":"Set","scenarios":[
            {"id":"bad-size","name":"Foreign size","size":"8x2","conditions":[],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}},
            {"id":"bad-type","name":"Foreign condition","size":"1x1",
            "conditions":[{"type":"weather"}],"content":{"kind":"template","layout":"icon_text",
            "items":[{"action":{"kind":"app","package":"com.x"}}]}},
            {"id":"bad-kind","name":"Foreign content","size":"1x1","conditions":[],
            "content":{"kind":"hologram"}},
            {"id":"bad-layout","name":"Foreign layout","size":"2x2","conditions":[],
            "content":{"kind":"template","layout":"mosaic","items":[]}},
            {"id":"good","name":"Good","size":"1x1","conditions":[],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}}]}
            """,
        )

        val ok = ok(text)

        assertEquals(listOf("good"), ok.accepted.map { it.scenario.id })
        assertEquals(
            listOf("bad-size", "bad-type", "bad-kind", "bad-layout"),
            ok.rejected.map { it.id },
        )
        assertEquals(listOf(ScenarioDefect.UnknownEnum("size", "8x2")), ok.rejected[0].defects)
        assertEquals(
            listOf(ScenarioDefect.UnknownEnum("conditions[].type", "weather")),
            ok.rejected[1].defects,
        )
        assertTrue(ok.rejected[2].defects.single() is ScenarioDefect.Malformed) { "content" }
        assertTrue(ok.rejected[3].defects.single() is ScenarioDefect.Malformed) { "layout" }
    }

    @Test
    fun `a newer format version is rejected`() {
        assertEquals(
            ScenarioDefect.UnsupportedSchemaVersion(found = 99, supported = 1),
            failed("""{"schemaVersion":99,"name":"Set","scenarios":[]}"""),
        )
    }

    @Test
    fun `a newer scenario in a set rejects only itself`() {
        val text = oneLine(
            """
            {"schemaVersion":1,"name":"Set","scenarios":[
            {"schemaVersion":7,"id":"from-future","name":"From the future","size":"1x1","conditions":[],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}},
            {"id":"good","name":"Good","size":"1x1","conditions":[],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}}]}
            """,
        )

        val ok = ok(text)

        assertEquals(listOf("good"), ok.accepted.map { it.scenario.id })
        assertEquals(
            ScenarioDefect.UnsupportedSchemaVersion(found = 7, supported = 1),
            ok.rejected.single().defects.single(),
        )
    }

    @Test
    fun `a file that is not a JSON object is rejected`() {
        listOf("", "[]", "nonsense", """{"id":""", "42").forEach { text ->
            assertTrue(failed(text) is ScenarioDefect.Malformed) { "input: $text" }
        }
    }

    @Test
    fun `a missing version is treated as current`() {
        val json = oneLine(
            """
            {"id":"a","name":"a","size":"1x1","conditions":[],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}}
            """,
        )

        assertEquals("a", accepted(json).single().scenario.id)
    }

    @Test
    fun `unreadable canonical JSON is read as null`() {
        assertNull(ScenarioFormat.decodeCanonical("""{"id":"a"}"""))
        assertNull(ScenarioFormat.decodeCanonical("nonsense"))
        assertEquals(
            musicScenario,
            ScenarioFormat.decodeCanonical(ScenarioFormat.canonicalJson(musicScenario)),
        )
    }

    @Test
    fun `a single action is still written as an object`() {
        val json = ScenarioFormat.canonicalJson(musicScenario)

        assertTrue(json.contains(""""action":{"kind":"app""""))
        assertTrue(!json.contains(""""action":["""))
    }

    @Test
    fun `multiple actions are written as an array and read back`() {
        val quiet = musicScenario.copy(
            content = TileContent.Template(
                layout = TemplateLayout.ICON_TEXT,
                items = listOf(
                    TemplateItem(
                        label = "Klid",
                        actions = listOf(
                            TapAction.SystemAction(SystemActionType.RINGER_SILENT),
                            TapAction.SystemAction(SystemActionType.DND_ON),
                        ),
                    ),
                ),
            ),
        )

        val json = ScenarioFormat.canonicalJson(quiet)

        assertTrue(json.contains(""""action":[{"kind":"system","action":"ringer_silent"}"""))
        assertEquals(quiet, roundtrip(quiet))
    }

    @Test
    fun `both object and array read the same way`() {
        val objectForm = accepted(scenarioWith(""""action":{"kind":"system","action":"dnd_on"}""")).single()
        val arrayForm = accepted(scenarioWith(""""action":[{"kind":"system","action":"dnd_on"}]""")).single()

        assertEquals(
            listOf(TapAction.SystemAction(SystemActionType.DND_ON)),
            (objectForm.scenario.content as TileContent.Template).items.single().actions,
        )
        assertEquals(objectForm.scenario.content, arrayForm.scenario.content)
    }

    @Test
    fun `order of actions in the array is preserved`() {
        val json = scenarioWith(
            """"action":[{"kind":"system","action":"ringer_silent"},{"kind":"system","action":"dnd_on"}]""",
        )

        val actions = (accepted(json).single().scenario.content as TileContent.Template)
            .items.single().actions

        assertEquals(
            listOf(
                TapAction.SystemAction(SystemActionType.RINGER_SILENT),
                TapAction.SystemAction(SystemActionType.DND_ON),
            ),
            actions,
        )
    }

    @Test
    fun `an empty array of actions is a defect`() {
        val read = ok(scenarioWith(""""action":[]"""))

        assertTrue(read.accepted.isEmpty())
        assertEquals(
            ScenarioDefect.MissingField("items[0].action"),
            read.rejected.single().defects.single(),
        )
    }

    private fun scenarioWith(action: String): String = oneLine(
        """
        {"schemaVersion":1,"id":"x","name":"X","priority":0,"size":"1x1",
        "conditions":[],"content":{"kind":"template","layout":"icon_text",
        "items":[{"label":"Klid",$action}]}}
        """,
    )

    private fun oneLine(json: String): String = json.trimIndent().replace("\n", "")

    private fun roundtrip(scenario: Scenario): Scenario? =
        ok(ScenarioFormat.encodeScenario(scenario)).accepted.singleOrNull()?.scenario

    private fun ok(text: String): ScenarioRead.Ok {
        val read = ScenarioFormat.read(text)
        assertTrue(read is ScenarioRead.Ok) { "expected Ok, got $read" }
        return read as ScenarioRead.Ok
    }

    private fun failed(text: String): ScenarioDefect {
        val read = ScenarioFormat.read(text)
        assertTrue(read is ScenarioRead.Failed) { "expected Failed, got $read" }
        return (read as ScenarioRead.Failed).defect
    }

    private fun accepted(text: String): List<ImportedScenario> {
        val ok = ok(text)
        assertTrue(ok.rejected.isEmpty()) { ok.rejected.toString() }
        return ok.accepted
    }
}
