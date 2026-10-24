package app.cue.launcher.data.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EndEventFormatTest {

    private val musicScenario = Scenario(
        id = "headphones-music",
        name = "Headphones → music",
        priority = 500,
        size = TileSize.GRID_1X1,
        conditions = listOf(Condition.AudioOutput()),
        content = TileContent.Template(
            layout = TemplateLayout.ICON_TEXT,
            items = listOf(TemplateItem(label = "Hudba", actions = listOf(TapAction.App("com.x")))),
        ),
    )

    @Test
    fun `every end event roundtrips on an instant condition`() {
        val events = listOf(
            EndEvent.AtTime(at = "17:00"),
            EndEvent.AtTime(at = "06:30", days = listOf(1, 5)),
            EndEvent.Alarm,
            EndEvent.BtDisconnected(),
            EndEvent.BtDisconnected(mac = "{{PLACEHOLDER:bt_car}}"),
            EndEvent.Notification(packageName = "cz.alza"),
            EndEvent.Notification(packageName = "cz.alza", contains = "picked up"),
            EndEvent.HaEvent(code = "door_closed"),
        )
        val conditions = listOf(
            Condition.TimeInstant(at = "08:00", until = EndEvent.Alarm),
            Condition.Alarm(until = EndEvent.AtTime(at = "09:00")),
            Condition.BtDisconnected(until = EndEvent.HaEvent(code = "home")),
            Condition.NotificationEnded(packageName = "com.google.android.apps.maps", until = EndEvent.Alarm),
            Condition.PlaceSaved(until = EndEvent.BtDisconnected()),
        ) + events.map { Condition.NotificationFrom(packageName = "cz.zasilkovna.app", until = it) } +
            events.map { Condition.HaEvent(code = "doorbell", durationSec = 3600, until = it) }

        conditions.forEach { condition ->
            val scenario = musicScenario.copy(conditions = listOf(condition))
            assertEquals(scenario, roundtrip(scenario)) { "condition $condition" }
        }
    }

    @Test
    fun `an end event writes the type of the condition it speaks for`() {
        assertEquals(
            oneLine(
                """
                [{"type":"notification","package":"cz.alza","contains":null,"durationSec":null,
                "until":{"type":"time_instant","at":"17:00","days":[1,2,3,4,5,6,7]}},
                {"type":"alarm","durationSec":null,"until":{"type":"alarm"}}]
                """,
            ),
            ScenarioFormat.encodeConditions(
                listOf(
                    Condition.NotificationFrom(packageName = "cz.alza", until = EndEvent.AtTime(at = "17:00")),
                    Condition.Alarm(until = EndEvent.Alarm),
                ),
            ),
        )
    }

    @Test
    fun `an instant condition without an end event carries no until field`() {
        val conditions = listOf(
            Condition.TimeInstant(at = "17:00"),
            Condition.Alarm(),
            Condition.BtDisconnected(),
            Condition.NotificationFrom(packageName = "cz.alza"),
            Condition.HaEvent(code = "doorbell"),
            Condition.NotificationEnded(packageName = "cz.alza"),
            Condition.PlaceSaved(),
        )

        val json = ScenarioFormat.encodeConditions(conditions)

        assertFalse("until" in json) { json }
    }

    @Test
    fun `every end event type is named the way its variant is written`() {
        val samples = mapOf(
            EndEventType.TIME_INSTANT to EndEvent.AtTime(at = "17:00"),
            EndEventType.ALARM to EndEvent.Alarm,
            EndEventType.BT_DISCONNECTED to EndEvent.BtDisconnected(),
            EndEventType.NOTIFICATION to EndEvent.Notification(packageName = "cz.alza"),
            EndEventType.HA_EVENT to EndEvent.HaEvent(code = "doorbell"),
        )

        assertEquals(EndEventType.entries.toSet(), samples.keys)
        samples.forEach { (type, event) ->
            val json = scenarioJson.encodeToString(EndEvent.serializer(), event)

            assertTrue(json.startsWith("""{"type":"${type.jsonName}"""")) { json }
            assertTrue(type.condition.isInstant) { type.jsonName }
            assertEquals(type, EndEventType.fromJsonName(type.jsonName))
        }
        assertNull(EndEventType.fromJsonName("charging"))
    }

    @Test
    fun `every end event variant has an end event type and no type is left without a variant`() {
        val variants = EndEvent.serializer().descriptor.getElementDescriptor(1)
        val serialNames = (0 until variants.elementsCount).map(variants::getElementName)

        assertEquals(EndEventType.entries.map { it.jsonName }.sorted(), serialNames.sorted())
    }

    @Test
    fun `an imported end event has its package tidied and nothing else`() {
        val padded = musicScenario.copy(
            match = ConditionMatch.ANY,
            conditions = listOf(
                Condition.Alarm(until = EndEvent.Notification(packageName = " cz.alza ", contains = " picked up ")),
                Condition.HaEvent(code = "doorbell", until = EndEvent.HaEvent(code = " door_closed ")),
                Condition.PlaceSaved(until = EndEvent.AtTime(at = "17:00", days = listOf(5, 1, 1))),
            ),
        )

        assertEquals(
            listOf(
                Condition.Alarm(until = EndEvent.Notification(packageName = "cz.alza", contains = " picked up ")),
                Condition.HaEvent(code = "doorbell", until = EndEvent.HaEvent(code = " door_closed ")),
                Condition.PlaceSaved(until = EndEvent.AtTime(at = "17:00", days = listOf(5, 1, 1))),
            ),
            roundtrip(padded)?.conditions,
        )
    }

    @Test
    fun `a null end event reads as none`() {
        val text = scenarioWith("""{"type":"ha_event","code":"doorbell","until":null}""")

        assertEquals(
            Condition.HaEvent(code = "doorbell"),
            ok(text).accepted.single().scenario.conditions.single(),
        )
    }

    @Test
    fun `an end event that is a list or lacks its time rejects only its own scenario`() {
        val text = oneLine(
            """
            {"schemaVersion":1,"name":"Set","scenarios":[
            {"id":"list-until","name":"List end","size":"1x1",
            "conditions":[{"type":"ha_event","code":"doorbell","until":[]}],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}},
            {"id":"no-at","name":"End without a time","size":"1x1",
            "conditions":[{"type":"alarm","until":{"type":"time_instant"}}],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}},
            {"id":"good","name":"Good","size":"1x1","conditions":[],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}}]}
            """,
        )

        val ok = ok(text)

        assertEquals(listOf("good"), ok.accepted.map { it.scenario.id })
        assertEquals(2, ok.rejected.size)
        ok.rejected.forEach { rejected ->
            val defect = rejected.defects.single()
            assertTrue(defect is ScenarioDefect.Malformed.Unreadable) { "$defect" }
        }
    }

    @Test
    fun `a condition type that is no end event type is an unknown end event`() {
        listOf("wifi", "notification_ended").forEach { type ->
            val text = scenarioWith("""{"type":"alarm","until":{"type":"$type"}}""")

            assertEquals(
                listOf(ScenarioDefect.UnknownEnum("conditions[].until.type", type)),
                ok(text).rejected.single().defects,
            ) { type }
        }
    }

    @Test
    fun `a zero duration beside a valid end event is still a defect`() {
        val text = scenarioWith("""{"type":"ha_event","code":"doorbell","durationSec":0,"until":{"type":"alarm"}}""")

        assertEquals(listOf(ScenarioDefect.InvalidDuration(0)), ok(text).rejected.single().defects)
    }

    @Test
    fun `an end event other than an object rejects only its own scenario`() {
        val text = oneLine(
            """
            {"schemaVersion":1,"name":"Set","scenarios":[
            {"id":"bad-until","name":"Text end","size":"1x1",
            "conditions":[{"type":"ha_event","code":"doorbell","until":"17:00"}],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}},
            {"id":"good","name":"Good","size":"1x1","conditions":[],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}}]}
            """,
        )

        val ok = ok(text)

        assertEquals(listOf("good"), ok.accepted.map { it.scenario.id })
        val defect = ok.rejected.single().defects.single()
        assertTrue(defect is ScenarioDefect.Malformed.Unreadable) { "$defect" }
        assertTrue("until" in (defect as ScenarioDefect.Malformed.Unreadable).detail.orEmpty()) { "$defect" }
    }

    @Test
    fun `an unknown end event type is named rather than reported as unreadable`() {
        val text = oneLine(
            """
            {"schemaVersion":1,"name":"Set","scenarios":[
            {"id":"bad-until","name":"Foreign end","size":"1x1",
            "conditions":[{"type":"ha_event","code":"doorbell","until":{"type":"sunset"}}],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}},
            {"id":"no-type","name":"End without a type","size":"1x1",
            "conditions":[{"type":"alarm","until":{"code":"doorbell"}}],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}},
            {"id":"good","name":"Good","size":"1x1",
            "conditions":[{"type":"ha_event","code":"doorbell","until":{"type":"ha_event","code":"door_closed"}}],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}}]}
            """,
        )

        val ok = ok(text)

        assertEquals(listOf("good"), ok.accepted.map { it.scenario.id })
        assertEquals(
            Condition.HaEvent(code = "doorbell", until = EndEvent.HaEvent(code = "door_closed")),
            ok.accepted.single().scenario.conditions.single(),
        )
        assertEquals(
            listOf(
                listOf(ScenarioDefect.UnknownEnum("conditions[].until.type", "sunset")),
                listOf(ScenarioDefect.UnknownEnum("conditions[].until.type", "")),
            ),
            ok.rejected.map { it.defects },
        )
    }

    @Test
    fun `an end event on a state condition is a defect`() {
        mapOf(
            "charging" to """{"type":"charging","until":{"type":"alarm"}}""",
            "wifi" to """{"type":"wifi","mode":"any","until":{"type":"alarm"}}""",
        ).forEach { (type, condition) ->
            assertEquals(
                listOf(ScenarioDefect.UntilOnStateCondition(type)),
                ok(scenarioWith(condition)).rejected.single().defects,
            ) { type }
        }
    }

    private fun oneLine(json: String): String = json.trimIndent().replace("\n", "")

    private fun scenarioWith(condition: String): String = oneLine(
        """
        {"schemaVersion":1,"id":"s1","name":"Scénář","size":"1x1",
        "conditions":[$condition],
        "content":{"kind":"template","layout":"icon_text","items":[
        {"action":{"kind":"app","package":"com.x"}}]}}
        """,
    )

    private fun roundtrip(scenario: Scenario): Scenario? =
        ok(ScenarioFormat.encodeScenario(scenario)).accepted.singleOrNull()?.scenario

    private fun ok(text: String): ScenarioRead.Ok {
        val read = ScenarioFormat.read(text)
        assertTrue(read is ScenarioRead.Ok) { "expected Ok, got $read" }
        return read as ScenarioRead.Ok
    }
}
