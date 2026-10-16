package app.cue.launcher.system.aichat

import app.cue.launcher.R

// The labels on the way back into Cue:, in the language its screens show them in; the brief quotes
// them so the chat names exactly what the user sees.
internal data class PasteRoute(
    val settings: String,
    val scenarios: String,
    val card: String,
    val pasteAnswer: String,
    val backups: String,
    val pasteJson: String,
    val handoff: String,
)

internal fun pasteRoute(string: (Int) -> String): PasteRoute = PasteRoute(
    settings = string(R.string.home_menu_settings),
    scenarios = string(R.string.settings_tab_scenarios),
    card = string(R.string.ai_chat_pending_title),
    pasteAnswer = string(R.string.ai_chat_paste_answer),
    backups = string(R.string.settings_tab_backups),
    pasteJson = string(R.string.ai_chat_choice_paste),
    handoff = string(R.string.ai_chat_handoff_title),
)
