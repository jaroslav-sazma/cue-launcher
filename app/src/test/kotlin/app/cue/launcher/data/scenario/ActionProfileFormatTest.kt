package app.cue.launcher.data.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActionProfileFormatTest {

    @Test
    fun `an app action may live in the work profile, and one of the personal profile writes no profile`() {
        val work = oneLine(
            """
            {"schemaVersion":1,"id":"a","name":"a","enabled":true,"priority":0,"size":"1x1",
            "conditions":[],"content":{"kind":"template","layout":"icon_text","items":[
            {"label":"Mail","action":[{"kind":"app","package":"com.microsoft.office.outlook","profile":"work"},
            {"kind":"shortcut","package":"com.microsoft.office.outlook","shortcutId":"compose","profile":"work"},
            {"kind":"capability","package":"com.microsoft.office.outlook",
            "capability":"actions.intent.CREATE_MESSAGE","profile":"work"}]}]}}
            """,
        )
        val personal = work.replace(""","profile":"work"""", "")

        val scenario = accepted(work).single().scenario
        val actions = (scenario.content as TileContent.Template).items.single().actions
        val older = accepted(personal).single().scenario
        val olderActions = (older.content as TileContent.Template).items.single().actions

        assertEquals(listOf(AppProfile.WORK), actions.map { it.actionApp?.profile }.distinct())
        assertEquals(scenario, roundtrip(scenario))
        assertEquals(listOf(null), olderActions.map { it.actionApp?.profile }.distinct())
        assertFalse("profile" in ScenarioFormat.canonicalJson(older))
    }

    @Test
    fun `an unknown profile rejects its scenario`() {
        val json = oneLine(
            """
            {"schemaVersion":1,"id":"a","name":"a","enabled":true,"priority":0,"size":"1x1",
            "conditions":[],"content":{"kind":"template","layout":"icon_text","items":[
            {"action":{"kind":"app","package":"com.x","profile":"private"}}]}}
            """,
        )

        val read = ScenarioFormat.read(json)

        assertFalse(read is ScenarioRead.Ok && read.accepted.isNotEmpty()) { read.toString() }
    }

    private fun oneLine(json: String): String = json.trimIndent().replace("\n", "")

    private fun roundtrip(scenario: Scenario): Scenario? =
        ok(ScenarioFormat.encodeScenario(scenario)).accepted.singleOrNull()?.scenario

    private fun ok(text: String): ScenarioRead.Ok {
        val read = ScenarioFormat.read(text)
        assertTrue(read is ScenarioRead.Ok) { "expected Ok, got $read" }
        return read as ScenarioRead.Ok
    }

    private fun accepted(text: String): List<ImportedScenario> {
        val ok = ok(text)
        assertTrue(ok.rejected.isEmpty()) { ok.rejected.toString() }
        return ok.accepted
    }
}
