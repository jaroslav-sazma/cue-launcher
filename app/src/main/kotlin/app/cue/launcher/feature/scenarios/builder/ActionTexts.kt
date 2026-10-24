package app.cue.launcher.feature.scenarios.builder

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.AppProfile
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.system.AppActionKind

@Composable
internal fun inProfile(name: String, profile: AppProfile?): String = when (profile) {
    null -> name
    AppProfile.WORK -> stringResource(R.string.builder_action_work, name)
}

@Composable
internal fun actionChoiceLabel(choice: ActionChoice): String = stringResource(
    when (choice) {
        ActionChoice.OPEN_APP -> R.string.action_choice_open_app
        ActionChoice.IN_APP -> R.string.action_choice_in_app
        ActionChoice.SYSTEM -> R.string.action_choice_system
        ActionChoice.HA -> R.string.action_choice_ha
        ActionChoice.URI -> R.string.action_choice_uri
    },
)

@Composable
internal fun actionChoiceDetail(choice: ActionChoice): String? = when (choice) {
    ActionChoice.OPEN_APP -> null
    ActionChoice.IN_APP -> stringResource(R.string.action_choice_in_app_detail)
    ActionChoice.SYSTEM -> stringResource(R.string.action_choice_system_detail)
    ActionChoice.HA -> null
    ActionChoice.URI -> stringResource(R.string.action_choice_uri_detail)
}

@Composable
internal fun appActionKindTitle(kind: AppActionKind): String = stringResource(
    when (kind) {
        AppActionKind.SHORTCUT -> R.string.action_kind_shortcuts
        AppActionKind.CONFIGURABLE -> R.string.action_kind_configurable
        AppActionKind.LINK -> R.string.action_kind_links
        AppActionKind.CAPABILITY -> R.string.action_kind_capabilities
        AppActionKind.ACTIVITY -> R.string.action_kind_activities
    },
)

@Composable
internal fun appActionKindDetail(kind: AppActionKind): String = stringResource(
    when (kind) {
        AppActionKind.SHORTCUT -> R.string.action_kind_shortcuts_detail
        AppActionKind.CONFIGURABLE -> R.string.action_kind_configurable_detail
        AppActionKind.LINK -> R.string.action_kind_links_detail
        AppActionKind.CAPABILITY -> R.string.action_kind_capabilities_detail
        AppActionKind.ACTIVITY -> R.string.action_kind_activities_detail
    },
)

@Composable
internal fun systemActionSectionTitle(section: SystemActionSection): String = stringResource(
    when (section) {
        SystemActionSection.SOUND -> R.string.action_system_sound
        SystemActionSection.DEVICE -> R.string.action_system_device
        SystemActionSection.PANELS -> R.string.action_system_panels
        SystemActionSection.PLACE -> R.string.action_system_place
    },
)

@Composable
@Suppress("CyclomaticComplexMethod")
internal fun systemActionLabel(action: SystemActionType): String = stringResource(
    when (action) {
        SystemActionType.RINGER_SILENT -> R.string.action_system_ringer_silent
        SystemActionType.RINGER_VIBRATE -> R.string.action_system_ringer_vibrate
        SystemActionType.RINGER_NORMAL -> R.string.action_system_ringer_normal
        SystemActionType.DND_ON -> R.string.action_system_dnd_on
        SystemActionType.DND_OFF -> R.string.action_system_dnd_off
        SystemActionType.FLASHLIGHT -> R.string.action_system_flashlight
        SystemActionType.TV_REMOTE -> R.string.action_system_tv_remote
        SystemActionType.PANEL_VOLUME -> R.string.action_system_panel_volume
        SystemActionType.PANEL_INTERNET -> R.string.action_system_panel_internet
        SystemActionType.PANEL_WIFI -> R.string.action_system_panel_wifi
        SystemActionType.PANEL_NFC -> R.string.action_system_panel_nfc
        SystemActionType.OPEN_SETTINGS -> R.string.action_system_open_settings
        SystemActionType.REMEMBER_PLACE -> R.string.action_system_remember_place
        SystemActionType.SHOW_PLACE -> R.string.action_system_show_place
    },
)

@Composable
internal fun systemActionDetail(action: SystemActionType): String? = when (action) {
    SystemActionType.DND_ON -> stringResource(R.string.action_system_dnd_access)
    SystemActionType.DND_OFF -> stringResource(R.string.action_system_dnd_off_detail)

    SystemActionType.RINGER_SILENT -> stringResource(R.string.action_system_ringer_silent_detail)
    SystemActionType.RINGER_VIBRATE,
    SystemActionType.RINGER_NORMAL,
    -> stringResource(R.string.action_system_ringer_ends_dnd_detail)
    SystemActionType.FLASHLIGHT -> stringResource(R.string.action_system_flashlight_detail)
    SystemActionType.TV_REMOTE -> stringResource(R.string.action_system_tv_remote_detail)
    SystemActionType.REMEMBER_PLACE -> stringResource(R.string.action_system_remember_place_detail)
    SystemActionType.SHOW_PLACE -> stringResource(R.string.action_system_show_place_detail)
    else -> null
}

@Composable
internal fun haTestText(state: HaTestState, millis: Long, detail: String?): String = when (state) {
    HaTestState.NOT_RUN -> stringResource(R.string.action_ha_test_hint)
    HaTestState.RUNNING -> stringResource(R.string.action_ha_test_running)
    HaTestState.OK -> stringResource(R.string.action_ha_test_ok, millis)
    HaTestState.SENT -> stringResource(R.string.action_ha_test_sent)
    HaTestState.FAILED -> detail?.let { stringResource(R.string.action_ha_test_failed_detail, it) }
        ?: stringResource(R.string.action_ha_test_failed)

    HaTestState.NOT_CONFIGURED -> stringResource(R.string.action_ha_test_not_configured)
    HaTestState.CONFIRMATION_UNAVAILABLE -> stringResource(R.string.action_confirm_unavailable)
}

@Composable
internal fun actionFormErrorText(error: ActionFormError): String = stringResource(
    when (error) {
        ActionFormError.URI_EMPTY -> R.string.action_error_uri_empty
        ActionFormError.URI_UNUSABLE -> R.string.action_error_uri_unusable
        ActionFormError.HA_SERVICE_EMPTY -> R.string.action_error_ha_service_empty
        ActionFormError.HA_SERVICE_FORMAT -> R.string.action_error_ha_service_format
        ActionFormError.HA_DATA -> R.string.action_error_ha_data
        ActionFormError.HA_STATE_ENTITY_FORMAT -> R.string.action_error_ha_state_entity_format
    },
)
