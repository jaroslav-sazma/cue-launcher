package app.cue.launcher.feature.scenarios.aichat

import app.cue.launcher.R
import app.cue.launcher.data.aichat.CandidateProblem
import app.cue.launcher.data.aichat.GroundingProblem
import app.cue.launcher.data.scenario.ScenarioDefect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProblemMessageTest {

    @Test
    fun `a defect keeps the import preview's own text`() {
        assertNull(problemMessage(CandidateProblem.Defect(ScenarioDefect.NegativePriority(-1))))
        assertNull(problemMessage(CandidateProblem.Defect(ScenarioDefect.MissingField("id"))))
    }

    @Test
    fun `a defect the import preview words for a file speaks of the pasted text`() {
        assertEquals(
            ProblemMessage(R.string.ai_chat_problem_not_json),
            problemMessage(CandidateProblem.Defect(ScenarioDefect.Malformed.NotJsonObject)),
        )
        assertEquals(
            ProblemMessage(R.string.ai_chat_problem_newer_version, 2, 1),
            problemMessage(CandidateProblem.Defect(ScenarioDefect.UnsupportedSchemaVersion(found = 2, supported = 1))),
        )
    }

    @Test
    fun `the scenario count names how many arrived`() {
        assertEquals(
            ProblemMessage(R.string.ai_chat_problem_count, 3),
            problemMessage(CandidateProblem.ScenarioCount(3)),
        )
    }

    @Test
    fun `each grounding problem names what the phone lacks`() {
        val expected = mapOf(
            GroundingProblem.UnknownPackage("com.x") to
                ProblemMessage(R.string.ai_chat_problem_package, "com.x"),
            GroundingProblem.UnknownBluetoothDevice("AA:BB") to
                ProblemMessage(R.string.ai_chat_problem_bluetooth, "AA:BB"),
            GroundingProblem.UnknownHaEntity("light.x") to
                ProblemMessage(R.string.ai_chat_problem_ha_entity, "light.x"),
            GroundingProblem.UnknownWidget("com.w", "com.w.Provider") to
                ProblemMessage(R.string.ai_chat_problem_widget, "com.w/com.w.Provider"),
            GroundingProblem.UnknownAppAction("com.a", "compose") to
                ProblemMessage(R.string.ai_chat_problem_app_action, "com.a", "compose"),
            GroundingProblem.ServiceDomainMismatch("light.toggle", "cover.gate") to
                ProblemMessage(R.string.ai_chat_problem_service_misfit, "light.toggle", "cover.gate"),
            GroundingProblem.HaServiceMisfit("cover.open_cover", "cover.blind", listOf("cover.toggle")) to
                ProblemMessage(R.string.ai_chat_problem_service_misfit, "cover.open_cover", "cover.blind"),
            GroundingProblem.MalformedHaService("dnd_on") to
                ProblemMessage(R.string.ai_chat_problem_service_malformed, "dnd_on"),
            GroundingProblem.UnknownHaService("light.blink", listOf("light.toggle")) to
                ProblemMessage(R.string.ai_chat_problem_service, "light.blink"),
            GroundingProblem.HaServiceTakesNoEntity("scene.reload", "scene.evening") to
                ProblemMessage(R.string.ai_chat_problem_service_no_entity, "scene.reload", "scene.evening"),
        )

        expected.forEach { (problem, message) ->
            assertEquals(message, problemMessage(CandidateProblem.Ungrounded(problem)), problem.toString())
        }
    }
}
