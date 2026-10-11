package app.cue.launcher.feature.scenarios.builder

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.LauncherWidgetType

@Composable
internal fun launcherWidgetLabel(widget: LauncherWidgetType): String = stringResource(
    when (widget) {
        LauncherWidgetType.CALENDAR -> R.string.builder_kind_calendar
        LauncherWidgetType.CLOCK -> R.string.builder_kind_clock
        LauncherWidgetType.MEDIA -> R.string.builder_kind_media
        LauncherWidgetType.SEARCHBAR -> R.string.launcher_widget_searchbar
    },
)

@Composable
internal fun launcherWidgetDetail(widget: LauncherWidgetType): String? = when (widget) {
    LauncherWidgetType.CALENDAR -> stringResource(R.string.builder_kind_calendar_detail)
    LauncherWidgetType.CLOCK -> stringResource(R.string.builder_kind_clock_detail)
    LauncherWidgetType.MEDIA -> stringResource(R.string.builder_kind_media_detail)
    LauncherWidgetType.SEARCHBAR -> null
}

@Composable
internal fun launcherWidgetNote(widget: LauncherWidgetType): String? = when (widget) {
    LauncherWidgetType.CALENDAR -> stringResource(R.string.builder_calendar_note)
    LauncherWidgetType.CLOCK -> stringResource(R.string.builder_clock_note)
    LauncherWidgetType.MEDIA -> stringResource(R.string.builder_media_note)
    LauncherWidgetType.SEARCHBAR -> null
}
