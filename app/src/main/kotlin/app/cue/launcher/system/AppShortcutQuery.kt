package app.cue.launcher.system

import android.content.ActivityNotFoundException
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.util.Log

// A conversation shortcut's id can be a phone number or a chat handle, and the exceptions repeat it,
// so only the package and the failure's class are logged.
internal inline fun startShortcutOutcome(packageName: String, tag: String, start: () -> Unit): ActionOutcome =
    try {
        start()
        ActionOutcome.DONE
    } catch (missing: ActivityNotFoundException) {
        Log.w(tag, "A shortcut of $packageName no longer exists: ${missing.javaClass.simpleName}")
        ActionOutcome.TARGET_MISSING
    } catch (denied: SecurityException) {
        Log.w(tag, "Launching a shortcut of $packageName was denied: ${denied.javaClass.simpleName}")
        ActionOutcome.FAILED
    } catch (locked: IllegalStateException) {
        Log.w(tag, "A shortcut of $packageName cannot be started right now: ${locked.javaClass.simpleName}")
        ActionOutcome.FAILED
    }

internal fun LauncherApps.appShortcutInfos(
    packageName: String,
    user: UserHandle,
    tag: String,
): List<ShortcutInfo> {
    val query = LauncherApps.ShortcutQuery()
        .setPackage(packageName)
        .setQueryFlags(APP_SHORTCUT_FLAGS)
    return try {
        getShortcuts(query, user).orEmpty()
    } catch (denied: SecurityException) {
        Log.w(tag, "The launcher is not allowed to query shortcuts of $packageName", denied)
        emptyList()
    } catch (locked: IllegalStateException) {
        Log.w(tag, "Shortcuts of $packageName cannot be read right now", locked)
        emptyList()
    }
}

internal const val APP_SHORTCUT_FLAGS = LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
