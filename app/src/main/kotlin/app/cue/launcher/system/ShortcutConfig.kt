package app.cue.launcher.system

import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.util.Log
import app.cue.launcher.data.scenario.TapAction

data class ConfiguredShortcut(val label: String?, val action: TapAction)

data class ShortcutPickers(val entries: List<AppActionEntry>, val creators: Set<String>)

data class LegacyShortcut(
    val action: String?,
    val uri: String?,
    val packageName: String?,
    val className: String?,
    val hasExtras: Boolean,
)

// A legacy CREATE_SHORTCUT result carries a whole Intent; schemaVersion 1 stores only a URI or a
// component, so anything richer than that has to be turned down instead of saved half-way.
fun legacyShortcutAction(shortcut: LegacyShortcut): TapAction? {
    if (shortcut.hasExtras) return null
    val uri = shortcut.uri?.trim()?.takeIf { it.isNotEmpty() }
    val packageName = shortcut.packageName?.trim()?.takeIf { it.isNotEmpty() }
    val className = shortcut.className?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        uri != null && shortcut.action in VIEWING_ACTIONS -> TapAction.DeepLink(uri)
        uri == null && packageName != null && className != null && shortcut.action in LAUNCHING_ACTIONS ->
            TapAction.ActivityLaunch(packageName = packageName, component = className)

        else -> null
    }
}

internal fun LauncherApps.shortcutConfigActivities(
    packageName: String,
    user: UserHandle,
    tag: String,
): List<LauncherActivityInfo> = try {
    getShortcutConfigActivityList(packageName, user).orEmpty()
} catch (denied: SecurityException) {
    Log.w(tag, "The launcher is not allowed to list shortcut pickers of $packageName", denied)
    emptyList()
} catch (locked: IllegalStateException) {
    Log.w(tag, "Shortcut pickers of $packageName cannot be read right now", locked)
    emptyList()
}

private val VIEWING_ACTIONS = setOf(null, "android.intent.action.VIEW")

private val LAUNCHING_ACTIONS = setOf(null, "android.intent.action.MAIN")
