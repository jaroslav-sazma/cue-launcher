package app.cue.launcher.system

import app.cue.launcher.data.ha.HaServiceCall
import app.cue.launcher.data.ha.haServiceCallOf
import app.cue.launcher.data.scenario.RingerModeState
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction

sealed interface ActionRoute {

    data class MainActivity(val packageName: String) : ActionRoute

    data class Activity(val packageName: String, val className: String) : ActionRoute

    data class ViewUri(val uri: String) : ActionRoute

    data class IntentUri(val uri: String) : ActionRoute

    data class Shortcut(val packageName: String, val shortcutId: String) : ActionRoute

    data class Capability(val packageName: String, val capability: String) : ActionRoute

    data class Ringer(val mode: RingerModeState) : ActionRoute

    data class DoNotDisturb(val enabled: Boolean) : ActionRoute

    data object Torch : ActionRoute

    data object RememberPlace : ActionRoute

    data object ShowPlace : ActionRoute

    data object TvRemote : ActionRoute

    data class SystemScreen(val screen: SystemScreenTarget) : ActionRoute

    data class HaService(val call: HaServiceCall) : ActionRoute

    data object Malformed : ActionRoute
}

enum class SystemScreenTarget {
    VOLUME_PANEL,
    INTERNET_PANEL,
    WIFI_PANEL,
    NFC_PANEL,
    SETTINGS,
}

fun actionRouteOf(action: TapAction): ActionRoute = when (action) {
    is TapAction.App -> action.packageName.nonBlank()
        ?.let(ActionRoute::MainActivity)
        ?: ActionRoute.Malformed

    is TapAction.ActivityLaunch -> activityRoute(action)

    is TapAction.DeepLink -> deepLinkRoute(action.uri)

    is TapAction.Shortcut -> pair(action.packageName, action.shortcutId)
        ?.let { (packageName, id) -> ActionRoute.Shortcut(packageName, id) }
        ?: ActionRoute.Malformed

    is TapAction.Capability -> pair(action.packageName, action.capability)
        ?.let { (packageName, capability) -> ActionRoute.Capability(packageName, capability) }
        ?: ActionRoute.Malformed

    is TapAction.SystemAction -> systemActionRoute(action.action)

    is TapAction.HaAction -> haServiceCallOf(action.service, action.entity, action.data)
        ?.let(ActionRoute::HaService)
        ?: ActionRoute.Malformed
}

fun combinedOutcome(outcomes: List<ActionOutcome>): ActionOutcome =
    outcomes.firstOrNull { it != ActionOutcome.DONE } ?: ActionOutcome.DONE

@Suppress("CyclomaticComplexMethod")
fun systemActionRoute(action: SystemActionType): ActionRoute = when (action) {
    SystemActionType.RINGER_SILENT -> ActionRoute.Ringer(RingerModeState.SILENT)
    SystemActionType.RINGER_VIBRATE -> ActionRoute.Ringer(RingerModeState.VIBRATE)
    SystemActionType.RINGER_NORMAL -> ActionRoute.Ringer(RingerModeState.NORMAL)
    SystemActionType.DND_ON -> ActionRoute.DoNotDisturb(enabled = true)
    SystemActionType.DND_OFF -> ActionRoute.DoNotDisturb(enabled = false)
    SystemActionType.FLASHLIGHT -> ActionRoute.Torch
    SystemActionType.TV_REMOTE -> ActionRoute.TvRemote
    SystemActionType.PANEL_VOLUME -> ActionRoute.SystemScreen(SystemScreenTarget.VOLUME_PANEL)
    SystemActionType.PANEL_INTERNET -> ActionRoute.SystemScreen(SystemScreenTarget.INTERNET_PANEL)
    SystemActionType.PANEL_WIFI -> ActionRoute.SystemScreen(SystemScreenTarget.WIFI_PANEL)
    SystemActionType.PANEL_NFC -> ActionRoute.SystemScreen(SystemScreenTarget.NFC_PANEL)
    SystemActionType.OPEN_SETTINGS -> ActionRoute.SystemScreen(SystemScreenTarget.SETTINGS)
    SystemActionType.REMEMBER_PLACE -> ActionRoute.RememberPlace
    SystemActionType.SHOW_PLACE -> ActionRoute.ShowPlace
}

fun absoluteClassName(packageName: String, component: String): String {
    val name = component.trim()
    return when {
        name.startsWith('.') -> packageName + name
        '.' in name -> name
        else -> "$packageName.$name"
    }
}

data class ShortcutCandidate(
    val id: String,
    val capabilities: List<String>,
    val enabled: Boolean,
)

fun shortcutForCapability(candidates: List<ShortcutCandidate>, capability: String): String? =
    candidates.firstOrNull { it.enabled && capability in it.capabilities }?.id

private fun activityRoute(action: TapAction.ActivityLaunch): ActionRoute {
    val (packageName, component) = pair(action.packageName, action.component) ?: return ActionRoute.Malformed
    return ActionRoute.Activity(packageName, absoluteClassName(packageName, component))
}

// An intent: or android-app: URI is a serialized Intent, not an address. ACTION_VIEW over it
// resolves to nothing, so it has to be parsed back into the Intent it describes — that is the form
// Home Assistant hands out, and the only one that carries a target package and extras.
private fun deepLinkRoute(uri: String): ActionRoute {
    val target = uri.nonBlank() ?: return ActionRoute.Malformed
    return when (schemeOf(target)?.lowercase()) {
        FILE_SCHEME -> ActionRoute.Malformed
        INTENT_SCHEME, ANDROID_APP_SCHEME -> ActionRoute.IntentUri(target)
        else -> ActionRoute.ViewUri(target)
    }
}

private fun schemeOf(uri: String): String? {
    val colon = uri.indexOf(':')
    if (colon <= 0) return null
    return uri.take(colon).takeIf { scheme -> scheme.all { it.isLetterOrDigit() || it in SCHEME_CHARS } }
}

private fun pair(first: String, second: String): Pair<String, String>? {
    val left = first.nonBlank()
    val right = second.nonBlank()
    return if (left == null || right == null) null else left to right
}

private fun String.nonBlank(): String? = trim().takeIf { it.isNotEmpty() }

internal const val FILE_SCHEME = "file"

private const val INTENT_SCHEME = "intent"

private const val ANDROID_APP_SCHEME = "android-app"

private const val SCHEME_CHARS = "+-."
