package app.cue.launcher.data.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HaStateFormatTest {

    @Test
    fun `a Home Assistant state condition writes the given shape`() {
        val condition = Condition.HaState(entity = "binary_sensor.door", states = listOf("on"), negate = true)

        assertEquals(
            """[{"type":"ha_state","entity":"binary_sensor.door","states":["on"],"negate":true}]""",
            ScenarioFormat.encodeConditions(listOf(condition)),
        )
    }

    @Test
    fun `an imported Home Assistant state condition is tidied`() {
        val padded = """{"type":"ha_state","entity":" Media_Player.TV ","states":[" Playing","","playing","PAUSED "]}"""
        val read = read(padded)

        assertEquals(
            Condition.HaState(entity = "media_player.tv", states = listOf("playing", "paused")),
            read.accepted.single().scenario.conditions.single(),
        )
    }

    @Test
    fun `a Home Assistant state condition without a state or with a malformed entity is a defect`() {
        assertEquals(
            listOf(ScenarioDefect.MissingField("ha_state.states")),
            defects("""{"type":"ha_state","entity":"media_player.tv","states":[" "]}"""),
        )
        assertEquals(
            listOf(ScenarioDefect.InvalidHaEntity("the TV")),
            defects("""{"type":"ha_state","entity":"the TV","states":["on"]}"""),
        )
        assertEquals(
            listOf(ScenarioDefect.MissingField("ha_state.entity")),
            defects("""{"type":"ha_state","entity":"","states":["on"]}"""),
        )
        assertTrue(defects("""{"type":"ha_state","entity":"media_player.tv"}""").single() is ScenarioDefect.Malformed)
    }

    @Test
    fun `a Home Assistant state condition may wait for its entity as a placeholder`() {
        val imported = read("""{"type":"ha_state","entity":"{{PLACEHOLDER:tv}}","states":["playing"]}""")
            .accepted.single()

        assertEquals(setOf("tv"), imported.placeholders)
        assertFalse(imported.asStored().enabled)
    }

    private fun defects(condition: String): List<ScenarioDefect> {
        val read = read(condition)
        assertTrue(read.accepted.isEmpty()) { condition }
        return read.rejected.single().defects
    }

    private fun read(condition: String): ScenarioRead.Ok {
        val json = """
            {"schemaVersion":1,"id":"tv","name":"TV","size":"1x1","conditions":[$condition],
            "content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x"}}]}}
        """.trimIndent().replace("\n", "")
        val read = ScenarioFormat.read(json)
        assertTrue(read is ScenarioRead.Ok) { read.toString() }
        return read as ScenarioRead.Ok
    }
}
