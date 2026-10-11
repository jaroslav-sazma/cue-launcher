package app.cue.launcher.feature.home

import androidx.compose.runtime.Stable
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.feature.scenarios.builder.HaAccess
import app.cue.launcher.system.AppActionsRepository
import app.cue.launcher.system.LauncherAppsRepository

@Stable
class WidgetSettingsAccess(
    val settings: SettingsRepository,
    val launcherApps: LauncherAppsRepository,
    val appActions: AppActionsRepository,
    val ha: HaAccess = HaAccess.NOT_CONFIGURED,
)
