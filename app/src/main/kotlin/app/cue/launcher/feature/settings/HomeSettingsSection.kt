package app.cue.launcher.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.settings.SettingsRepository
import kotlinx.coroutines.launch

@Composable
internal fun HomeSettingsSection(
    settings: SettingsRepository,
    stored: LauncherSettings,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    SettingsSection(title = stringResource(R.string.settings_home_section), modifier = modifier) {
        SettingsSwitchRow(
            label = stringResource(R.string.settings_layout_locked),
            detail = stringResource(R.string.settings_layout_locked_hint),
            checked = stored.layoutLocked,
            onCheckedChange = { wanted -> scope.launch { settings.setLayoutLocked(wanted) } },
        )
    }
}
