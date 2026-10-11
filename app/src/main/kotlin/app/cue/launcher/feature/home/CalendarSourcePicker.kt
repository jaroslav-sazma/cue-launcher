package app.cue.launcher.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.calendar.CalendarSource
import app.cue.launcher.data.calendar.prunedCalendarSelection
import app.cue.launcher.data.calendar.selectedCalendarIds
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.feature.scenarios.JitPermission
import app.cue.launcher.feature.scenarios.JitRequirement
import kotlinx.coroutines.launch

// null while the read is still running, so a caller can tell "not known yet" from "none".
@Composable
internal fun rememberCalendarSources(granted: Boolean): List<CalendarSource>? {
    val sources = LocalCalendarSources.current
    var calendars by remember(sources) { mutableStateOf<List<CalendarSource>?>(null) }
    LaunchedEffect(sources, granted) { calendars = if (granted) sources.read() else null }
    return calendars
}

@Composable
internal fun CalendarSourcePicker(access: WidgetSettingsAccess) {
    val gate = rememberCalendarGate()

    val shown = rememberCalendarSources(gate.granted)
    val stored by access.settings.settings.collectAsStateWithLifecycle(initialValue = LauncherSettings())
    val storedIds = stored.calendarWidget.calendars

    if (!gate.granted) {
        CalendarAccessRow(onResolve = { gate.request() })
        return
    }
    when {
        shown == null -> Unit
        shown.isEmpty() -> CalendarPickerNotice(stringResource(R.string.widget_settings_calendars_empty))
        else -> CalendarSourceList(calendars = shown, stored = storedIds, access = access)
    }
    if (storedIds != null) {
        val scope = rememberCoroutineScope()
        WidgetSettingRow(
            label = stringResource(R.string.widget_settings_calendars_clear),
            detail = null,
            glyph = GLYPH_RESET,
            trailingGlyph = null,
            onClick = { scope.launch { access.settings.setCalendarWidgetCalendars(null) } },
        )
    }
}

// A calendar that was removed keeps its id in the stored choice; drop it once the list is known.
// Losing every picked calendar goes back to the default rather than leaving the widget empty.
@Composable
internal fun ForgetGoneCalendars(
    calendars: List<CalendarSource>?,
    stored: Set<Long>?,
    access: WidgetSettingsAccess,
) {
    LaunchedEffect(calendars, stored) {
        val kept = prunedCalendarSelection(calendars, stored) ?: return@LaunchedEffect
        access.settings.setCalendarWidgetCalendars(kept.ifEmpty { null })
    }
}

@Composable
private fun CalendarAccessRow(onResolve: (JitRequirement) -> Unit) {
    val requirement = JitRequirement.Grant(JitPermission.CALENDAR)
    WidgetSettingRow(
        label = stringResource(R.string.permission_grant),
        detail = stringResource(requirement.rationale),
        glyph = GLYPH_CALENDAR,
        trailingGlyph = null,
        onClick = { onResolve(requirement) },
    )
}

@Composable
private fun CalendarSourceList(
    calendars: List<CalendarSource>,
    stored: Set<Long>?,
    access: WidgetSettingsAccess,
) {
    val scope = rememberCoroutineScope()
    val selected = remember(calendars, stored) { selectedCalendarIds(calendars, stored) }
    val visibleDefault = remember(calendars) { selectedCalendarIds(calendars, null) }

    Column(
        modifier = Modifier
            .heightIn(max = CALENDAR_LIST_MAX_HEIGHT)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Tokens.WidgetSettings.rowGap),
    ) {
        calendars.forEach { calendar ->
            CalendarSourceRow(
                calendar = calendar,
                selected = calendar.id in selected,
                onClick = {
                    scope.launch {
                        access.settings.toggleCalendarWidgetCalendar(calendar.id, visibleDefault)
                    }
                },
            )
        }
    }
}

@Composable
private fun CalendarSourceRow(
    calendar: CalendarSource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    WidgetSettingRow(
        label = calendar.name,
        detail = calendar.account,
        glyph = if (selected) GLYPH_SELECTED else GLYPH_UNSELECTED,
        glyphTint = calendarDotColor(calendar.color, LocalCueColors.current.textSecondary),
        trailingGlyph = null,
        onClick = onClick,
    )
}

@Composable
private fun CalendarPickerNotice(text: String) {
    val colors = LocalCueColors.current
    Text(
        text = text,
        color = colors.textSecondary,
        style = Tokens.Type.tileBody.toTextStyle(),
    )
}

private val CALENDAR_LIST_MAX_HEIGHT = Tokens.Grid.RowHeight.static * 4

private const val GLYPH_CALENDAR = "calendar-month-outline"
private const val GLYPH_SELECTED = "checkbox-marked-circle"
private const val GLYPH_UNSELECTED = "checkbox-blank-circle-outline"
private const val GLYPH_RESET = "restore"
