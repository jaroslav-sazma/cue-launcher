package app.cue.launcher.feature.scenarios

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.designsystem.Flash
import app.cue.launcher.designsystem.FlashState
import app.cue.launcher.engine.SystemState

internal sealed interface ScenarioNotice {

    val isUndoable: Boolean get() = this is Saved || this is Deleted

    data class Saved(val scenario: Scenario) : ScenarioNotice

    data class Deleted(val scenario: Scenario) : ScenarioNotice

    data class Soon(@StringRes val title: Int, @StringRes val detail: Int?) : ScenarioNotice

    data class Message(val text: String, val failed: Boolean = false) : ScenarioNotice
}

// The flash text is fixed once it is up, so a saved scenario's status line waits for the first real
// system state: read against a placeholder, it would report the scenario as not holding. Nothing else
// reads the state, so nothing else waits for it.
@Composable
internal fun NoticeFlash(
    notice: ScenarioNotice?,
    state: SystemState?,
    flash: FlashState,
    onUndo: (ScenarioNotice) -> Unit,
    onGone: (ScenarioNotice) -> Unit,
) {
    val text = when {
        notice == null -> null
        notice is ScenarioNotice.Saved && state == null -> null
        else -> noticeText(notice, state)
    }
    val undo = stringResource(R.string.scenarios_undo)
    LaunchedEffect(notice, text != null) {
        if (notice == null || text == null) return@LaunchedEffect
        val shown = Flash(
            message = text,
            error = (notice as? ScenarioNotice.Message)?.failed == true,
            action = undo.takeIf { notice.isUndoable },
        )
        flash.show(shown) { acted ->
            if (acted) onUndo(notice)
            onGone(notice)
        }
    }
}

@Composable
private fun noticeText(notice: ScenarioNotice, state: SystemState?): String {
    val title = noticeTitle(notice)
    return noticeDetail(notice, state)?.let { "$title\n$it" } ?: title
}

@Composable
private fun noticeTitle(notice: ScenarioNotice): String = when (notice) {
    is ScenarioNotice.Saved -> stringResource(R.string.scenarios_saved, notice.scenario.name)
    is ScenarioNotice.Deleted -> stringResource(R.string.scenarios_deleted, notice.scenario.name)
    is ScenarioNotice.Soon -> stringResource(notice.title)
    is ScenarioNotice.Message -> notice.text
}

@Composable
private fun noticeDetail(notice: ScenarioNotice, state: SystemState?): String? = when (notice) {
    is ScenarioNotice.Saved -> state?.let { scenarioStatusText(scenarioStatusOf(notice.scenario, it)) }
    is ScenarioNotice.Soon -> notice.detail?.let { stringResource(it) }
    else -> null
}
