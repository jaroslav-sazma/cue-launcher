package app.cue.launcher.data.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScenarioValidationTest {

    private val item = TemplateItem(label = "X", actions = listOf(TapAction.App("com.x")))

    private val scenario = Scenario(
        id = "s1",
        name = "Scénář",
        size = TileSize.GRID_1X1,
        content = TileContent.Template(TemplateLayout.ICON_TEXT, items = listOf(item)),
    )

    private fun notification(extract: List<ExtractRule>): Scenario = scenario.copy(
        conditions = listOf(Condition.NotificationFrom(packageName = "cz.zasilkovna.app", extract = extract)),
    )

    @Test
    fun `a valid scenario has no defect`() {
        assertEquals(emptyList<ScenarioDefect>(), validateScenario(scenario))
    }

    @Test
    fun `a pin inside the size class is accepted`() {
        assertEquals(emptyList<ScenarioDefect>(), validateScenario(scenario.copy(pinnedPosition = 2)))
    }

    @Test
    fun `a pin on the overflow slot is a defect`() {
        assertEquals(
            listOf(ScenarioDefect.PinnedPositionOutOfRange(position = 3, size = TileSize.GRID_1X1)),
            validateScenario(scenario.copy(pinnedPosition = 3)),
        )
    }

    @Test
    fun `the hero has no position to pin`() {
        val hero = scenario.copy(
            size = TileSize.HERO_4X2,
            pinnedPosition = 0,
            content = TileContent.LauncherWidget(LauncherWidgetType.CALENDAR),
        )

        assertEquals(
            listOf(ScenarioDefect.PinnedPositionOutOfRange(position = 0, size = TileSize.HERO_4X2)),
            validateScenario(hero),
        )
    }

    @Test
    fun `two instant conditions are a defect`() {
        val twoInstants = scenario.copy(
            conditions = listOf(
                Condition.HaEvent(code = "zvonek"),
                Condition.TimeInstant(at = "17:00"),
            ),
        )

        assertEquals(
            listOf(ScenarioDefect.MultipleInstantConditions(count = 2)),
            validateScenario(twoInstants),
        )
    }

    @Test
    fun `two instant conditions are fine under any`() {
        val twoInstants = scenario.copy(
            match = ConditionMatch.ANY,
            conditions = listOf(
                Condition.HaEvent(code = "zvonek"),
                Condition.TimeInstant(at = "17:00"),
            ),
        )

        assertEquals(emptyList<ScenarioDefect>(), validateScenario(twoInstants))
    }

    @Test
    fun `there can be any number of state conditions`() {
        val many = scenario.copy(
            conditions = listOf(
                Condition.TimeWindow(from = "18:00", to = "23:00"),
                Condition.Wifi(mode = WifiMode.ANY),
                Condition.Charging(),
                Condition.HaEvent(code = "zvonek", durationSec = 60),
            ),
        )

        assertEquals(emptyList<ScenarioDefect>(), validateScenario(many))
    }

    @Test
    fun `a duration on a state condition is a defect`() {
        val text = """
            {"schemaVersion":1,"id":"s1","name":"Scénář","size":"1x1",
            "conditions":[{"type":"charging","durationSec":60}],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}}
        """.trimIndent().replace("\n", "")

        val read = ScenarioFormat.read(text) as ScenarioRead.Ok

        assertEquals(
            listOf(ScenarioDefect.DurationOnStateCondition("charging")),
            read.rejected.single().defects,
        )
    }

    @Test
    fun `a non-positive duration is a defect`() {
        val zero = scenario.copy(conditions = listOf(Condition.HaEvent(code = "x", durationSec = 0)))

        assertEquals(listOf(ScenarioDefect.InvalidDuration(0)), validateScenario(zero))
    }

    @Test
    fun `an end event alongside a duration is fine, the duration caps it`() {
        val capped = scenario.copy(
            conditions = listOf(
                Condition.NotificationFrom(
                    packageName = "cz.zasilkovna.app",
                    durationSec = 86_400,
                    until = EndEvent.Notification(packageName = "cz.zasilkovna.app", contains = "picked up"),
                ),
            ),
        )

        assertEquals(emptyList<ScenarioDefect>(), validateScenario(capped))
    }

    @Test
    fun `valid end events are not a defect`() {
        listOf(
            EndEvent.AtTime(at = "23:59", days = listOf(1, 7)),
            EndEvent.Alarm,
            EndEvent.BtDisconnected(),
            EndEvent.BtDisconnected(mac = "{{PLACEHOLDER:bt_car}}"),
            EndEvent.Notification(packageName = "cz.alza"),
            EndEvent.HaEvent(code = "door_closed"),
        ).forEach { until ->
            val ended = scenario.copy(conditions = listOf(Condition.HaEvent(code = "doorbell", until = until)))

            assertEquals(emptyList<ScenarioDefect>(), validateScenario(ended)) { "until $until" }
        }
    }

    @Test
    fun `a broken end event is a defect of its own`() {
        val cases = mapOf(
            EndEvent.AtTime(at = "24:00") to listOf(ScenarioDefect.InvalidTime("until.at", "24:00")),
            EndEvent.AtTime(at = "17:00", days = listOf(0, 8)) to listOf(ScenarioDefect.InvalidDays(listOf(0, 8))),
            EndEvent.AtTime(at = "17:00", days = emptyList()) to listOf(ScenarioDefect.MissingField("until.days")),
            EndEvent.Notification(packageName = " ") to listOf(ScenarioDefect.MissingField("until.package")),
            EndEvent.HaEvent(code = "") to listOf(ScenarioDefect.MissingField("until.code")),
        )

        cases.forEach { (until, expected) ->
            val broken = scenario.copy(conditions = listOf(Condition.Alarm(until = until)))

            assertEquals(expected, validateScenario(broken)) { "until $until" }
        }
    }

    @Test
    fun `a broken end event rejects only its own scenario on import`() {
        val text = """
            {"schemaVersion":1,"name":"Set","scenarios":[
            {"id":"bad","name":"Bad","size":"1x1",
            "conditions":[{"type":"alarm","until":{"type":"notification","package":""}}],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}},
            {"id":"good","name":"Good","size":"1x1",
            "conditions":[{"type":"alarm","until":{"type":"time_instant","at":"07:30"}}],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}}]}
        """.trimIndent().replace("\n", "")

        val read = ScenarioFormat.read(text) as ScenarioRead.Ok

        assertEquals(
            listOf(Condition.Alarm(until = EndEvent.AtTime(at = "07:30"))),
            read.accepted.single().scenario.conditions,
        )
        assertEquals(listOf(ScenarioDefect.MissingField("until.package")), read.rejected.single().defects)
    }

    @Test
    fun `a template in the wrong size is a defect`() {
        val wrong = scenario.copy(
            size = TileSize.GRID_2X2,
            content = TileContent.Template(TemplateLayout.ICON_TEXT, items = listOf(item)),
        )

        assertEquals(
            listOf(
                ScenarioDefect.LayoutSizeMismatch(TemplateLayout.ICON_TEXT, TileSize.GRID_2X2),
            ),
            validateScenario(wrong),
        )
    }

    @Test
    fun `a hero has no template`() {
        TemplateLayout.entries.forEach { layout ->
            val hero = scenario.copy(
                size = TileSize.HERO_4X2,
                content = TileContent.Template(layout, items = List(layout.itemCount) { item }),
            )

            assertTrue(
                validateScenario(hero).contains(
                    ScenarioDefect.LayoutSizeMismatch(layout, TileSize.HERO_4X2),
                ),
            ) { "layout $layout" }
        }
    }

    @Test
    fun `the item count must match the layout`() {
        assertEquals(1, TemplateLayout.ICON_TEXT.itemCount)
        assertEquals(1, TemplateLayout.TEXT_ONLY.itemCount)
        assertEquals(2, TemplateLayout.TEXT_TWO_ICONS.itemCount)
        assertEquals(4, TemplateLayout.FOUR_ICONS.itemCount)

        val tooFew = scenario.copy(
            size = TileSize.GRID_2X2,
            content = TileContent.Template(TemplateLayout.FOUR_ICONS, items = listOf(item, item)),
        )

        assertEquals(
            listOf(
                ScenarioDefect.ItemCountMismatch(TemplateLayout.FOUR_ICONS, expected = 4, actual = 2),
            ),
            validateScenario(tooFew),
        )
    }

    @Test
    fun `a searchbar as scenario content is a defect`() {
        TileSize.entries.forEach { size ->
            val searchbar = scenario.copy(
                size = size,
                content = TileContent.LauncherWidget(LauncherWidgetType.SEARCHBAR),
            )

            assertTrue(
                validateScenario(searchbar).contains(
                    ScenarioDefect.WidgetSizeMismatch(LauncherWidgetType.SEARCHBAR, size),
                ),
            ) { "size $size" }
        }
    }

    @Test
    fun `the calendar and clock belong on hero`() {
        listOf(LauncherWidgetType.CALENDAR, LauncherWidgetType.CLOCK).forEach { widget ->
            val hero = scenario.copy(
                size = TileSize.HERO_4X2,
                content = TileContent.LauncherWidget(widget),
            )
            val small = hero.copy(size = TileSize.GRID_1X1)

            assertEquals(emptyList<ScenarioDefect>(), validateScenario(hero)) { "widget $widget" }
            assertEquals(
                listOf(ScenarioDefect.WidgetSizeMismatch(widget, TileSize.GRID_1X1)),
                validateScenario(small),
            )
        }
    }

    @Test
    fun `a negative priority is a defect and a large one is fine`() {
        assertEquals(
            listOf(ScenarioDefect.NegativePriority(-1)),
            validateScenario(scenario.copy(priority = -1)),
        )
        assertEquals(
            emptyList<ScenarioDefect>(),
            validateScenario(scenario.copy(priority = Int.MAX_VALUE)),
        )
    }

    @Test
    fun `overlay is rejected in v1`() {
        assertTrue(
            validateScenario(scenario.copy(size = TileSize.OVERLAY))
                .contains(ScenarioDefect.OverlayUnsupported),
        )
    }

    @Test
    fun `a blank id is a defect`() {
        assertTrue(
            validateScenario(scenario.copy(id = " ")).contains(ScenarioDefect.BlankId("Scénář")),
        )
    }

    @Test
    fun `a specific Wi-Fi without an SSID is a defect`() {
        val missing = scenario.copy(conditions = listOf(Condition.Wifi(mode = WifiMode.SSID)))

        assertEquals(listOf(ScenarioDefect.MissingField("wifi.ssid")), validateScenario(missing))
    }

    @Test
    fun `an SSID as a placeholder is not a defect`() {
        val placeholder = scenario.copy(
            conditions = listOf(
                Condition.Wifi(mode = WifiMode.SSID, ssid = "{{PLACEHOLDER:wifi_home}}"),
            ),
        )

        assertEquals(emptyList<ScenarioDefect>(), validateScenario(placeholder))
    }

    @Test
    fun `a time outside HH mm is a defect`() {
        listOf("24:00", "18:60", "8:00", "1800", "", "18:00:00").forEach { value ->
            val window = scenario.copy(conditions = listOf(Condition.TimeWindow(from = value, to = "23:00")))

            assertEquals(
                listOf(ScenarioDefect.InvalidTime("time_window.from", value)),
                validateScenario(window),
            ) { "time $value" }
        }
    }

    @Test
    fun `valid extract rules are not a defect`() {
        val rules = listOf(
            ExtractRule(name = "kod", after = "kód", kind = ExtractKind.NUMBER),
            ExtractRule(name = "misto_2", after = "místo", kind = ExtractKind.LINE),
            ExtractRule(name = "note", kind = ExtractKind.UNTIL, until = "."),
            ExtractRule(name = "castka", kind = ExtractKind.AMOUNT, until = " "),
        )

        assertEquals(emptyList<ScenarioDefect>(), validateScenario(notification(emptyList())))
        assertEquals(emptyList<ScenarioDefect>(), validateScenario(notification(rules)))
    }

    @Test
    fun `each broken extract rule is a defect of its own`() {
        val rules = listOf(
            ExtractRule(name = "Kod", kind = ExtractKind.NUMBER),
            ExtractRule(name = "text", kind = ExtractKind.WORD),
            ExtractRule(name = "a", kind = ExtractKind.WORD),
            ExtractRule(name = "a", kind = ExtractKind.LINE),
        )

        assertEquals(
            listOf(
                ScenarioDefect.InvalidExtractRule("Kod", ExtractProblem.NAME),
                ScenarioDefect.InvalidExtractRule("text", ExtractProblem.RESERVED_NAME),
                ScenarioDefect.InvalidExtractRule("a", ExtractProblem.DUPLICATE_NAME),
            ),
            validateScenario(notification(rules)),
        )
    }

    @Test
    fun `an extract rule name is lowercase ascii, digits and underscore up to 32 characters`() {
        listOf("", "kód", "Kod", "kod-1", "kod 1", "a".repeat(33), "{kod}").forEach { name ->
            val rule = ExtractRule(name = name, kind = ExtractKind.WORD)

            assertEquals(ExtractProblem.NAME, extractProblem(rule)) { name }
        }
        listOf("a", "kod_2", "0", "a".repeat(32)).forEach { name ->
            assertEquals(null, extractProblem(ExtractRule(name = name, kind = ExtractKind.WORD))) { name }
        }
    }

    @Test
    fun `until needs its marker and both texts are capped`() {
        val long = "x".repeat(EXTRACT_MARKER_MAX_LENGTH + 1)
        val fits = "x".repeat(EXTRACT_MARKER_MAX_LENGTH)

        assertEquals(
            ExtractProblem.MISSING_UNTIL,
            extractProblem(ExtractRule(name = "a", kind = ExtractKind.UNTIL)),
        )
        assertEquals(
            ExtractProblem.MISSING_UNTIL,
            extractProblem(ExtractRule(name = "a", kind = ExtractKind.UNTIL, until = "  ")),
        )
        assertEquals(
            ExtractProblem.MARKER_TOO_LONG,
            extractProblem(ExtractRule(name = "a", kind = ExtractKind.UNTIL, until = long)),
        )
        assertEquals(
            ExtractProblem.ANCHOR_TOO_LONG,
            extractProblem(ExtractRule(name = "a", after = long, kind = ExtractKind.WORD)),
        )
        assertEquals(
            null,
            extractProblem(ExtractRule(name = "a", after = " $fits ", kind = ExtractKind.UNTIL, until = fits)),
        )
    }

    @Test
    fun `a marker on a kind that ignores it is dropped rather than kept unchecked`() {
        val long = "x".repeat(EXTRACT_MARKER_MAX_LENGTH + 1)

        assertEquals(
            listOf(ExtractRule(name = "a", kind = ExtractKind.WORD)),
            tidyExtractRules(listOf(ExtractRule(name = "a", kind = ExtractKind.WORD, until = long))),
        )
    }

    @Test
    fun `a label that folds to nothing is blank`() {
        val rule = ExtractRule(name = "a", after = " \u0301 ", kind = ExtractKind.AMOUNT)

        assertEquals(null, rule.anchor)
        assertEquals(null, extractProblem(rule))
        assertEquals(listOf(rule.copy(after = null)), tidyExtractRules(listOf(rule)))
        assertEquals(
            ExtractProblem.MISSING_UNTIL,
            extractProblem(ExtractRule(name = "a", kind = ExtractKind.UNTIL, until = "\u0301")),
        )
    }

    @Test
    fun `more than four extract rules is a defect`() {
        val rules = List(EXTRACT_MAX_RULES + 1) { ExtractRule(name = "v$it", kind = ExtractKind.WORD) }

        assertEquals(
            listOf(ScenarioDefect.TooManyExtractRules(count = EXTRACT_MAX_RULES + 1)),
            validateScenario(notification(rules)),
        )
    }

    @Test
    fun `a valid time and a window crossing midnight are fine`() {
        val overnight = scenario.copy(conditions = listOf(Condition.TimeWindow(from = "23:00", to = "06:30")))

        assertEquals(emptyList<ScenarioDefect>(), validateScenario(overnight))
    }

    @Test
    fun `a day outside the week is a defect`() {
        val days = listOf(0, 8)
        val window = scenario.copy(
            conditions = listOf(Condition.TimeWindow(from = "18:00", to = "23:00", days = days)),
        )

        assertEquals(listOf(ScenarioDefect.InvalidDays(days)), validateScenario(window))
    }

    @Test
    fun `all defects are collected`() {
        val broken = scenario.copy(
            id = "",
            priority = -5,
            size = TileSize.OVERLAY,
            conditions = listOf(
                Condition.HaEvent(code = "a", durationSec = -1),
                Condition.TimeInstant(at = "99:99"),
            ),
        )

        val defects = validateScenario(broken)

        assertEquals(
            setOf(
                ScenarioDefect.BlankId("Scénář"),
                ScenarioDefect.OverlayUnsupported,
                ScenarioDefect.NegativePriority(-5),
                ScenarioDefect.MultipleInstantConditions(2),
                ScenarioDefect.InvalidDuration(-1),
                ScenarioDefect.InvalidTime("time_instant.at", "99:99"),
                ScenarioDefect.LayoutSizeMismatch(TemplateLayout.ICON_TEXT, TileSize.OVERLAY),
            ),
            defects.toSet(),
        )
    }
}
