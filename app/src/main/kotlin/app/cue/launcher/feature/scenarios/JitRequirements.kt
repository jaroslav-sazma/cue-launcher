package app.cue.launcher.feature.scenarios

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.engine.usesBluetooth
import app.cue.launcher.engine.usesCalendar
import app.cue.launcher.engine.usesDndAccess
import app.cue.launcher.engine.usesNotificationListener
import app.cue.launcher.engine.usesPlace
import app.cue.launcher.engine.usesWifiSsid
import app.cue.launcher.system.ACCESS_LOCAL_NETWORK_PERMISSION
import app.cue.launcher.system.BLUETOOTH_CONNECT_PERMISSION
import app.cue.launcher.system.LOCATION_PERMISSIONS
import app.cue.launcher.system.READ_CALENDAR_PERMISSION
import app.cue.launcher.system.READ_CONTACTS_PERMISSION
import app.cue.launcher.system.approximateLocationGranted
import app.cue.launcher.system.bluetoothConnectGranted
import app.cue.launcher.system.calendarGranted
import app.cue.launcher.system.fineLocationGranted
import app.cue.launcher.system.locationServicesEnabled
import app.cue.launcher.system.notificationPolicyAccessGranted
import app.cue.launcher.system.notifications.notificationListenerEnabled
import app.cue.launcher.system.notifications.notificationListenerSettingsIntent

internal enum class JitPermission(
    val requested: List<String>,
    @param:StringRes val rationale: Int,
    @param:StringRes val denied: Int,
    @param:StringRes val label: Int,
) {
    BLUETOOTH(
        requested = listOf(BLUETOOTH_CONNECT_PERMISSION),
        rationale = R.string.permission_bluetooth_rationale,
        denied = R.string.permission_bluetooth_denied,
        label = R.string.permission_bluetooth_label,
    ),
    LOCATION(
        requested = LOCATION_PERMISSIONS,
        rationale = R.string.permission_location_rationale,
        denied = R.string.permission_location_denied,
        label = R.string.permission_location_label,
    ),
    // The same permission as LOCATION, asked for a different reason: the place a tap remembers.
    // Approximate is enough to go on, so it is the one whose refusal counts as a denial.
    PLACE(
        requested = LOCATION_PERMISSIONS.reversed(),
        rationale = R.string.permission_place_rationale,
        denied = R.string.permission_place_denied,
        label = R.string.permission_location_label,
    ),
    CALENDAR(
        requested = listOf(READ_CALENDAR_PERMISSION),
        rationale = R.string.permission_calendar_rationale,
        denied = R.string.permission_calendar_denied,
        label = R.string.permission_calendar_label,
    ),

    CONTACTS(
        requested = listOf(READ_CONTACTS_PERMISSION),
        rationale = R.string.permission_contacts_rationale,
        denied = R.string.permission_contacts_denied,
        label = R.string.permission_contacts_label,
    ),

    // Only for a Home Assistant that lives on the home network. An instance picked in the system
    // picker needs nothing, and a Nabu Casa address is not local at all.
    LOCAL_NETWORK(
        requested = listOf(ACCESS_LOCAL_NETWORK_PERMISSION),
        rationale = R.string.permission_local_network_rationale,
        denied = R.string.permission_local_network_denied,
        label = R.string.permission_local_network_label,
    ),
    ;

    val decisive: String get() = requested.first()
}

internal sealed interface JitRequirement {

    @get:StringRes val rationale: Int

    @get:StringRes val action: Int

    @get:StringRes val label: Int

    data class Grant(val permission: JitPermission) : JitRequirement {
        override val rationale: Int get() = permission.rationale
        override val action: Int get() = R.string.permission_grant
        override val label: Int get() = permission.label
    }

    data object LocationServices : JitRequirement {
        override val rationale: Int get() = R.string.permission_location_services_rationale
        override val action: Int get() = R.string.permission_location_services_open
        override val label: Int get() = R.string.permission_location_services_label
    }

    data object NotificationPolicy : JitRequirement {
        override val rationale: Int get() = R.string.permission_dnd_rationale
        override val action: Int get() = R.string.permission_dnd_open
        override val label: Int get() = R.string.permission_dnd_label
    }

    data object NotificationListener : JitRequirement {
        override val rationale: Int get() = R.string.permission_notifications_rationale
        override val action: Int get() = R.string.permission_notifications_open
        override val label: Int get() = R.string.permission_notifications_label
    }
}

// Special access has no request dialog: the only way to grant it is the settings screen behind
// these intents, so every place that reports such a requirement has to be able to open one.
internal fun JitRequirement.settingsIntent(context: Context): Intent? = when (this) {
    is JitRequirement.Grant -> null
    JitRequirement.LocationServices -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    JitRequirement.NotificationPolicy -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    JitRequirement.NotificationListener -> notificationListenerSettingsIntent(context)
}

internal data class SystemAccess(
    val bluetoothGranted: Boolean = false,
    val locationGranted: Boolean = false,
    val approximateLocationGranted: Boolean = false,
    val locationEnabled: Boolean = false,
    val dndAccessGranted: Boolean = false,
    val calendarGranted: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
)

internal fun jitRequirements(scenarios: List<Scenario>, access: SystemAccess): List<JitRequirement> = buildList {
    if (usesBluetooth(scenarios) && !access.bluetoothGranted) {
        add(JitRequirement.Grant(JitPermission.BLUETOOTH))
    }
    if (usesWifiSsid(scenarios)) {
        if (!access.locationGranted) add(JitRequirement.Grant(JitPermission.LOCATION))
        if (!access.locationEnabled) add(JitRequirement.LocationServices)
    } else if (usesPlace(scenarios) && !access.approximateLocationGranted) {
        add(JitRequirement.Grant(JitPermission.PLACE))
    }
    if (usesCalendar(scenarios) && !access.calendarGranted) {
        add(JitRequirement.Grant(JitPermission.CALENDAR))
    }
    if (usesNotificationListener(scenarios) && !access.notificationListenerEnabled) {
        add(JitRequirement.NotificationListener)
    }
    if (usesDndAccess(scenarios) && !access.dndAccessGranted) {
        add(JitRequirement.NotificationPolicy)
    }
}

internal fun missingRequirements(context: Context, scenarios: List<Scenario>): List<JitRequirement> =
    jitRequirements(
        scenarios = scenarios,
        access = SystemAccess(
            bluetoothGranted = bluetoothConnectGranted(context),
            locationGranted = fineLocationGranted(context),
            approximateLocationGranted = approximateLocationGranted(context),
            locationEnabled = locationServicesEnabled(context),
            dndAccessGranted = notificationPolicyAccessGranted(context),
            calendarGranted = calendarGranted(context),
            notificationListenerEnabled = notificationListenerEnabled(context),
        ),
    )

@Composable
internal fun RequirementRequest(
    requirement: JitRequirement,
    onResolve: () -> Unit,
    modifier: Modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        Text(text = stringResource(requirement.rationale), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onResolve) {
            Text(text = stringResource(requirement.action))
        }
    }
}

@Composable
internal fun RequirementPrompt(requirement: JitRequirement, onResolve: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(requirement.label)) },
        text = { Text(text = stringResource(requirement.rationale)) },
        confirmButton = {
            TextButton(onClick = onResolve) { Text(text = stringResource(requirement.action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.permission_not_now)) }
        },
    )
}
