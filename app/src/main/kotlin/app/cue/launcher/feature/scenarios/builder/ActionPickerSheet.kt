package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.designsystem.ModalSheet
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.rememberExpandedSheetState
import app.cue.launcher.system.AppActionsRepository
import app.cue.launcher.system.InstalledApp
import app.cue.launcher.system.LauncherAppsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPickerSheet(
    launcherApps: LauncherAppsRepository,
    appActions: AppActionsRepository,
    initial: TapAction?,
    onPick: (TapAction) -> Unit,
    onDismiss: () -> Unit,
    ha: HaAccess = HaAccess.NOT_CONFIGURED,
) {
    var level by remember { mutableStateOf<ActionPickerLevel>(ActionPickerLevel.Choice) }
    val permissions = rememberRequirementResolver { }
    val sheetState = rememberExpandedSheetState()
    val content = Modifier.imePadding()
    val confirm: (TapAction) -> Unit = { action ->
        onPick(action)
        onDismiss()
    }

    ModalSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ActionPickerContent(
            level = level,
            onLevel = { level = it },
            launcherApps = launcherApps,
            appActions = appActions,
            permissions = permissions,
            initial = initial,
            ha = ha,
            onConfirm = confirm,
            onDismiss = onDismiss,
            modifier = content,
        )
    }
}

@Composable
private fun ActionPickerContent(
    level: ActionPickerLevel,
    onLevel: (ActionPickerLevel) -> Unit,
    launcherApps: LauncherAppsRepository,
    appActions: AppActionsRepository,
    permissions: ConditionPermissions,
    initial: TapAction?,
    ha: HaAccess,
    onConfirm: (TapAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    when (val current = level) {
        ActionPickerLevel.Choice -> ActionChoiceSheetContent(
            onPick = { choice -> onLevel(levelFor(choice)) },
            onClose = onDismiss,
            modifier = modifier,
        )

        is ActionPickerLevel.Apps -> AppPickerSheetContent(
            launcherApps = launcherApps,
            withWorkApps = true,
            onPick = { app ->
                if (current.inApp) {
                    onLevel(ActionPickerLevel.AppActions(app))
                } else {
                    scope.launch { onConfirm(launcherApps.openAction(app)) }
                }
            },
            onClose = onDismiss,
            onBack = { onLevel(ActionPickerLevel.Choice) },
            modifier = modifier,
        )

        is ActionPickerLevel.AppActions -> AppActionSheetContent(
            app = current.app,
            appActions = appActions,
            onPick = { action, _ -> onConfirm(action) },
            onClose = onDismiss,
            onBack = { onLevel(ActionPickerLevel.Apps(inApp = true)) },
            modifier = modifier,
        )

        ActionPickerLevel.System -> SystemActionSheetContent(
            initial = (initial as? TapAction.SystemAction)?.action,
            permissions = permissions,
            onConfirm = { action -> onConfirm(TapAction.SystemAction(action)) },
            onClose = onDismiss,
            onBack = { onLevel(ActionPickerLevel.Choice) },
            modifier = modifier,
        )

        ActionPickerLevel.Ha -> HaActionSheetContent(
            initial = initial as? TapAction.HaAction,
            ha = ha,
            onConfirm = onConfirm,
            onClose = onDismiss,
            onBack = { onLevel(ActionPickerLevel.Choice) },
            modifier = modifier,
        )

        ActionPickerLevel.Uri -> UriActionSheetContent(
            initial = (initial as? TapAction.DeepLink)?.uri.orEmpty(),
            onConfirm = onConfirm,
            onClose = onDismiss,
            onBack = { onLevel(ActionPickerLevel.Choice) },
            modifier = modifier,
        )
    }
}

private sealed interface ActionPickerLevel {

    data object Choice : ActionPickerLevel

    data class Apps(val inApp: Boolean) : ActionPickerLevel

    data class AppActions(val app: InstalledApp) : ActionPickerLevel

    data object System : ActionPickerLevel

    data object Ha : ActionPickerLevel

    data object Uri : ActionPickerLevel
}

private fun levelFor(choice: ActionChoice): ActionPickerLevel = when (choice) {
    ActionChoice.OPEN_APP -> ActionPickerLevel.Apps(inApp = false)
    ActionChoice.IN_APP -> ActionPickerLevel.Apps(inApp = true)
    ActionChoice.SYSTEM -> ActionPickerLevel.System
    ActionChoice.HA -> ActionPickerLevel.Ha
    ActionChoice.URI -> ActionPickerLevel.Uri
}
