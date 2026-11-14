package app.cue.launcher.feature.scenarios.builder

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.actionApp
import app.cue.launcher.feature.scenarios.AppJoiner
import app.cue.launcher.feature.scenarios.actionTargetOf
import app.cue.launcher.feature.scenarios.appListText

@Composable
internal fun actionSummary(action: TapAction?, actionLabel: String? = null): String = when (action) {
    null -> stringResource(R.string.builder_action_empty)
    // An action read from a saved or imported scenario carries no label of its own.
    is TapAction.App -> stringResource(
        R.string.builder_action_app,
        inProfile(actionLabel ?: appListText(listOf(action.packageName), AppJoiner.OR), action.profile),
    )

    is TapAction.Shortcut -> stringResource(R.string.builder_action_shortcut, appTargetText(action, actionLabel))
    is TapAction.DeepLink -> stringResource(R.string.builder_action_deeplink, actionLabel ?: action.uri)
    is TapAction.Capability -> stringResource(R.string.builder_action_capability, appTargetText(action, actionLabel))
    is TapAction.ActivityLaunch ->
        stringResource(R.string.builder_action_activity, actionLabel ?: actionTargetOf(action).orEmpty())

    is TapAction.SystemAction ->
        stringResource(R.string.builder_action_system, systemActionLabel(action.action))

    is TapAction.HaAction -> stringResource(R.string.builder_action_ha, action.service)
}

@Composable
private fun appTargetText(action: TapAction, label: String?): String {
    val app = action.actionApp ?: return label.orEmpty()
    return if (label != null) {
        inProfile(label, app.profile)
    } else {
        stringResource(
            R.string.builder_summary_value,
            inProfile(appListText(listOf(app.packageName), AppJoiner.OR), app.profile),
            actionTargetOf(action).orEmpty(),
        )
    }
}
