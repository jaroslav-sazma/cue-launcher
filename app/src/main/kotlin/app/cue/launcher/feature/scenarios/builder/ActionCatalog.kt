package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction

enum class ActionChoice {

    OPEN_APP,

    IN_APP,

    SYSTEM,

    HA,

    URI,
}

val ACTION_CATALOG: List<ActionChoice> = listOf(
    ActionChoice.OPEN_APP,
    ActionChoice.IN_APP,
    ActionChoice.SYSTEM,
    ActionChoice.HA,
    ActionChoice.URI,
)

fun actionChoiceOf(action: TapAction): ActionChoice = when (action) {
    is TapAction.App -> ActionChoice.OPEN_APP
    is TapAction.Shortcut -> ActionChoice.IN_APP
    is TapAction.Capability -> ActionChoice.IN_APP
    is TapAction.ActivityLaunch -> ActionChoice.IN_APP
    is TapAction.SystemAction -> ActionChoice.SYSTEM
    is TapAction.HaAction -> ActionChoice.HA
    is TapAction.DeepLink -> ActionChoice.URI
}

enum class SystemActionSection { SOUND, DEVICE, PLACE, MAC, PANELS }

data class SystemActionGroup(val section: SystemActionSection, val actions: List<SystemActionType>)

val SYSTEM_ACTION_CATALOG: List<SystemActionType> = listOf(
    SystemActionType.RINGER_SILENT,
    SystemActionType.RINGER_VIBRATE,
    SystemActionType.RINGER_NORMAL,
    SystemActionType.DND_ON,
    SystemActionType.DND_OFF,
    SystemActionType.FLASHLIGHT,
    SystemActionType.TV_REMOTE,
    SystemActionType.REMEMBER_PLACE,
    SystemActionType.SHOW_PLACE,
    SystemActionType.MAC_LOCK,
    SystemActionType.PANEL_VOLUME,
    SystemActionType.PANEL_INTERNET,
    SystemActionType.PANEL_WIFI,
    SystemActionType.PANEL_NFC,
    SystemActionType.OPEN_SETTINGS,
)

// What the sheet leaves out while no Mac is paired; a stored one still runs and says why it cannot.
fun unpairedSystemActions(bridgePaired: Boolean): Set<SystemActionType> =
    if (bridgePaired) emptySet() else setOf(SystemActionType.MAC_LOCK)

fun systemActionSection(action: SystemActionType): SystemActionSection = when (action) {
    SystemActionType.RINGER_SILENT,
    SystemActionType.RINGER_VIBRATE,
    SystemActionType.RINGER_NORMAL,
    SystemActionType.DND_ON,
    SystemActionType.DND_OFF,
    -> SystemActionSection.SOUND

    SystemActionType.FLASHLIGHT,
    SystemActionType.TV_REMOTE,
    -> SystemActionSection.DEVICE

    SystemActionType.REMEMBER_PLACE,
    SystemActionType.SHOW_PLACE,
    -> SystemActionSection.PLACE

    SystemActionType.MAC_LOCK -> SystemActionSection.MAC

    SystemActionType.PANEL_VOLUME,
    SystemActionType.PANEL_INTERNET,
    SystemActionType.PANEL_WIFI,
    SystemActionType.PANEL_NFC,
    SystemActionType.OPEN_SETTINGS,
    -> SystemActionSection.PANELS
}

fun systemActionSections(
    catalog: List<SystemActionType> = SYSTEM_ACTION_CATALOG,
    unavailable: Set<SystemActionType> = emptySet(),
): List<SystemActionGroup> = SystemActionSection.entries.mapNotNull { section ->
    catalog.filter { systemActionSection(it) == section && it !in unavailable }
        .takeIf { it.isNotEmpty() }
        ?.let { SystemActionGroup(section, it) }
}

fun systemActionNeedsDndAccess(action: SystemActionType): Boolean =
    action == SystemActionType.DND_ON || action == SystemActionType.DND_OFF
