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
internal fun TransparencySettingsSection(
    settings: SettingsRepository,
    stored: LauncherSettings,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    SettingsSection(
        title = stringResource(R.string.settings_transparency_section),
        modifier = modifier,
    ) {
        SettingsSwitchRow(
            label = stringResource(R.string.settings_reduced_transparency),
            detail = stringResource(R.string.settings_reduced_transparency_hint),
            checked = stored.appearance.reducedTransparency,
            onCheckedChange = { wanted -> scope.launch { settings.setReducedTransparency(wanted) } },
        )
    }
}
