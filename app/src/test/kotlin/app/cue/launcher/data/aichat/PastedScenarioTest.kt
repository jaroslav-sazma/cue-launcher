package app.cue.launcher.data.aichat

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioFormat
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PastedScenarioTest {

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
    private val answer = "Here it is:\n```json\n${ScenarioFormat.encodeScenario(scenario)}\n```\nCopy it into Cue:."

    @Test
    fun `a pasted answer the phone can run is valid`() = runTest {
        assertEquals(CandidateCheck.Valid(scenario), checkPasted(answer) { emptyList() })
    }

    @Test
    fun `what the phone lacks makes the answer invalid with the grounding problems`() = runTest {
        val missing = GroundingProblem.UnknownPackage("com.clock")

        val check = checkPasted(answer) { listOf(missing) }

        assertEquals(CandidateCheck.Invalid(listOf(CandidateProblem.Ungrounded(missing))), check)
    }

    @Test
    fun `a rejected answer is not grounded at all`() = runTest {
        var grounded = false

        val check = checkPasted("no scenario here") {
            grounded = true
            emptyList()
        }

        assertInstanceOf(CandidateCheck.Invalid::class.java, check)
        assertTrue(!grounded)
    }

    @Test
    fun `a set of two scenarios is refused, sets stay with the file import`() = runTest {
        val set = ScenarioFormat.encodeSet("Two", listOf(scenario, scenario.copy(id = "other")))

        val check = assertInstanceOf(CandidateCheck.Invalid::class.java, checkPasted(set) { emptyList() })

        assertEquals(listOf(CandidateProblem.ScenarioCount(2)), check.problems)
    }
}
