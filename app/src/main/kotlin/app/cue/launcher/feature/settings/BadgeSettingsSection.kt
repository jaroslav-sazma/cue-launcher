package app.cue.launcher.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.scenarios.JitRequirement
import app.cue.launcher.feature.scenarios.RequirementRequest
import app.cue.launcher.feature.scenarios.builder.rememberRequirementResolver
import app.cue.launcher.system.notifications.notificationListenerEnabled
import kotlinx.coroutines.launch

@Composable
internal fun BadgeSettingsSection(
    settings: SettingsRepository,
    stored: LauncherSettings,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var denied by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    val permissions = rememberRequirementResolver { }
    val granted = remember(permissions.tick, context) { notificationListenerEnabled(context) }
    val requirement = JitRequirement.NotificationListener
    val enabled = stored.appearance.notificationBadges

    // Special access has no result to wait for: the settings screen just comes back, so whether it
    // was granted is read on return.
    LaunchedEffect(permissions.tick) {
        if (!asking) return@LaunchedEffect
        asking = false
        if (granted) settings.setNotificationBadges(true) else denied = true
    }

    val request = { viaPrompt: Boolean ->
        asking = true
        if (viaPrompt) permissions.ask(requirement) else permissions.resolve(requirement)
    }

    SettingsSection(title = stringResource(R.string.settings_badges_section), modifier = modifier) {
        SettingsSwitchRow(
            label = stringResource(R.string.settings_badges),
            detail = stringResource(R.string.settings_badges_hint),
            checked = enabled,
            onCheckedChange = { wanted ->
                denied = false
                when {
                    !wanted -> scope.launch { settings.setNotificationBadges(false) }
                    granted -> scope.launch { settings.setNotificationBadges(true) }
                    else -> request(true)
                }
            },
        )

        // Only the first switch-on can fail this way; with badges already on, the request below
        // keeps saying what is missing and the switch is not the one staying off.
        if (denied && !enabled) {
            Text(
                text = stringResource(R.string.settings_badges_denied),
                modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (enabled && !granted) {
            RequirementRequest(
                requirement = requirement,
                onResolve = { request(false) },
                modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
            )
        }
    }
}
