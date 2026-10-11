package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.widgets.WidgetPlacement
import app.cue.launcher.designsystem.ModalSheet
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.rememberExpandedSheetState
import app.cue.launcher.engine.SystemState
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.feature.widgets.WidgetPickerContent
import app.cue.launcher.feature.widgets.rememberBentoSlotPx
import app.cue.launcher.system.AppActionsRepository
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.widget.AvailableWidget
import app.cue.launcher.system.widget.widgetFitsSlot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BuilderSheetHost(
    screen: BuilderState,
    state: SystemState,
    permissions: ConditionPermissions,
    launcherApps: LauncherAppsRepository,
    appActions: AppActionsRepository,
    widgets: WidgetHosting,
    ha: HaAccess,
) {
    val sheet = screen.sheet ?: return

    val sheetState = rememberExpandedSheetState()

    val content = Modifier.imePadding()
    ModalSheet(onDismissRequest = { screen.show(null) }, sheetState = sheetState, opening = screen.opening) {
        when (sheet) {
            is BuilderSheet.Catalog -> CatalogSheet(screen, sheet, content)
            is BuilderSheet.Settings -> SettingsSheet(screen, state, permissions, ha, sheet, content)
            is BuilderSheet.WidgetPicker -> WidgetPickerStep(screen, widgets)
            is BuilderSheet.WidgetBind -> WidgetBindStep(screen, widgets, sheet.widget, content)
            is BuilderSheet.ActionPicker -> ActionPickerStep(screen, sheet.slot, content)
            is BuilderSheet.AppPicker -> AppPickerStep(screen, launcherApps, sheet.request, content)
            is BuilderSheet.AppActions -> AppActionsStep(screen, appActions, sheet, content)
            is BuilderSheet.SystemAction -> SystemActionStep(screen, permissions, sheet.slot, content)
            is BuilderSheet.HaAction -> HaActionStep(screen, ha, sheet.slot, content)
            is BuilderSheet.UriAction -> UriActionStep(screen, sheet.slot, content)
            is BuilderSheet.IconPicker -> IconPickerStep(screen, sheet.item, content)
        }
    }
}

@Composable
private fun CatalogSheet(screen: BuilderState, sheet: BuilderSheet.Catalog, modifier: Modifier) {
    ConditionCatalog(
        draft = screen.draft,
        replacing = sheet.replacing,
        onReplacingChange = { screen.show(sheet.copy(replacing = it)) },
        onPick = { type -> screen.show(BuilderSheet.Settings(newCondition(type), index = null)) },
        onClose = { screen.show(null) },
        modifier = modifier,
    )
}

@Composable
private fun SettingsSheet(
    screen: BuilderState,
    state: SystemState,
    permissions: ConditionPermissions,
    ha: HaAccess,
    sheet: BuilderSheet.Settings,
    modifier: Modifier,
) {
    val isNew = sheet.index == null
    ConditionSheetContent(
        initial = sheet.condition,
        state = state,
        permissions = permissions,
        ha = ha,
        denied = screen.denied,
        modifier = modifier,
        diagnosis = sheet.diagnosis,
        confirmLabel = if (isNew) R.string.builder_condition_confirm else R.string.builder_condition_save,
        onConfirm = { condition ->
            screen.draft = if (sheet.index == null) {
                screen.draft.withCondition(condition)
            } else {
                screen.draft.withConditionAt(sheet.index, condition)
            }
            screen.show(null)
        },
        onClose = { screen.show(null) },

        onBack = if (isNew) {
            { screen.show(BuilderSheet.Catalog(replacing = false)) }
        } else {
            null
        },

        onPickApp = { condition ->
            if (condition is Condition.NotificationFrom || condition is Condition.NotificationEnded) {
                screen.show(BuilderSheet.AppPicker(AppPickRequest.ForCondition(condition, sheet.index)))
            }
        },
    )
}

@Composable
private fun WidgetPickerStep(screen: BuilderState, widgets: WidgetHosting) {
    val size = screen.draft.size ?: return
    val slot = rememberBentoSlotPx(size)

    val fit = remember(slot) {
        { widget: AvailableWidget -> widgetFitsSlot(widget.size, slot.width, slot.height) }
    }
    WidgetPickerContent(
        widgets = widgets,
        onPick = { widget -> screen.show(BuilderSheet.WidgetBind(widget)) },
        modifier = Modifier.imePadding(),
        onClose = { screen.show(null) },
        title = stringResource(R.string.builder_widget_sheet_title, tileSizeBadge(size)),
        note = {
            Note(
                text = stringResource(R.string.builder_widget_sheet_note, tileSizeBadge(size)),
                modifier = Modifier.padding(bottom = Tokens.WidgetPicker.rowPadding),
            )
        },
        fit = fit,
    )
}

@Composable
private fun WidgetBindStep(
    screen: BuilderState,
    widgets: WidgetHosting,
    widget: AvailableWidget,
    modifier: Modifier,
) {
    WidgetBindSheetContent(
        widget = widget,
        onContinue = {
            screen.show(null)
            widgets.add(widget, WidgetPlacement.SCENARIO)
        },
        onPickAnother = { screen.show(BuilderSheet.WidgetPicker) },
        onClose = { screen.show(null) },
        modifier = modifier,
    )
}
