package app.cue.launcher.feature.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.feature.scenarios.JitPermission
import app.cue.launcher.feature.scenarios.JitRequirement
import app.cue.launcher.feature.scenarios.builder.rememberRequirementResolver
import app.cue.launcher.system.calendarGranted
import app.cue.launcher.system.widget.AvailableWidget
import kotlinx.coroutines.launch

@Stable
class WidgetPick(
    val app: (AvailableWidget) -> Unit,
    val launcher: (LauncherWidgetType) -> Unit,
)

@Composable
fun rememberWidgetPick(widgets: WidgetHosting, onPicked: () -> Unit = {}): WidgetPick {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissions = rememberRequirementResolver { }
    return WidgetPick(
        app = { widget ->
            widgets.add(widget)
            onPicked()
        },
        launcher = { widget ->
            scope.launch { widgets.addLauncherWidget(widget) }
            if (widget == LauncherWidgetType.CALENDAR && !calendarGranted(context)) {
                permissions.ask(JitRequirement.Grant(JitPermission.CALENDAR))
            }
            onPicked()
        },
    )
}
