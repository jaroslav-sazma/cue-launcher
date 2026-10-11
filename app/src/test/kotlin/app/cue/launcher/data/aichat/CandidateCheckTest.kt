package app.cue.launcher.data.aichat

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioDefect
import app.cue.launcher.data.scenario.ScenarioFormat
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CandidateCheckTest {

    private val scenario = Scenario(
        id = "charging",
        name = "Charging",
        size = TileSize.GRID_1X1,
        conditions = listOf(Condition.Charging()),
        content = TileContent.Template(
            TemplateLayout.ICON_TEXT,
            items = listOf(TemplateItem(label = "Clock", actions = listOf(TapAction.App("com.clock")))),
        ),
    )
    private val valid = ScenarioFormat.encodeScenario(scenario)

    @Test
    fun `a valid candidate is the scenario it describes`() {
        assertEquals(CandidateCheck.Valid(scenario), checkCandidate(valid))
    }

    @Test
    fun `a candidate the import gate rejects is invalid with its defects`() {
        val check = checkCandidate(valid.replace("\"1x1\"", "\"3x3\""))

        val invalid = assertInstanceOf(CandidateCheck.Invalid::class.java, check)
        assertTrue(invalid.problems.isNotEmpty() && invalid.problems.all { it is CandidateProblem.Defect })
    }

    @Test
    fun `text that is no JSON at all is invalid`() {
        val check = checkCandidate("Sorry, I cannot help with that.")

        assertInstanceOf(CandidateCheck.Invalid::class.java, check)
    }

    @Test
    fun `a draft with a placeholder arrives disabled like an import`() {
        val gate = scenario.copy(
            content = TileContent.Template(
                TemplateLayout.ICON_TEXT,
                items = listOf(
                    TemplateItem(
                        label = "Gate",
                        actions = listOf(TapAction.HaAction("cover.open_cover", entity = "{{PLACEHOLDER:gate}}")),
                    ),
                ),
            ),
        )

        val check = checkCandidate(ScenarioFormat.encodeScenario(gate))

        assertEquals(CandidateCheck.Valid(gate.copy(enabled = false)), check)
    }

    @Test
    fun `a set with more than one scenario is not a candidate`() {
        val set = ScenarioFormat.encodeSet("Two", listOf(scenario, scenario.copy(id = "other")))

        assertEquals(CandidateCheck.Invalid(listOf(CandidateProblem.ScenarioCount(2))), checkCandidate(set))
    }

    @Test
    fun `a set holding exactly one scenario is a candidate`() {
        val set = ScenarioFormat.encodeSet("One", listOf(scenario))

        assertEquals(CandidateCheck.Valid(scenario), checkCandidate(set))
    }

    @Test
    fun `a fenced candidate with preamble is read`() {
        val fenced = "Here it is:\n```json\n$valid\n```"

        assertEquals(CandidateCheck.Valid(scenario), checkCandidate(fenced))
    }

    @Test
    fun `braces in the prose after a fenced block do not spoil it`() {
        val answer = "Here it is:\n```json\n$valid\n```\nFill in `{{PLACEHOLDER:bt_car}}` with your car."

        assertEquals(CandidateCheck.Valid(scenario), checkCandidate(answer))
    }

    @Test
    fun `braces in the prose on both sides of an unfenced object do not spoil it`() {
        val answer = "It turns on {ha:light.porch} at dusk.\n$valid\nThen fill in {{PLACEHOLDER:gate}}."

        assertEquals(CandidateCheck.Valid(scenario), checkCandidate(answer))
    }

    @Test
    fun `a stray opening brace in the prose does not swallow the object`() {
        assertEquals(valid, candidateBody("Use { for a set.\n$valid"))
    }

    @Test
    fun `the json fence wins over another fence before it`() {
        val answer = "Inventory:\n```text\n{ \"x\": 1 }\n```\nScenario:\n```json\n$valid\n```"

        assertEquals(valid, candidateBody(answer))
    }

    @Test
    fun `of two json fences the first is the candidate`() {
        val answer = "```json\n$valid\n```\nOr:\n```json\n{\"other\": true}\n```"

        assertEquals(valid, candidateBody(answer))
    }

    @Test
    fun `a bare fence counts only when it holds an object`() {
        assertEquals(valid, candidateBody("```\nnot this\n```\n```\n$valid\n```"))
    }

    @Test
    fun `braces and quotes inside JSON strings do not end the object`() {
        val tricky = """{"name":"a } b { \" c","nested":{"deep":{"x":"}"}}}"""

        assertEquals(tricky, candidateBody("{ha:x} $tricky {{PLACEHOLDER:y}}"))
    }

    @Test
    fun `the outermost object is taken whole, not an inner one`() {
        val nested = """{"a":{"b":{"c":1}},"d":2}"""

        assertEquals(nested, candidateBody("Answer: $nested."))
    }

    @Test
    fun `when nothing parses the first balanced object goes to the gate`() {
        assertEquals("{ broken: json }", candidateBody("Sorry { broken: json } and {another}"))
    }

    @Test
    fun `a broken json fence is what the gate sees`() {
        val check = checkCandidate("```json\n{\"id\": \"x\",}\n```\n{\"ok\": 1}")

        val notJson = CandidateProblem.Defect(ScenarioDefect.Malformed.NotJsonObject)
        assertEquals(CandidateCheck.Invalid(listOf(notJson)), check)
    }

    @Test
    fun `an answer cut off mid-object reaches the gate whole, not as an inner fragment`() {
        val cut = valid.dropLast(valid.length / 2)

        assertEquals(cut, candidateBody("Here: $cut"))
    }

    @Test
    fun `text without any JSON stays as it is`() {
        assertEquals("Sorry, I cannot help.", candidateBody("Sorry, I cannot help."))
    }

    @Test
    fun `every problem has an English one-liner`() {
        val problems = listOf(
            CandidateProblem.ScenarioCount(0),
            CandidateProblem.Defect(ScenarioDefect.NegativePriority(-1)),
            CandidateProblem.Ungrounded(GroundingProblem.UnknownPackage("com.missing")),
        )

        assertTrue(problems.all { it.description().isNotBlank() && '\n' !in it.description() })
    }
}
