package app.cue.launcher.feature.scenarios.aichat

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.aichat.CandidateProblem
import app.cue.launcher.data.aichat.GroundingProblem
import app.cue.launcher.data.scenario.ScenarioDefect
import app.cue.launcher.feature.scenarios.defectText

internal data class ProblemMessage(@param:StringRes val text: Int, val first: Any? = null, val second: Any? = null)

// The chat gets the English wording with its suggestions; the user gets what is wrong, in their
// language. A defect reads as it does in the import preview, except where that one speaks of a file.
@Composable
internal fun problemText(problem: CandidateProblem): String {
    val message = problemMessage(problem)
    return when {
        message == null && problem is CandidateProblem.Defect -> defectText(problem.defect)
        message == null -> ""
        message.first == null -> stringResource(message.text)
        message.second == null -> stringResource(message.text, message.first)
        else -> stringResource(message.text, message.first, message.second)
    }
}

internal fun problemMessage(problem: CandidateProblem): ProblemMessage? = when (problem) {
    is CandidateProblem.Defect -> pastedDefectMessage(problem.defect)
    is CandidateProblem.ScenarioCount -> ProblemMessage(R.string.ai_chat_problem_count, problem.count)
    is CandidateProblem.Ungrounded -> groundingMessage(problem.problem)
}

private fun pastedDefectMessage(defect: ScenarioDefect): ProblemMessage? = when (defect) {
    ScenarioDefect.Malformed.NotJsonObject -> ProblemMessage(R.string.ai_chat_problem_not_json)
    is ScenarioDefect.UnsupportedSchemaVersion ->
        ProblemMessage(R.string.ai_chat_problem_newer_version, defect.found, defect.supported)

    else -> null
}

private fun groundingMessage(problem: GroundingProblem): ProblemMessage = when (problem) {
    is GroundingProblem.UnknownPackage -> ProblemMessage(R.string.ai_chat_problem_package, problem.packageName)
    is GroundingProblem.UnknownBluetoothDevice -> ProblemMessage(R.string.ai_chat_problem_bluetooth, problem.mac)
    is GroundingProblem.UnknownHaEntity -> ProblemMessage(R.string.ai_chat_problem_ha_entity, problem.entity)
    is GroundingProblem.UnknownWidget ->
        ProblemMessage(R.string.ai_chat_problem_widget, "${problem.packageName}/${problem.provider}")

    is GroundingProblem.UnknownAppAction ->
        ProblemMessage(R.string.ai_chat_problem_app_action, problem.packageName, problem.target)

    is GroundingProblem.ServiceDomainMismatch ->
        ProblemMessage(R.string.ai_chat_problem_service_misfit, problem.service, problem.entity)

    is GroundingProblem.HaServiceMisfit ->
        ProblemMessage(R.string.ai_chat_problem_service_misfit, problem.service, problem.entity)

    is GroundingProblem.MalformedHaService ->
        ProblemMessage(R.string.ai_chat_problem_service_malformed, problem.service)

    is GroundingProblem.UnknownHaService -> ProblemMessage(R.string.ai_chat_problem_service, problem.service)
    is GroundingProblem.HaServiceTakesNoEntity ->
        ProblemMessage(R.string.ai_chat_problem_service_no_entity, problem.service, problem.entity)

    is GroundingProblem.UnavailableSystemAction ->
        ProblemMessage(R.string.ai_chat_problem_system_action, problem.action.jsonName)
}

