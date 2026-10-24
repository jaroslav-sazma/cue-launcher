package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.EndEventType
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.engine.haCodeKey
import java.text.DecimalFormatSymbols

@Composable
internal fun EndEventSection(
    condition: Condition,
    end: EndEvent,
    capSec: Int?,
    permissionTick: Int,
    onPickApp: (() -> Unit)?,
    onEnd: (EndEvent) -> Unit,
    onCap: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Switching kinds keeps what each was set to, as the moment form keeps its set time.
    val drafts = remember { mutableMapOf<EndEventType, EndEvent>() }
    val kind = endEventTypeOf(end)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.builder_until_section))
        END_EVENT_CATALOG.forEach { type ->
            RadioRow(
                title = endEventKindLabel(type),
                selected = type == kind,
                onSelect = {
                    if (type != kind) {
                        drafts[kind] = end
                        onEnd(drafts[type] ?: newEndEvent(type))
                    }
                },
            )
        }
        EndEventFields(end = end, permissionTick = permissionTick, onPickApp = onPickApp, onEnd = onEnd)
        if (endRepeatsTrigger(condition, end)) Note(text = stringResource(R.string.builder_until_repeats))
        EndCapSection(capSec = capSec, onCap = onCap)
    }
}

@Composable
private fun EndEventFields(
    end: EndEvent,
    permissionTick: Int,
    onPickApp: (() -> Unit)?,
    onEnd: (EndEvent) -> Unit,
) {
    when (end) {
        is EndEvent.Notification -> {
            WatchedAppRow(
                packageName = end.packageName,
                onPickApp = onPickApp,
                detail = R.string.builder_until_notification_package,
            )
            ContainsField(value = end.contains, onChange = { onEnd(end.copy(contains = it)) })
        }

        is EndEvent.AtTime -> {
            SetTimeFields(
                at = end.at,
                days = end.days,
                onChange = { at, days -> onEnd(end.copy(at = at, days = days)) },
            )
            Note(text = stringResource(R.string.builder_until_time_note))
        }

        EndEvent.Alarm -> NextAlarmNote()
        is EndEvent.BtDisconnected -> BluetoothForm(mac = end.mac, permissionTick = permissionTick) {
            onEnd(end.copy(mac = it))
        }

        is EndEvent.HaEvent -> HaCodeFields(code = end.code, onChange = { onEnd(end.copy(code = it)) })
    }
}

@Composable
private fun EndCapSection(capSec: Int?, onCap: (Int?) -> Unit) {
    val separator = DecimalFormatSymbols.getInstance(LocalConfiguration.current.locales[0]).decimalSeparator
    val current = capSec?.takeIf { it > 0 }
    SectionLabel(title = stringResource(R.string.builder_until_cap))
    ConnectedChoiceRow(
        options = endCapChoices(current).map { choice ->
            ConnectedChoice(
                label = capLabel(choice, separator),
                selected = choice == current,
                onSelect = { onCap(choice) },
            )
        },
    )
    Note(text = stringResource(R.string.builder_until_note))
}

@Composable
private fun capLabel(seconds: Int?, separator: Char): String = when {
    seconds == null -> stringResource(R.string.builder_until_cap_none)
    seconds % SECONDS_PER_HOUR == 0 -> stringResource(R.string.builder_until_cap_hours, seconds / SECONDS_PER_HOUR)
    else -> stringResource(R.string.builder_until_cap_minutes, customMinutesOf(seconds, separator))
}

@Composable
private fun endEventKindLabel(type: EndEventType): String = stringResource(
    when (type) {
        EndEventType.NOTIFICATION -> R.string.builder_until_notification
        EndEventType.TIME_INSTANT -> R.string.builder_until_time
        EndEventType.ALARM -> R.string.builder_until_alarm
        EndEventType.BT_DISCONNECTED -> R.string.builder_until_bt
        EndEventType.HA_EVENT -> R.string.builder_until_ha
    },
)

// Such an end never ends anything: every event it waits for also starts the tile again, so the
// trigger only has to cover the end, not equal it.
fun endRepeatsTrigger(condition: Condition, end: EndEvent): Boolean = when (end) {
    is EndEvent.Notification -> condition is Condition.NotificationFrom && covers(condition, end)
    is EndEvent.HaEvent ->
        condition is Condition.HaEvent && haCodeKey(end.code).let { it.isNotEmpty() && it == haCodeKey(condition.code) }

    is EndEvent.BtDisconnected -> condition is Condition.BtDisconnected &&
        (condition.mac == null || condition.mac.equals(end.mac, ignoreCase = true))

    is EndEvent.AtTime -> condition is Condition.TimeInstant && covers(condition, end)
    EndEvent.Alarm -> condition is Condition.Alarm
}

private fun covers(condition: Condition.NotificationFrom, end: EndEvent.Notification): Boolean {
    val needle = condition.contains?.trim().orEmpty()
    return condition.packageName.trim().equals(end.packageName.trim(), ignoreCase = true) &&
        (needle.isEmpty() || end.contains?.trim().orEmpty().contains(needle, ignoreCase = true))
}

private fun covers(condition: Condition.TimeInstant, end: EndEvent.AtTime): Boolean =
    parseTimeOfDay(condition.at) == parseTimeOfDay(end.at) && condition.days.containsAll(end.days)

// An imported cap off the presets stays on offer, so opening the form does not change it.
internal fun endCapChoices(current: Int?): List<Int?> =
    (END_CAP_CHOICES + listOfNotNull(current)).distinct().sortedBy { it ?: 0 }

private val END_CAP_CHOICES: List<Int?> = listOf(null, 3_600, 14_400, 86_400)

private const val SECONDS_PER_HOUR = 3_600
