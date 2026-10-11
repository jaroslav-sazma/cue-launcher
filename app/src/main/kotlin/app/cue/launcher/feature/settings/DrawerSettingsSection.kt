package app.cue.launcher.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.drawer.HiddenAppRepository
import app.cue.launcher.data.launchlog.LaunchLogRepository
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.scenarios.JitPermission
import app.cue.launcher.feature.scenarios.JitRequirement
import app.cue.launcher.feature.scenarios.RequirementRequest
import app.cue.launcher.feature.scenarios.builder.rememberRequirementResolver
import app.cue.launcher.system.contactsGranted
import kotlinx.coroutines.launch

@Composable
internal fun DrawerSettingsSection(
    settings: SettingsRepository,
    stored: LauncherSettings,
    hiddenApps: HiddenAppRepository,
    launchLog: LaunchLogRepository,
    onOpenHiddenApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val hidden by remember(hiddenApps) { hiddenApps.observeHidden() }
        .collectAsStateWithLifecycle(initialValue = emptySet())

    var historyCleared by remember { mutableStateOf(false) }

    SettingsSection(title = stringResource(R.string.settings_drawer_section), modifier = modifier) {
        ContactSearchRow(settings = settings, stored = stored)
        SettingsSwitchRow(
            label = stringResource(R.string.settings_drawer_ai),
            detail = stringResource(R.string.settings_drawer_ai_hint),
            checked = stored.drawer.aiRow,
            onCheckedChange = { wanted -> scope.launch { settings.setDrawerAiRow(wanted) } },
        )
        SettingsSwitchRow(
            label = stringResource(R.string.settings_drawer_web),
            detail = stringResource(R.string.settings_drawer_web_hint),
            checked = stored.drawer.webRow,
            onCheckedChange = { wanted -> scope.launch { settings.setDrawerWebRow(wanted) } },
        )
        SettingsLinkRow(
            label = stringResource(R.string.settings_drawer_hidden),
            onClick = onOpenHiddenApps,
            value = stringResource(R.string.settings_drawer_hidden_count, hidden.size),
        )

        SettingsActionRow(
            label = stringResource(R.string.settings_drawer_history),
            detail = stringResource(
                if (historyCleared) {
                    R.string.settings_drawer_history_cleared
                } else {
                    R.string.settings_drawer_history_hint
                },
            ),
            action = stringResource(R.string.settings_drawer_history_clear),
            onAction = {
                historyCleared = true
                scope.launch { launchLog.clear() }
            },
        )
    }
}

@Composable
private fun ContactSearchRow(
    settings: SettingsRepository,
    stored: LauncherSettings,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var denied by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    val permissions = rememberRequirementResolver { denied = true }
    val granted = remember(permissions.tick, context) { contactsGranted(context) }
    val requirement = JitRequirement.Grant(JitPermission.CONTACTS)
    val label = stringResource(R.string.settings_drawer_contacts)
    val enabled = stored.drawer.contacts

    LaunchedEffect(permissions.tick) {
        if (!asking) return@LaunchedEffect
        asking = false
        if (granted) settings.setDrawerContacts(true)
    }

    val request = { viaPrompt: Boolean ->
        asking = true
        if (viaPrompt) permissions.ask(requirement) else permissions.resolve(requirement)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        SettingsSwitchRow(
            label = label,
            detail = stringResource(R.string.settings_drawer_contacts_hint),
            checked = enabled,
            onCheckedChange = { wanted ->
                denied = false
                when {
                    !wanted -> scope.launch { settings.setDrawerContacts(false) }
                    granted -> scope.launch { settings.setDrawerContacts(true) }
                    else -> request(true)
                }
            },
        )

        if (denied) {
            Text(
                text = stringResource(JitPermission.CONTACTS.denied),
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
