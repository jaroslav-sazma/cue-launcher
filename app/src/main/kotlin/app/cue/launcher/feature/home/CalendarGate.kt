package app.cue.launcher.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.cue.launcher.feature.scenarios.JitPermission
import app.cue.launcher.feature.scenarios.JitRequirement
import app.cue.launcher.feature.scenarios.builder.rememberRequirementResolver
import app.cue.launcher.system.calendarGranted

// A calendar widget that has no access shows a notice instead of events, and until the grant
// arrives its tap asks for the permission rather than running its action — otherwise a refusal
// leaves a widget that can never recover, because the only other grant path is buried in the
// long-press menu. Recovering from a refusal for good is the shared resolver's job. `ask` shows the
// rationale first; `request` is only for a row that already shows it.
@Stable
class CalendarGate(val granted: Boolean, val ask: () -> Unit, val request: () -> Unit)

@Composable
fun rememberCalendarGate(): CalendarGate {
    val context = LocalContext.current
    val permissions = rememberRequirementResolver { }
    val granted = remember(permissions.tick, context) { calendarGranted(context) }
    val requirement = JitRequirement.Grant(JitPermission.CALENDAR)
    return remember(granted, permissions) {
        CalendarGate(
            granted = granted,
            ask = { permissions.ask(requirement) },
            request = { permissions.resolve(requirement) },
        )
    }
}
