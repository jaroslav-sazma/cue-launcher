package app.cue.launcher.feature.scenarios.builder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.withUntil
import app.cue.launcher.system.AppActionsRepository
import app.cue.launcher.system.LauncherAppsRepository
import kotlinx.coroutines.launch

@Composable
internal fun ActionPickerStep(screen: BuilderState, slot: ActionSlot, modifier: Modifier) {
    ActionChoiceSheetContent(
        onPick = { choice -> screen.show(actionSheetFor(choice, slot)) },
        onClose = { screen.show(null) },
        modifier = modifier,
    )
}

private fun actionSheetFor(choice: ActionChoice, slot: ActionSlot): BuilderSheet = when (choice) {
    ActionChoice.OPEN_APP -> BuilderSheet.AppPicker(AppPickRequest.OpenApp(slot))
    ActionChoice.IN_APP -> BuilderSheet.AppPicker(AppPickRequest.InApp(slot))
    ActionChoice.SYSTEM -> BuilderSheet.SystemAction(slot)
    ActionChoice.HA -> BuilderSheet.HaAction(slot)
    ActionChoice.URI -> BuilderSheet.UriAction(slot)
}

@Composable
internal fun AppPickerStep(
    screen: BuilderState,
    launcherApps: LauncherAppsRepository,
    request: AppPickRequest,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    AppPickerSheetContent(
        launcherApps = launcherApps,
        withWorkApps = request !is AppPickRequest.ForCondition,
        onPick = { app ->
            when (request) {
                is AppPickRequest.OpenApp -> scope.launch {
                    screen.setAction(request.slot, launcherApps.openAction(app), app.label)
                    screen.show(null)
                }

                is AppPickRequest.InApp -> screen.show(BuilderSheet.AppActions(request.slot, app))
                is AppPickRequest.ForCondition -> screen.show(
                    BuilderSheet.Settings(
                        condition = withWatchedPackage(request.condition, app.component.packageName, request.target),
                        index = request.index,
                    ),
                )
            }
        },
        onClose = { screen.show(null) },
        onBack = { screen.show(appPickerBack(request)) },
        modifier = modifier,
    )
}

internal fun withWatchedPackage(
    condition: Condition.InstantCondition,
    packageName: String,
    target: AppTarget,
): Condition {
    val until = condition.until
    return when {
        target == AppTarget.END_EVENT && until is EndEvent.Notification ->
            condition.withUntil(until.copy(packageName = packageName))

        target == AppTarget.END_EVENT -> condition
        condition is Condition.NotificationFrom -> condition.copy(packageName = packageName)
        condition is Condition.NotificationEnded -> condition.copy(packageName = packageName)
        else -> condition
    }
}

private fun appPickerBack(request: AppPickRequest): BuilderSheet = when (request) {
    is AppPickRequest.OpenApp -> BuilderSheet.ActionPicker(request.slot)
    is AppPickRequest.InApp -> BuilderSheet.ActionPicker(request.slot)
    is AppPickRequest.ForCondition -> BuilderSheet.Settings(request.condition, request.index)
}

@Composable
internal fun AppActionsStep(
    screen: BuilderState,
    appActions: AppActionsRepository,
    sheet: BuilderSheet.AppActions,
    modifier: Modifier,
) {
    AppActionSheetContent(
        app = sheet.app,
        appActions = appActions,
        onPick = { action, label ->
            screen.setAction(sheet.slot, action, label)
            screen.show(null)
        },
        onClose = { screen.show(null) },
        onBack = { screen.show(BuilderSheet.AppPicker(AppPickRequest.InApp(sheet.slot))) },
        modifier = modifier,
    )
}

@Composable
internal fun SystemActionStep(
    screen: BuilderState,
    permissions: ConditionPermissions,
    slot: ActionSlot,
    modifier: Modifier,
) {
    SystemActionSheetContent(
        initial = (screen.actionAt(slot) as? TapAction.SystemAction)?.action,
        permissions = permissions,

        onConfirm = { action ->
            screen.setAction(slot, TapAction.SystemAction(action), actionLabel = null)
            screen.show(null)
        },
        onClose = { screen.show(null) },
        onBack = { screen.show(BuilderSheet.ActionPicker(slot)) },
        modifier = modifier,
    )
}

@Composable
internal fun HaActionStep(
    screen: BuilderState,
    ha: HaAccess,
    slot: ActionSlot,
    modifier: Modifier,
) {
    HaActionSheetContent(
        initial = screen.actionAt(slot) as? TapAction.HaAction,
        ha = ha,
        onConfirm = { action ->
            screen.setAction(slot, action, actionLabel = null)
            screen.show(null)
        },
        onClose = { screen.show(null) },
        onBack = { screen.show(BuilderSheet.ActionPicker(slot)) },
        modifier = modifier,
    )
}

@Composable
internal fun IconPickerStep(screen: BuilderState, item: Int, modifier: Modifier) {
    IconPickerSheetContent(
        item = screen.draft.content.items.getOrNull(item) ?: TemplateItemDraft(),
        onConfirm = { icon ->
            screen.setIcon(item, icon)
            screen.show(null)
        },
        onReset = {
            screen.setIcon(item, icon = null)
            screen.show(null)
        },
        onClose = { screen.show(null) },
        modifier = modifier,
    )
}

@Composable
internal fun UriActionStep(screen: BuilderState, slot: ActionSlot, modifier: Modifier) {
    UriActionSheetContent(
        initial = (screen.actionAt(slot) as? TapAction.DeepLink)?.uri.orEmpty(),
        onConfirm = { action ->
            screen.setAction(slot, action, actionLabel = null)
            screen.show(null)
        },
        onClose = { screen.show(null) },
        onBack = { screen.show(BuilderSheet.ActionPicker(slot)) },
        modifier = modifier,
    )
}
