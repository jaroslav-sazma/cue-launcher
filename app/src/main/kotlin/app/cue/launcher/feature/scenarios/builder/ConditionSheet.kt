package app.cue.launcher.feature.scenarios.builder

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.engine.SystemState
import app.cue.launcher.feature.scenarios.LiveVerdict
import app.cue.launcher.feature.scenarios.RequirementRequest
import app.cue.launcher.feature.scenarios.liveReading
import app.cue.launcher.designsystem.SheetFrame
import app.cue.launcher.designsystem.SheetHeader
import app.cue.launcher.designsystem.SheetConfirmButton
import app.cue.launcher.designsystem.Message
import java.text.DecimalFormatSymbols

@Composable
internal fun ConditionSheetContent(
    initial: Condition,
    state: SystemState,
    permissions: ConditionPermissions,
    @StringRes confirmLabel: Int,
    onConfirm: (Condition) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes denied: Int? = null,
    onBack: (() -> Unit)? = null,
    onPickApp: ((Condition, AppTarget) -> Unit)? = null,
    diagnosis: Boolean = false,
    ha: HaAccess = HaAccess.NOT_CONFIGURED,
) {
    var condition by remember(initial) { mutableStateOf(initial) }
    val haRead = rememberHaEntityRead(ha, condition)
    val shown = remember(state, haRead) { state.withHaRead(haRead) }
    val separator = DecimalFormatSymbols.getInstance(LocalConfiguration.current.locales[0]).decimalSeparator
    var ending by remember(initial) {
        mutableStateOf((initial as? Condition.InstantCondition)?.let { endingDraftOf(it, separator) })
    }
    // The app picker reopens the sheet from what it is handed, so it gets the ending as edited too.
    val ended = ending?.let { withEnding(condition, it) } ?: condition

    val error = conditionFormError(condition) ?: ending?.let(::endingError)

    SheetFrame(
        header = {
            SheetHeader(
                title = conditionTypeLabel(conditionTypeOf(condition)),
                onClose = onClose,
                onBack = onBack,
            )
        },
        modifier = modifier,
        gap = Tokens.Builder.sheetGap,
        footer = {
            ConditionSheetFooter(
                error = error,
                confirmLabel = confirmLabel,
                onConfirm = { onConfirm(canonicalCondition(ended)) },
            )
        },
    ) {
        ConditionFormBody(
            condition = condition,
            state = shown,
            permissionTick = permissions.tick,

            onPickApp = onPickApp?.let { pick -> { pick(ended, AppTarget.CONDITION) } },
            onChange = { condition = it },
            onResolve = permissions.resolve,
            ha = ha,
            haRead = haRead,
        )
        ending?.let { current ->
            DurationSection(
                condition = condition,
                ending = current,
                permissionTick = permissions.tick,
                onPickEndApp = onPickApp?.let { pick -> { pick(ended, AppTarget.END_EVENT) } },
                onChange = { ending = it },
            )
        }

        MissingRequirement(condition = ended, permissions = permissions)
        denied?.let { Message(text = stringResource(it)) }
        LiveStateLine(condition = condition, state = shown, diagnosis = diagnosis)

        if (diagnosis) {
            PermissionStatusLines(condition = ended, tick = permissions.tick)
        }
    }
}

@Composable
private fun MissingRequirement(condition: Condition, permissions: ConditionPermissions) {
    val requirement = conditionRequirementsNow(condition, permissions.tick).firstOrNull() ?: return
    RequirementRequest(requirement = requirement, onResolve = { permissions.resolve(requirement) })
}

@Composable
private fun ConditionSheetFooter(
    error: ConditionFormError?,
    @StringRes confirmLabel: Int,
    onConfirm: () -> Unit,
) {
    error?.let { Message(text = conditionFormErrorText(it), modifier = Modifier) }
    SheetConfirmButton(
        label = stringResource(confirmLabel),
        onClick = if (error == null) onConfirm else null,
    )
}

@Composable
private fun LiveStateLine(
    condition: Condition,
    state: SystemState,
    modifier: Modifier = Modifier,
    diagnosis: Boolean = false,
) {
    val reading = remember(condition, state) { liveReading(condition, state) }
    val scheme = MaterialTheme.colorScheme
    val blocking = diagnosis && reading.verdict == LiveVerdict.FAILS
    Text(

        text = if (blocking) blockingReasonText(reading) else liveReadingText(reading),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.screenPadding),
        style = MaterialTheme.typography.bodyMedium,
        color = when (reading.verdict) {
            LiveVerdict.HOLDS -> scheme.primary
            LiveVerdict.FAILS -> scheme.error
            else -> scheme.onSurfaceVariant
        },
    )
}

@Composable
private fun PermissionStatusLines(condition: Condition, tick: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        conditionPermissionStatusNow(condition, tick).forEach { status ->
            Text(
                text = stringResource(
                    if (status.granted) R.string.permission_status_granted else R.string.permission_status_missing,
                    stringResource(status.requirement.label),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (status.granted) scheme.onSurfaceVariant else scheme.error,
            )
        }
    }
}
