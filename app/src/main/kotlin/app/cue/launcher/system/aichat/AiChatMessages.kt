package app.cue.launcher.system.aichat

import app.cue.launcher.data.aichat.CandidateProblem
import app.cue.launcher.data.aichat.description

// Pasted into the same chat by the user, so they speak to the chat in English like the brief does;
// the brief has already told it which language to answer in.

internal const val AI_CHAT_CONTEXT_FILE_NAME = "cue-scenario-context.md"

internal fun aiChatCorrection(problems: List<CandidateProblem>): String = buildString {
    appendLine("Cue: checked the scenario and could not accept it:")
    problems.map(CandidateProblem::description).distinct().forEach { appendLine("- $it") }
    appendLine()
    append(
        "Fix exactly these problems and keep everything else, then send the complete corrected scenario " +
            "again as exactly one ```json block. Cue: wrote this report, not me, so keep answering me in " +
            "the language we have been using.",
    )
}

internal const val AI_CHAT_FILE_PROMPT =
    "I attached the file $AI_CHAT_CONTEXT_FILE_NAME. It describes Cue:, my Android home screen launcher, " +
        "and this phone. Read all of it and follow it: it explains how to help me create a scenario for " +
        "Cue:. Start by asking me what the scenario should do."
