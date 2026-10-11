package app.cue.launcher.feature.scenarios.builder

import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.clock.ClockAlarmDay
import app.cue.launcher.data.clock.clockAlarmDay
import app.cue.launcher.data.clock.clockShortWeekdayText
import app.cue.launcher.data.clock.clockTimeText
import app.cue.launcher.data.scenario.ALL_WEEK_DAYS
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionType
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Note
import app.cue.launcher.system.rememberNextAlarmMillis
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
internal fun TimeWindowForm(
    condition: Condition.TimeWindow,
    onChange: (Condition.TimeWindow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowItemGap),
        ) {
            TimeField(
                value = condition.from,
                label = stringResource(R.string.builder_time_from),
                onChange = { onChange(condition.copy(from = it)) },
                modifier = Modifier.weight(1f),
            )
            TimeField(
                value = condition.to,
                label = stringResource(R.string.builder_time_to),
                onChange = { onChange(condition.copy(to = it)) },
                modifier = Modifier.weight(1f),
            )
        }

        if (isOvernight(condition.from, condition.to)) {
            Note(
                text = stringResource(R.string.builder_time_overnight, condition.from, condition.to),
            )
        }
        SectionLabel(title = stringResource(R.string.builder_time_days))
        DayChips(days = condition.days, onChange = { onChange(condition.copy(days = it)) })
    }
}

// The duration lives in the sheet, not in the condition, so switching modes keeps it as it is.
@Composable
internal fun MomentForm(
    condition: Condition,
    onChange: (Condition) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastSetTime by remember {
        mutableStateOf(
            condition as? Condition.TimeInstant ?: newCondition(ConditionType.TIME_INSTANT) as Condition.TimeInstant,
        )
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        ConnectedChoiceRow(
            options = listOf(
                ConnectedChoice(
                    label = stringResource(R.string.builder_moment_alarm),
                    selected = condition is Condition.Alarm,
                    onSelect = { onChange(newCondition(ConditionType.ALARM)) },
                ),
                ConnectedChoice(
                    label = stringResource(R.string.builder_moment_set_time),
                    selected = condition is Condition.TimeInstant,
                    onSelect = { onChange(lastSetTime) },
                ),
            ),
        )
        when (condition) {
            is Condition.TimeInstant -> TimeInstantForm(
                condition = condition,
                onChange = {
                    lastSetTime = it
                    onChange(it)
                },
            )

            else -> NextAlarmNote()
        }
    }
}

@Composable
private fun NextAlarmNote() {
    val millis = rememberNextAlarmMillis()
    Note(text = millis?.let { nextAlarmText(it) } ?: stringResource(R.string.builder_moment_no_alarm))
}

@Composable
private fun nextAlarmText(millis: Long): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val alarm = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    val time = clockTimeText(alarm.toLocalTime(), DateFormat.is24HourFormat(context), locale)
    return when (clockAlarmDay(LocalDate.now(zone), alarm.toLocalDate())) {
        ClockAlarmDay.TODAY -> stringResource(R.string.builder_moment_next_today, time)
        ClockAlarmDay.TOMORROW -> stringResource(R.string.builder_moment_next_tomorrow, time)
        ClockAlarmDay.LATER ->
            stringResource(R.string.builder_moment_next_day, clockShortWeekdayText(alarm.toLocalDate(), locale), time)
    }
}

@Composable
private fun TimeInstantForm(
    condition: Condition.TimeInstant,
    onChange: (Condition.TimeInstant) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        TimeField(
            value = condition.at,
            label = stringResource(R.string.builder_time_at),
            onChange = { onChange(condition.copy(at = it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
        )
        SectionLabel(title = stringResource(R.string.builder_time_days))
        DayChips(days = condition.days, onChange = { onChange(condition.copy(days = it)) })
    }
}

@Composable
private fun TimeField(
    value: String,
    label: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = { Text(text = label) },
        placeholder = { Text(text = stringResource(R.string.builder_time_placeholder)) },
        singleLine = true,
    )
}

@Composable
private fun DayChips(days: List<Int>, onChange: (List<Int>) -> Unit, modifier: Modifier = Modifier) {
    val names = stringArrayResource(R.array.days_short)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Settings.surfacePadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Scenarios.chipGap),
    ) {
        ALL_WEEK_DAYS.forEach { day ->
            val selected = day in days
            FilterChip(
                selected = selected,
                onClick = {
                    onChange(if (selected) days - day else (days + day).sorted())
                },
                label = { Text(text = names[day - 1]) },
            )
        }
    }
}

@Suppress("ReturnCount")
private fun isOvernight(from: String, to: String): Boolean {
    val start = parseTimeOfDay(from) ?: return false
    val end = parseTimeOfDay(to) ?: return false
    return start > end
}
