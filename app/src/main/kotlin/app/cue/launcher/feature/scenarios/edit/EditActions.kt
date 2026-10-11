package app.cue.launcher.feature.scenarios.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioRepository
import app.cue.launcher.designsystem.Flash
import app.cue.launcher.designsystem.FlashState
import app.cue.launcher.feature.scenarios.ScenarioNotice
import app.cue.launcher.feature.scenarios.rememberScenarioActions

@Stable
internal class EditActions(
    val duplicate: () -> Unit,
    val export: () -> Unit,
    val delete: () -> Unit,
)

@Composable
internal fun rememberEditActions(
    scenario: Scenario,
    scenarios: ScenarioRepository,
    onNotice: (ScenarioNotice) -> Unit,
    flash: FlashState,
    onClose: () -> Unit,
): EditActions {
    val actions = rememberScenarioActions(repository = scenarios, onEdit = {}) { notice ->
        when (notice) {
            is ScenarioNotice.Message -> flash.show(Flash(notice.text, error = notice.failed))
            else -> {
                onNotice(notice)
                onClose()
            }
        }
    }

    val current = rememberUpdatedState(scenario)
    return remember(actions) {
        EditActions(
            duplicate = { actions.duplicate(current.value) },
            export = { actions.export(current.value) },
            delete = { actions.delete(current.value) },
        )
    }
}
