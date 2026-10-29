package app.cue.launcher.data.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MacLockFormatTest {

    @Test
    fun `a Mac locked condition imports with and without negate`() {
        val read = read("""{"schemaVersion":1,"name":"Set","scenarios":[${scenario(PLAIN)},${scenario(NEGATED)}]}""")

        assertTrue(read.rejected.isEmpty()) { read.rejected.toString() }
        assertEquals(
            listOf(listOf(Condition.MacLocked()), listOf(Condition.MacLocked(negate = true))),
            read.accepted.map { it.scenario.conditions },
        )
    }

    @Test
    fun `a Mac locked condition writes its own type and reads back the same`() {
        val desk = read(scenario(NEGATED)).accepted.single().scenario

        val canonical = ScenarioFormat.canonicalJson(desk)

        assertTrue(""""conditions":[{"type":"mac_locked","negate":true}]""" in canonical) { canonical }
        assertEquals(listOf(desk), read(ScenarioFormat.encodeScenario(desk)).accepted.map { it.scenario })
    }

    @Test
    fun `a Mac locked condition with a duration is refused like any state condition`() {
        val read = read(scenario("""{"type":"mac_locked","durationSec":60}"""))

        assertTrue(read.accepted.isEmpty())
        assertEquals(
            listOf(ScenarioDefect.DurationOnStateCondition("mac_locked")),
            read.rejected.single().defects,
        )
    }

    @Test
    fun `the Lock Mac action imports and writes its own value`() {
        val scenario = read(scenario(PLAIN, action = LOCK)).accepted.single().scenario
        val item = (scenario.content as TileContent.Template).items.single()

        assertEquals(listOf(TapAction.SystemAction(SystemActionType.MAC_LOCK)), item.actions)
        assertTrue(LOCK in ScenarioFormat.canonicalJson(scenario))
        assertEquals(listOf(scenario), read(ScenarioFormat.encodeScenario(scenario)).accepted.map { it.scenario })
    }

    private fun scenario(condition: String, action: String = DND_ON): String =
        """{"schemaVersion":1,"id":"${(condition + action).hashCode()}","name":"Desk","size":"1x1",""" +
            """"conditions":[$condition],"content":{"kind":"template","layout":"icon_text","items":[""" +
            """{"action":$action}]}}"""

    private fun read(text: String): ScenarioRead.Ok {
        val read = ScenarioFormat.read(text)
        assertTrue(read is ScenarioRead.Ok) { read.toString() }
        return read as ScenarioRead.Ok
    }

    private companion object {
        const val PLAIN = """{"type":"mac_locked"}"""
        const val NEGATED = """{"type":"mac_locked","negate":true}"""
        const val DND_ON = """{"kind":"system","action":"dnd_on"}"""
        const val LOCK = """{"kind":"system","action":"mac_lock"}"""
    }
}
