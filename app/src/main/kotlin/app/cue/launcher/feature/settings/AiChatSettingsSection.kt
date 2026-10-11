package app.cue.launcher.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.designsystem.ExperimentalLabel
import kotlinx.coroutines.launch

@Composable
internal fun AiChatSettingsSection(
    settings: SettingsRepository,
    stored: LauncherSettings,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    SettingsSection(title = stringResource(R.string.settings_scenarios_section), modifier = modifier) {
        SettingsSwitchRow(
            label = stringResource(R.string.settings_ai_chat),
            detail = stringResource(R.string.settings_ai_chat_detail),
            checked = stored.aiChatScenarios,
            onCheckedChange = { wanted -> scope.launch { settings.setAiChatScenarios(wanted) } },
            badge = { ExperimentalLabel() },
        )
    }
}
