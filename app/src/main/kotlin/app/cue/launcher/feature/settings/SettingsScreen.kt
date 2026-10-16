package app.cue.launcher.feature.settings

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.drawer.HiddenAppRepository
import app.cue.launcher.data.launchlog.LaunchLogRepository
import app.cue.launcher.data.scenario.ScenarioRepository
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.feature.scenarios.ScenarioBackup
import app.cue.launcher.feature.scenarios.builder.HaAccess
import app.cue.launcher.system.AppActionsRepository
import app.cue.launcher.system.ConfigBackup
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.iconpack.IconPackRepository
import app.cue.launcher.designsystem.TabPaneScaffold
import app.cue.launcher.designsystem.rememberFlashState

@Composable
fun BackupsScreen(
    scenarios: ScenarioRepository,
    settings: SettingsRepository,
    backup: ConfigBackup,
    modifier: Modifier = Modifier,
    importUri: Uri? = null,
    onImportConsumed: () -> Unit = {},
    onEditScenario: (id: String, notice: String) -> Unit = { _, _ -> },
    onPasteJson: () -> Unit = {},
) {
    val flash = rememberFlashState()
    TabPaneScaffold(title = stringResource(R.string.settings_tab_backups), modifier = modifier, flash = flash) {
        ScenarioBackup(
            repository = scenarios,
            settings = settings,
            backup = backup,
            flash = flash,
            importUri = importUri,
            onImportConsumed = onImportConsumed,
            onEditScenario = onEditScenario,
            onPasteJson = onPasteJson,
        )
    }
}

@Composable
fun AppearanceScreen(
    settings: SettingsRepository,
    iconPacks: IconPackRepository,
    launcherApps: LauncherAppsRepository,
    appActions: AppActionsRepository,
    hiddenApps: HiddenAppRepository,
    launchLog: LaunchLogRepository,
    onOpenHiddenApps: () -> Unit,
    ha: HaAccess,
    modifier: Modifier = Modifier,
) {
    val stored by settings.settings.collectAsStateWithLifecycle(initialValue = LauncherSettings())
    TabPaneScaffold(
        title = stringResource(R.string.settings_tab_appearance),
        modifier = modifier,
        scrollable = true,
    ) {
        HomeSettingsSection(settings = settings, stored = stored)
        IconSettingsSection(
            settings = settings,
            stored = stored,
            iconPacks = iconPacks,
            launcherApps = launcherApps,
        )
        AccentSettingsSection(settings = settings, stored = stored)
        BadgeSettingsSection(settings = settings, stored = stored)
        TransparencySettingsSection(settings = settings, stored = stored)
        DrawerSettingsSection(
            settings = settings,
            stored = stored,
            hiddenApps = hiddenApps,
            launchLog = launchLog,
            onOpenHiddenApps = onOpenHiddenApps,
        )
        GestureSettingsSection(
            settings = settings,
            stored = stored,
            launcherApps = launcherApps,
            appActions = appActions,
            ha = ha,
        )
        AiChatSettingsSection(settings = settings, stored = stored)
    }
}
