package app.cue.launcher.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.feature.scenarios.builder.ActionPickerSheet
import app.cue.launcher.feature.scenarios.builder.HaAccess
import app.cue.launcher.feature.scenarios.builder.actionSummary
import app.cue.launcher.system.AppActionsRepository
import app.cue.launcher.system.LauncherAppsRepository
import kotlinx.coroutines.launch

@Composable
internal fun GestureSettingsSection(
    settings: SettingsRepository,
    stored: LauncherSettings,
    launcherApps: LauncherAppsRepository,
    appActions: AppActionsRepository,
    ha: HaAccess,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var picking by rememberSaveable { mutableStateOf(false) }
    val swipeLeft = stored.gestures.swipeLeft

    SettingsSection(title = stringResource(R.string.settings_gestures_section), modifier = modifier) {
        ActionRow(
            title = stringResource(R.string.settings_gesture_swipe_left),
            action = swipeLeft,

            empty = stringResource(R.string.settings_gesture_none),
            onPick = { picking = true },
            onClear = { scope.launch { settings.setSwipeLeftAction(null) } },
        )

        SettingsFixedRow(
            label = stringResource(R.string.settings_gesture_swipe_up),
            detail = stringResource(R.string.settings_gesture_fixed),
        )
    }

    if (picking) {
        ActionPickerSheet(
            launcherApps = launcherApps,
            appActions = appActions,
            initial = swipeLeft,
            onPick = { picked -> scope.launch { settings.setSwipeLeftAction(picked) } },
            onDismiss = { picking = false },
            ha = ha,
            bridgePaired = stored.bridge.enabled && stored.bridge.pairing != null,
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    action: TapAction?,
    empty: String,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {

    val detail = if (action == null) empty else actionSummary(action)
    if (action == null) {
        SettingsLinkRow(label = title, onClick = onPick, detail = detail)
    } else {
        SettingsActionRow(
            label = title,
            detail = detail,
            action = stringResource(R.string.settings_gesture_clear),
            onAction = onClear,
            onClick = onPick,
            chevron = true,
        )
    }
}
