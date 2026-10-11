package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.scenarios.JitRequirement
import app.cue.launcher.feature.scenarios.RequirementPrompt
import app.cue.launcher.feature.scenarios.RequirementRequest
import app.cue.launcher.system.notificationPolicyAccessGranted
import app.cue.launcher.system.unavailableSystemActions
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.SheetFrame
import app.cue.launcher.designsystem.SheetHeader
import app.cue.launcher.designsystem.SheetConfirmButton

@Composable
internal fun SystemActionSheetContent(
    initial: SystemActionType?,
    permissions: ConditionPermissions,
    onConfirm: (SystemActionType) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val unavailable = remember(context) { unavailableSystemActions(context) }
    var selected by remember(initial) { mutableStateOf(initial?.takeUnless { it in unavailable }) }
    val dndGranted = remember(permissions.tick) { notificationPolicyAccessGranted(context) }
    val needsDnd = selected?.let(::systemActionNeedsDndAccess) == true && !dndGranted

    SheetFrame(
        header = {
            SheetHeader(
                title = stringResource(R.string.action_system_title),
                onClose = onClose,
                onBack = onBack,
                backDescription = R.string.action_sheet_back,
            )
        },
        modifier = modifier,
        footer = {
            SheetConfirmButton(
                label = stringResource(R.string.action_use),
                onClick = selected?.let { action -> { onConfirm(action) } },
            )
        },
    ) {
        SystemActionList(selected = selected, unavailable = unavailable, onPick = { selected = it })
        if (needsDnd) {
            RequirementRequest(
                requirement = JitRequirement.NotificationPolicy,
                onResolve = { permissions.resolve(JitRequirement.NotificationPolicy) },
            )
        }
    }
    DndAccessPrompt(missing = needsDnd, permissions = permissions)
}

@Composable
private fun SystemActionList(
    selected: SystemActionType?,
    unavailable: Set<SystemActionType>,
    onPick: (SystemActionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        systemActionSections(unavailable = unavailable).forEach { group ->
            SectionLabel(title = systemActionSectionTitle(group.section))
            group.actions.forEach { action ->
                TonalRow(onClick = { onPick(action) }, selected = action == selected) {
                    RadioButton(selected = action == selected, onClick = { onPick(action) })
                    RowText(
                        title = systemActionLabel(action),
                        modifier = Modifier.weight(1f),
                        detail = systemActionDetail(action),
                    )
                }
            }
        }
    }
}

// Picking the action is the moment the access matters; the notice under the list sits below a
// screenful of rows and is easy to miss, so the pick asks outright. Refusing leaves the action
// selectable and the notice is then the only reminder, which is why it asks no second time.
@Composable
private fun DndAccessPrompt(missing: Boolean, permissions: ConditionPermissions) {
    var answered by remember { mutableStateOf(false) }
    if (!missing || answered) return
    RequirementPrompt(
        requirement = JitRequirement.NotificationPolicy,
        onResolve = {
            answered = true
            permissions.resolve(JitRequirement.NotificationPolicy)
        },
        onDismiss = { answered = true },
    )
}
