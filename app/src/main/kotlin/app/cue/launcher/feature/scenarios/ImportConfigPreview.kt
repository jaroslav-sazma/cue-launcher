package app.cue.launcher.feature.scenarios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.AccentSource
import app.cue.launcher.data.scenario.AppearanceConfig
import app.cue.launcher.data.scenario.IconMode
import app.cue.launcher.data.scenario.accentHexOf
import app.cue.launcher.data.scenario.customAccentRgb
import app.cue.launcher.data.scenario.effectiveAccent
import app.cue.launcher.data.scenario.LauncherConfig
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.supportingInk
import app.cue.launcher.designsystem.supportingStyle
import app.cue.launcher.feature.scenarios.builder.launcherWidgetLabel

@Composable
internal fun ImportConfigSection(config: LauncherConfig, modifier: Modifier = Modifier) {
    val zone = config.staticZone
    val items = zone.grid.size + zone.dock.size + zone.widgetPanel.dock.size
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.import_section_config))
        ImportDetailRow(
            title = stringResource(R.string.import_config_home),
            detail = pluralStringResource(R.plurals.import_config_items, items, items),
        )
        ImportDetailRow(
            title = stringResource(R.string.import_config_panel),
            detail = pluralStringResource(
                R.plurals.import_config_widgets,
                zone.widgetPanel.widgets.size,
                zone.widgetPanel.widgets.size,
            ),
        )
        ConfigSettingRows(config)
        Text(
            text = stringResource(R.string.import_config_note),
            style = supportingStyle,
            color = supportingInk(),
            modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
        )
    }
}

@Composable
private fun ConfigSettingRows(config: LauncherConfig) {
    val gestures = remember(config.gestures) { gestureDisclosuresOf(config.gestures) }
    gestures.forEach { disclosure ->
        ImportDetailRow(title = gestureLabel(disclosure.gesture), detail = null) {
            ImportTapLines(disclosure.tap)
        }
    }
    ImportDetailRow(
        title = stringResource(R.string.settings_icons_section),
        detail = iconModeLabel(config.appearance.iconMode),
    ) {
        val overrides = config.appearance.iconOverrides.size
        if (overrides > 0) {
            ImportSupportingLine(pluralStringResource(R.plurals.import_config_icon_overrides, overrides, overrides))
        }
    }
    ImportDetailRow(
        title = stringResource(R.string.settings_accent_section),
        detail = accentLabel(config.appearance),
    )
    val hidden = config.drawer.hiddenApps.size
    if (hidden > 0) {
        ImportDetailRow(
            title = stringResource(R.string.settings_drawer_hidden),
            detail = pluralStringResource(R.plurals.import_config_hidden_apps, hidden, hidden),
        )
    }
    config.widgets.mediaApp?.let { player ->
        ImportDetailRow(
            title = stringResource(
                R.string.builder_summary_value,
                launcherWidgetLabel(LauncherWidgetType.MEDIA),
                stringResource(R.string.widget_settings_media_player),
            ),
            detail = appListText(listOf(player), AppJoiner.OR),
        )
    }
}

@Composable
private fun gestureLabel(gesture: ConfigGesture): String = when (gesture) {
    ConfigGesture.SWIPE_LEFT -> stringResource(R.string.settings_gesture_swipe_left)
    ConfigGesture.SEARCHBAR_TAP -> widgetTapLabel(LauncherWidgetType.SEARCHBAR)
    ConfigGesture.CALENDAR_TAP -> widgetTapLabel(LauncherWidgetType.CALENDAR)
}

@Composable
private fun widgetTapLabel(widget: LauncherWidgetType): String = stringResource(
    R.string.builder_summary_value,
    launcherWidgetLabel(widget),
    stringResource(R.string.widget_settings_tap_action),
)

@Composable
private fun accentLabel(appearance: AppearanceConfig): String = when (appearance.effectiveAccent) {
    AccentSource.PRIMARY -> stringResource(R.string.settings_accent_primary)
    AccentSource.TERTIARY -> stringResource(R.string.settings_accent_tertiary)
    AccentSource.CUSTOM -> stringResource(
        R.string.settings_accent_custom_value,
        appearance.customAccentRgb?.let(::accentHexOf).orEmpty(),
    )
}

@Composable
private fun iconModeLabel(mode: IconMode): String = stringResource(
    when (mode) {
        IconMode.NATIVE -> R.string.settings_icons_native
        IconMode.ICONPACK -> R.string.settings_icons_pack
        IconMode.TINTED -> R.string.settings_icons_tinted
    },
)
