package app.cue.launcher.data.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MacNearbyFormatTest {

    @Test
    fun `a Mac nearby condition imports with and without negate`() {
        val read = read("""{"schemaVersion":1,"name":"Set","scenarios":[${scenario(PLAIN)},${scenario(NEGATED)}]}""")

        assertTrue(read.rejected.isEmpty()) { read.rejected.toString() }
        assertEquals(
            listOf(listOf(Condition.MacNearby()), listOf(Condition.MacNearby(negate = true))),
            read.accepted.map { it.scenario.conditions },
        )
    }

    @Test
    fun `a Mac nearby condition writes its own type and reads back the same`() {
        val desk = read(scenario(NEGATED)).accepted.single().scenario

        val canonical = ScenarioFormat.canonicalJson(desk)

        assertTrue(""""conditions":[{"type":"mac_nearby","negate":true}]""" in canonical) { canonical }
        assertEquals(listOf(desk), read(ScenarioFormat.encodeScenario(desk)).accepted.map { it.scenario })
    }

    @Test
    fun `a Mac nearby condition with a duration is refused like any state condition`() {
        val read = read(scenario("""{"type":"mac_nearby","durationSec":60}"""))

        assertTrue(read.accepted.isEmpty())
        assertEquals(
            listOf(ScenarioDefect.DurationOnStateCondition("mac_nearby")),
            read.rejected.single().defects,
        )
    }

    private fun scenario(condition: String): String =
        """{"schemaVersion":1,"id":"${condition.hashCode()}","name":"Desk","size":"1x1","conditions":[$condition],""" +
            """"content":{"kind":"template","layout":"icon_text","items":[""" +
            """{"action":{"kind":"system","action":"dnd_on"}}]}}"""

    private fun read(text: String): ScenarioRead.Ok {
        val read = ScenarioFormat.read(text)
        assertTrue(read is ScenarioRead.Ok) { read.toString() }
        return read as ScenarioRead.Ok
    }

    private companion object {
        const val PLAIN = """{"type":"mac_nearby"}"""
        const val NEGATED = """{"type":"mac_nearby","negate":true}"""
    }
}
