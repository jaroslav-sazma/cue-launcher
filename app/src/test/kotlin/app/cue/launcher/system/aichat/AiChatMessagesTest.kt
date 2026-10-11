package app.cue.launcher.system.aichat

import app.cue.launcher.data.aichat.CandidateProblem
import app.cue.launcher.data.aichat.GroundingProblem
import app.cue.launcher.data.aichat.description
import app.cue.launcher.data.scenario.ScenarioDefect
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiChatMessagesTest {

    @Test
    fun `the correction lists every problem once, one line each, and asks for the whole scenario again`() {
        val problems = listOf(
            CandidateProblem.Defect(ScenarioDefect.LayoutSizeMismatch(TemplateLayout.FOUR_ICONS, TileSize.GRID_1X1)),
            CandidateProblem.ScenarioCount(2),
            CandidateProblem.Ungrounded(GroundingProblem.UnknownPackage("com.missing")),
            CandidateProblem.Ungrounded(GroundingProblem.UnknownHaService("light.blink", listOf("light.toggle"))),
            CandidateProblem.ScenarioCount(2),
        )

        val text = aiChatCorrection(problems)

        val listed = text.lines().filter { it.startsWith("- ") }
        assertEquals(problems.distinct().map { "- ${it.description()}" }, listed)
        assertTrue("complete corrected scenario" in text && "exactly one ```json block" in text, text)
    }

    @Test
    fun `the prompt for an attached brief names the file and asks for the first question`() {
        assertTrue(AI_CHAT_CONTEXT_FILE_NAME in AI_CHAT_FILE_PROMPT)
        assertTrue(AI_CHAT_CONTEXT_FILE_NAME.endsWith(".md"))
        assertTrue("asking me what the scenario should do" in AI_CHAT_FILE_PROMPT)
    }
}
