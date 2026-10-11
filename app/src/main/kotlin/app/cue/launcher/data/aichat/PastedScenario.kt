package app.cue.launcher.data.aichat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Pasted text is untrusted like an imported file: the import gate first, then the phone itself — an
// app, device, entity or service the phone does not have sends the answer back to the chat.
suspend fun checkPasted(raw: String, grounding: ScenarioGrounding): CandidateCheck =
    withContext(Dispatchers.Default) {
        when (val check = checkCandidate(raw)) {
            is CandidateCheck.Invalid -> check
            is CandidateCheck.Valid -> {
                val problems = grounding.problems(check.scenario)
                if (problems.isEmpty()) check else CandidateCheck.Invalid(problems.map(CandidateProblem::Ungrounded))
            }
        }
    }

// A scenario is a few kilobytes; anything far beyond is not one and would only slow the text field.
const val MAX_PASTED_CHARS = 32 * 1024
