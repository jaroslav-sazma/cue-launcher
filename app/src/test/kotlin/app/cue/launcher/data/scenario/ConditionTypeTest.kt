package app.cue.launcher.data.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConditionTypeTest {

    @Test
    fun `there are nine state condition types and seven instant ones`() {
        assertEquals(9, ConditionType.entries.count { !it.isInstant })
        assertEquals(7, ConditionType.entries.count { it.isInstant })
    }

    @Test
    fun `every type from the catalog parses and falls into the right family`() {
        ConditionType.entries.forEach { type ->
            val condition = readCondition(type)

            assertEquals(
                type.isInstant,
                condition is Condition.InstantCondition,
            ) { "${type.jsonName} → $condition" }
            assertEquals(
                !type.isInstant,
                condition is Condition.StateCondition,
            ) { "${type.jsonName} → $condition" }
        }
    }

    @Test
    fun `only an instant condition carries a duration`() {
        ConditionType.entries.filter { it.isInstant }.forEach { type ->
            val condition = readCondition(type, extras = ""","durationSec":30""")

            assertEquals(30, (condition as Condition.InstantCondition).durationSec) { type.jsonName }
        }
    }

    @Test
    fun `type names translate in both directions`() {
        ConditionType.entries.forEach { type ->
            assertEquals(type, ConditionType.fromJsonName(type.jsonName))
        }
        assertEquals(null, ConditionType.fromJsonName("weather"))
    }

    @Test
    fun `size, layout, and widget names translate in both directions`() {
        TileSize.entries.forEach { assertEquals(it, TileSize.fromJsonName(it.jsonName)) }
        TemplateLayout.entries.forEach { assertEquals(it, TemplateLayout.fromJsonName(it.jsonName)) }
        LauncherWidgetType.entries.forEach { assertEquals(it, LauncherWidgetType.fromJsonName(it.jsonName)) }
        SystemActionType.entries.forEach { assertEquals(it, SystemActionType.fromJsonName(it.jsonName)) }
    }

    private fun readCondition(type: ConditionType, extras: String = ""): Condition {
        val json = """
            {"schemaVersion":1,"id":"s1","name":"Scénář","size":"1x1",
            "conditions":[{"type":"${type.jsonName}"${REQUIRED_FIELDS.getValue(type)}$extras}],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}}
        """.trimIndent().replace("\n", "")

        val read = ScenarioFormat.read(json)
        assertTrue(read is ScenarioRead.Ok) { "${type.jsonName}: $read" }
        val ok = read as ScenarioRead.Ok
        assertTrue(ok.rejected.isEmpty()) { "${type.jsonName}: ${ok.rejected}" }
        return ok.accepted.single().scenario.conditions.single()
    }

    private companion object {

        val REQUIRED_FIELDS = mapOf(
            ConditionType.TIME_WINDOW to ""","from":"18:00","to":"23:00"""",
            ConditionType.BT_CONNECTED to "",
            ConditionType.WIFI to ""","mode":"any"""",
            ConditionType.CHARGING to "",
            ConditionType.AUDIO_OUTPUT to "",
            ConditionType.MEDIA_PLAYING to "",
            ConditionType.RINGER_MODE to ""","mode":"dnd"""",
            ConditionType.CALENDAR_EVENT to "",
            ConditionType.HA_STATE to ""","entity":"media_player.tv","states":["playing"]""",
            ConditionType.TIME_INSTANT to ""","at":"17:00"""",
            ConditionType.ALARM to "",
            ConditionType.BT_DISCONNECTED to "",
            ConditionType.NOTIFICATION to ""","package":"cz.alza"""",
            ConditionType.HA_EVENT to ""","code":"zvonek"""",
            ConditionType.NOTIFICATION_ENDED to ""","package":"com.google.android.apps.maps"""",
            ConditionType.PLACE_SAVED to "",
        )
    }
}
