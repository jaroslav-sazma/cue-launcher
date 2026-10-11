package app.cue.launcher.feature.home

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.layout.LauncherLayoutRepository
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.data.layout.StaticLayout
import app.cue.launcher.data.layout.firstFreeArea
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.widgets.WidgetPlacement
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.feature.widgets.WidgetPickerSheet
import app.cue.launcher.system.calendarGranted
import app.cue.launcher.system.widget.AvailableWidget
import app.cue.launcher.system.widget.WidgetBindOutcome
import kotlinx.coroutines.launch

@Stable
class HomeWidgetPicker(
    val fits: (AvailableWidget) -> Boolean,
    val launcherWidgetFits: (LauncherWidgetType) -> Boolean,
    val pickApp: (AvailableWidget) -> Unit,
    val pickLauncherWidget: (LauncherWidgetType) -> Unit,
)

@Composable
fun rememberHomeWidgetPicker(
    widgets: WidgetHosting,
    layoutRepository: LauncherLayoutRepository,
    layout: StaticLayout,
): HomeWidgetPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gate = rememberCalendarGate()
    val metrics = rememberStaticGridMetrics()

    LaunchedEffect(widgets, layoutRepository, metrics, context) {
        widgets.outcomes.collect { result ->
            if (result.placement != WidgetPlacement.HOME) return@collect
            when (result.outcome) {
                WidgetBindOutcome.ADDED -> {
                    val span = widgetCellSpan(result.widget.size, metrics)
                    val position = layoutRepository.layout()
                        .firstFreeArea(LayoutContainer.GRID, span.columns, span.rows)
                    if (position == null) {

                        widgets.unbind(result.appWidgetId)
                        Toast.makeText(context, R.string.home_widget_no_room, Toast.LENGTH_SHORT).show()
                    } else {
                        layoutRepository.placeAppWidget(
                            container = LayoutContainer.GRID,
                            position = position,
                            appWidgetId = result.appWidgetId,
                            columnSpan = span.columns,
                            rowSpan = span.rows,
                        )
                    }
                }

                WidgetBindOutcome.FAILED ->
                    Toast.makeText(context, R.string.widget_bind_failed, Toast.LENGTH_SHORT).show()

                WidgetBindOutcome.CANCELED -> Unit
            }
        }
    }

    return remember(widgets, layoutRepository, layout, metrics, context, scope, gate) {
        HomeWidgetPicker(
            fits = { widget ->
                val span = widgetCellSpan(widget.size, metrics)
                layout.firstFreeArea(LayoutContainer.GRID, span.columns, span.rows) != null
            },

            launcherWidgetFits = { widget -> freeAreaFor(layout, widget) != null },
            pickApp = { widget -> widgets.add(widget, WidgetPlacement.HOME) },
            pickLauncherWidget = { widget ->

                val position = freeAreaFor(layout, widget)
                if (position == null) {
                    Toast.makeText(context, R.string.home_widget_no_room, Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        layoutRepository.placeLauncherWidget(LayoutContainer.GRID, position, widget)
                    }

                    if (widget == LauncherWidgetType.CALENDAR && !calendarGranted(context)) {
                        gate.ask()
                    }
                }
            },
        )
    }
}

@Composable
fun HomeWidgetPickerSheet(
    widgets: WidgetHosting,
    picker: HomeWidgetPicker,
    onClose: () -> Unit,
) {
    WidgetPickerSheet(
        widgets = widgets,
        onPick = { widget ->
            onClose()
            picker.pickApp(widget)
        },
        onPickLauncherWidget = { widget ->
            onClose()
            picker.pickLauncherWidget(widget)
        },
        onDismiss = onClose,
        note = stringResource(R.string.home_widget_picker_note),
        fit = picker.fits,

        launcherWidgets = LauncherWidgetType.entries.filter(picker.launcherWidgetFits),
    )
}

private fun freeAreaFor(layout: StaticLayout, widget: LauncherWidgetType): Int? = layout.firstFreeArea(
    container = LayoutContainer.GRID,
    columnSpan = widget.staticColumns,
    rowSpan = widget.staticRows,
)
