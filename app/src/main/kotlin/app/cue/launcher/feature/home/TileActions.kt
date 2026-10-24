package app.cue.launcher.feature.home

import android.content.Context
import android.graphics.Rect
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.cue.launcher.R
import app.cue.launcher.data.scenario.GesturesConfig
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.NotificationReply
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.engine.ScenarioEngine
import app.cue.launcher.feature.scenarios.JitPermission
import app.cue.launcher.feature.scenarios.JitRequirement
import app.cue.launcher.feature.scenarios.builder.rememberRequirementResolver
import app.cue.launcher.system.ActionExecutor
import app.cue.launcher.system.ActionOutcome
import app.cue.launcher.system.notifications.notificationListenerEnabled
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Suppress("LongParameterList")
@Stable
class TileActions(
    val perform: (actions: List<TapAction>, sourceBounds: Rect?) -> Unit,
    val close: (scenarioId: String) -> Unit,

    val reply: (reply: NotificationReply, text: String) -> Unit = { _, _ -> },

    val openCalendar: (sourceBounds: Rect?) -> Unit = {},

    val openAlarms: (sourceBounds: Rect?) -> Unit = {},

    val openMedia: (sourceBounds: Rect?) -> Unit = {},

    val openSearch: () -> Unit = {},
)

@Composable
fun rememberTileActions(
    executor: ActionExecutor,
    engine: ScenarioEngine,

    gestures: GesturesConfig = GesturesConfig(),
): TileActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val calendar = rememberCalendarGate()
    val dnd = rememberDndRequest()
    val notificationAccess = rememberNotificationAccessRequest()
    val location = rememberLocationRequest()
    val media = LocalMediaFeed.current
    val placeTap = remember { PlaceTap() }
    LifecycleStartEffect(placeTap) { onStopOrDispose { placeTap.cancel() } }
    return remember(
        executor,
        engine,
        context,
        scope,
        gestures.searchbarTap,
        gestures.calendarTap,
        calendar,
        dnd,
        notificationAccess,
        location,
        media,
        placeTap,
    ) {
        val report = outcomeReport(context, dnd, location)
        TileActions(
            perform = { actions, sourceBounds ->
                val job = scope.launch { report(executor.perform(actions, sourceBounds)) }
                if (actions.any(::remembersPlace)) placeTap.replace(job)
            },
            close = { scenarioId -> scope.launch { engine.deactivate(scenarioId) } },

            reply = { reply, text ->
                scope.launch {
                    val outcome = executor.reply(reply, text)
                    val message = outcome.messageRes() ?: R.string.tile_reply_sent
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            // Without access the tap buys the permission instead of the action it normally carries,
            // configured or not: an empty calendar is worth less than the chance to fill it.
            openCalendar = { sourceBounds ->
                if (!calendar.granted) {
                    calendar.ask()
                } else {
                    scope.launch { report(executor.openCalendar(gestures.calendarTap, sourceBounds)) }
                }
            },
            openAlarms = { sourceBounds -> report(executor.openAlarms(sourceBounds)) },

            // Read at the tap rather than remembered: the access is granted in the system settings,
            // and a value remembered here would still say no when the user comes back.
            openMedia = { sourceBounds ->
                if (!notificationListenerEnabled(context)) {
                    notificationAccess()
                } else {
                    scope.launch { report(media.open(sourceBounds)) }
                }
            },

            openSearch = {
                scope.launch { report(executor.openSearch(gestures.searchbarTap)) }
            },
        )
    }
}

// The composition outlives onStop, so a tap still waiting for a location fix is cancelled when the
// home screen stops: the place is read only while the launcher is on screen. A second tap replaces
// the first rather than asking for a second fix.
@Stable
private class PlaceTap {
    private var job: Job? = null

    fun replace(next: Job) {
        job?.cancel()
        job = next
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}

private fun remembersPlace(action: TapAction): Boolean =
    action is TapAction.SystemAction && action.action == SystemActionType.REMEMBER_PLACE

// Special access has no request dialog, so a refused Do Not Disturb action would otherwise end in a
// message naming a settings screen the user has to find on their own.
private fun outcomeReport(
    context: Context,
    dnd: () -> Unit,
    location: (JitRequirement) -> Unit,
): (ActionOutcome) -> Unit = { outcome ->
    outcome.messageRes()?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    when (outcome) {
        ActionOutcome.PERMISSION_REQUIRED -> dnd()
        ActionOutcome.LOCATION_PERMISSION_REQUIRED -> location(JitRequirement.Grant(JitPermission.PLACE))
        ActionOutcome.LOCATION_OFF -> location(JitRequirement.LocationServices)
        else -> Unit
    }
}

@Composable
private fun rememberDndRequest(): () -> Unit {
    val permissions = rememberRequirementResolver { }
    return remember(permissions) { { permissions.ask(JitRequirement.NotificationPolicy) } }
}

@Composable
private fun rememberLocationRequest(): (JitRequirement) -> Unit {
    val context = LocalContext.current
    val permissions = rememberRequirementResolver { denied ->
        Toast.makeText(context, denied.denied, Toast.LENGTH_LONG).show()
    }
    return remember(permissions) {
        { requirement ->
            if (requirement is JitRequirement.Grant) permissions.ask(requirement) else permissions.resolve(requirement)
        }
    }
}

@Composable
private fun rememberNotificationAccessRequest(): () -> Unit {
    val permissions = rememberRequirementResolver { }
    return remember(permissions) { { permissions.ask(JitRequirement.NotificationListener) } }
}

fun TileActions.tileClick(
    widget: LauncherWidgetType?,
    tileActions: List<TapAction>,
    bounds: () -> Rect?,
): (() -> Unit)? = when {
    widget == LauncherWidgetType.CALENDAR -> ({ openCalendar(bounds()) })
    widget == LauncherWidgetType.CLOCK -> ({ openAlarms(bounds()) })
    widget == LauncherWidgetType.MEDIA -> ({ openMedia(bounds()) })
    tileActions.isEmpty() -> null
    else -> ({ perform(tileActions, bounds()) })
}

@StringRes
@Suppress("CyclomaticComplexMethod")
private fun ActionOutcome.messageRes(): Int? = when (this) {
    ActionOutcome.DONE -> null
    ActionOutcome.TARGET_MISSING -> R.string.action_target_missing
    ActionOutcome.FAILED -> R.string.action_failed
    ActionOutcome.PERMISSION_REQUIRED -> null
    ActionOutcome.HA_NOT_CONFIGURED -> R.string.action_ha_not_configured
    ActionOutcome.HA_UNREACHABLE -> R.string.action_ha_unreachable
    ActionOutcome.HA_REJECTED -> R.string.action_ha_rejected
    ActionOutcome.HA_TIMEOUT -> R.string.action_ha_timeout
    ActionOutcome.HA_LOCAL_NETWORK -> R.string.action_ha_local_network
    ActionOutcome.NOTIFICATION_GONE -> R.string.action_notification_gone
    ActionOutcome.CONFIRMATION_DISMISSED -> null
    ActionOutcome.CONFIRMATION_UNAVAILABLE -> R.string.action_confirm_unavailable
    ActionOutcome.LOCATION_PERMISSION_REQUIRED -> null
    ActionOutcome.LOCATION_OFF -> R.string.action_place_location_off
    ActionOutcome.PLACE_NOT_FOUND -> R.string.action_place_not_found
    ActionOutcome.PLACE_NOT_AWAITED -> R.string.action_place_not_awaited
    ActionOutcome.NO_PLACE -> R.string.action_place_none
    ActionOutcome.GOOGLE_TV_MISSING -> R.string.action_google_tv_missing
    ActionOutcome.WORK_PAUSED -> R.string.action_work_paused
}
