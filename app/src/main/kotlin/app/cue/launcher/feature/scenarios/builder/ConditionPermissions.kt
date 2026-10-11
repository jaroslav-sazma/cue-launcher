package app.cue.launcher.feature.scenarios.builder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.WifiMode
import app.cue.launcher.feature.scenarios.JitPermission
import app.cue.launcher.feature.scenarios.JitRequirement
import app.cue.launcher.feature.scenarios.RequirementPrompt
import app.cue.launcher.feature.scenarios.RequirementRequest
import app.cue.launcher.feature.scenarios.SystemAccess
import app.cue.launcher.feature.scenarios.settingsIntent
import app.cue.launcher.system.bluetoothConnectGranted
import app.cue.launcher.system.calendarGranted
import app.cue.launcher.system.fineLocationGranted
import app.cue.launcher.system.launchSystemScreen
import app.cue.launcher.system.locationServicesEnabled
import app.cue.launcher.system.notifications.notificationListenerEnabled

internal fun conditionRequirements(condition: Condition, access: SystemAccess): List<JitRequirement> = buildList {
    val usesBluetooth = condition is Condition.BtConnected || condition is Condition.BtDisconnected
    if (usesBluetooth && !access.bluetoothGranted) add(JitRequirement.Grant(JitPermission.BLUETOOTH))
    if (condition is Condition.Wifi && condition.mode == WifiMode.SSID) {
        if (!access.locationGranted) add(JitRequirement.Grant(JitPermission.LOCATION))
        if (!access.locationEnabled) add(JitRequirement.LocationServices)
    }
    if (condition is Condition.CalendarEvent && !access.calendarGranted) {
        add(JitRequirement.Grant(JitPermission.CALENDAR))
    }
    val usesNotificationAccess = condition is Condition.NotificationFrom ||
        condition is Condition.NotificationEnded ||
        condition is Condition.MediaPlaying
    if (usesNotificationAccess && !access.notificationListenerEnabled) {
        add(JitRequirement.NotificationListener)
    }
}

@Composable
internal fun conditionRequirementsNow(condition: Condition, tick: Int): List<JitRequirement> {
    val context = LocalContext.current
    return remember(condition, tick) { conditionRequirements(condition, systemAccessOf(context)) }
}

private fun systemAccessOf(context: Context): SystemAccess = SystemAccess(
    bluetoothGranted = bluetoothConnectGranted(context),
    locationGranted = fineLocationGranted(context),
    locationEnabled = locationServicesEnabled(context),
    calendarGranted = calendarGranted(context),
    notificationListenerEnabled = notificationListenerEnabled(context),
)

internal data class RequirementStatus(val requirement: JitRequirement, val granted: Boolean)

internal fun conditionPermissionStatus(condition: Condition, access: SystemAccess): List<RequirementStatus> {
    val missing = conditionRequirements(condition, access)

    return conditionRequirements(condition, SystemAccess())
        .map { requirement -> RequirementStatus(requirement, requirement !in missing) }
}

@Composable
internal fun conditionPermissionStatusNow(condition: Condition, tick: Int): List<RequirementStatus> {
    val context = LocalContext.current
    return remember(condition, tick) { conditionPermissionStatus(condition, systemAccessOf(context)) }
}

@Composable
internal fun rememberRequirementResolver(onDenied: (JitPermission) -> Unit): ConditionPermissions {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var tick by remember { mutableIntStateOf(0) }
    var asked by remember { mutableStateOf<JitPermission?>(null) }
    val systemSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { tick++ }
    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val requested = asked
        tick++
        if (requested != null && results[requested.decisive] != true) {
            // Android stops showing the dialog once a permission has been refused for good: the
            // request then returns straight away and every further attempt is a dead end. Falling
            // through to the app's own settings page keeps a mistaken refusal recoverable.
            if (activity?.shouldShowRequestPermissionRationale(requested.decisive) == false) {
                systemSettings.launchSystemScreen(appDetailsIntent(context.packageName), context)
            }
            onDenied(requested)
        }
    }

    val resolve: (JitRequirement) -> Unit = { requirement ->
        when (requirement) {
            is JitRequirement.Grant -> {
                asked = requirement.permission
                permissionRequest.launch(requirement.permission.requested.toTypedArray())
            }

            JitRequirement.LocationServices,
            JitRequirement.NotificationPolicy,
            JitRequirement.NotificationListener,
            -> requirement.settingsIntent(context)?.let { systemSettings.launchSystemScreen(it, context) }
        }
    }

    // Play wants the reason on screen before the system prompt. Where no rationale is visible yet,
    // `ask` raises it from here, so the dialog lives exactly as long as the launchers it ends in.
    var prompted by remember { mutableStateOf<JitRequirement?>(null) }
    prompted?.let { requirement ->
        RequirementPrompt(
            requirement = requirement,
            onResolve = {
                prompted = null
                resolve(requirement)
            },
            onDismiss = { prompted = null },
        )
    }

    return ConditionPermissions(tick = tick, resolve = resolve, ask = { prompted = it })
}

// `resolve` goes straight to the system and is only for a button whose rationale is already shown
// next to it; anything else uses `ask`.
internal class ConditionPermissions(
    val tick: Int,
    val resolve: (JitRequirement) -> Unit,
    val ask: (JitRequirement) -> Unit,
)

@Composable
internal fun LauncherWidgetAccessRequest(widget: LauncherWidgetType?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val permissions = rememberRequirementResolver { }
    val requirement = when (widget) {
        LauncherWidgetType.CALENDAR -> JitRequirement.Grant(JitPermission.CALENDAR)
        LauncherWidgetType.MEDIA -> JitRequirement.NotificationListener
        LauncherWidgetType.CLOCK, LauncherWidgetType.SEARCHBAR, null -> return
    }
    val granted = remember(permissions.tick, context, requirement) {
        if (requirement == JitRequirement.NotificationListener) {
            notificationListenerEnabled(context)
        } else {
            calendarGranted(context)
        }
    }
    if (granted) return
    RequirementRequest(
        requirement = requirement,
        onResolve = { permissions.resolve(requirement) },
        modifier = modifier,
    )
}

// A permission refused for good can only be changed here; the request dialog never appears again.
internal fun appDetailsIntent(packageName: String): Intent = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", packageName, null),
)
