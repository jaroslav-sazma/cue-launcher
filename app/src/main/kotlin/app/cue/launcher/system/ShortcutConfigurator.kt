package app.cue.launcher.system

import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.util.Log
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.TapAction
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
@Inject
class ShortcutConfigurator(
    private val application: Application,
    private val profiles: UserProfiles,
) {

    private val launcherApps: LauncherApps = application.getSystemService(LauncherApps::class.java)

    // Only the default home app may read shortcuts. Everything in the launcher that touches them
    // hangs off this: the published shortcuts of an app, its capabilities, and the pickers below.
    fun canReadShortcuts(): Boolean = launcherApps.hasShortcutHostPermission()

    // A shortcut picker started plainly as an activity only shows an empty screen — it waits for
    // CREATE_SHORTCUT and a result. It gets its own section and never counts as an app screen.
    //
    // The two halves of the API are guarded differently: listing the pickers needs no permission,
    // opening one does. Without the home role the rows would be offered and then fail on the tap,
    // so they are left out and the sheet says why instead. The creators are still collected —
    // whether a picker can be opened or not, it must never show up among the app screens.
    fun pickersOf(packageName: String, user: UserHandle = profiles.own): ShortcutPickers {
        val pickers = pickers(packageName, user)
        val offered = if (canReadShortcuts()) pickers else emptyList()
        return ShortcutPickers(
            entries = offered.map { picker ->
                AppActionEntry(
                    kind = AppActionKind.CONFIGURABLE,
                    label = picker.label?.toString()?.takeIf { it.isNotBlank() }
                        ?: picker.componentName.className.substringAfterLast('.'),
                    detail = null,
                    action = null,
                    configActivity = picker.componentName.className,
                )
            },
            creators = shortcutCreatorsOf(application.packageManager, packageName) +
                pickers.map { it.componentName.className },
        )
    }

    private fun pickers(packageName: String, user: UserHandle): List<LauncherActivityInfo> =
        launcherApps.shortcutConfigActivities(packageName, user, TAG)

    suspend fun intentFor(app: ComponentRef, className: String): IntentSender? =
        withContext(Dispatchers.Default) {
            val user = profiles.userOf(app.userSerial) ?: return@withContext null
            pickers(app.packageName, user)
                .firstOrNull { it.componentName.className == className }
                ?.let(::pickerIntent)
        }

    // A legacy result is a link or an app screen, which only the launcher's own profile can start,
    // so a picker of a work app has to hand back a pinned shortcut.
    suspend fun resolve(result: Intent?, app: ComponentRef): ConfiguredShortcut? = withContext(Dispatchers.Default) {
        val own = app.userSerial == profiles.ownSerial
        result?.let { pinnedShortcut(it) ?: legacyShortcut(it).takeIf { own } }
    }

    private fun pickerIntent(picker: LauncherActivityInfo): IntentSender? = try {
        launcherApps.getShortcutConfigActivityIntent(picker)
    } catch (denied: SecurityException) {
        Log.w(TAG, "The launcher is not allowed to open the shortcut picker ${picker.componentName}", denied)
        null
    } catch (locked: IllegalStateException) {
        Log.w(TAG, "The shortcut picker ${picker.componentName} cannot be opened right now", locked)
        null
    }

    private fun pinnedShortcut(result: Intent): ConfiguredShortcut? = pinRequest(result)
        ?.takeIf { it.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT }
        ?.let(::acceptedShortcut)
        ?.let { info ->
            ConfiguredShortcut(
                label = info.shortLabel?.toString()?.takeIf { it.isNotBlank() },
                action = TapAction.Shortcut(
                    packageName = info.`package`,
                    shortcutId = info.id,
                    profile = profiles.appProfileOf(info.userHandle),
                ),
            )
        }

    private fun pinRequest(result: Intent): LauncherApps.PinItemRequest? = try {
        launcherApps.getPinItemRequest(result)
    } catch (denied: SecurityException) {
        Log.w(TAG, "The launcher is not allowed to read the shortcut the app handed back", denied)
        null
    }

    // Pinning is what keeps the shortcut launchable later — an unpinned custom shortcut belongs to
    // nobody and startShortcut() would not find it.
    private fun acceptedShortcut(request: LauncherApps.PinItemRequest): ShortcutInfo? = try {
        if (request.isValid && request.accept()) request.shortcutInfo else null
    } catch (invalid: IllegalStateException) {
        Log.w(TAG, "The shortcut could not be pinned", invalid)
        null
    } catch (denied: SecurityException) {
        Log.w(TAG, "Pinning the shortcut was denied", denied)
        null
    }

    @Suppress("DEPRECATION")
    private fun legacyShortcut(result: Intent): ConfiguredShortcut? =
        result.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
            ?.let { intent ->
                legacyShortcutAction(
                    LegacyShortcut(
                        action = intent.action,
                        uri = intent.data?.toString(),
                        packageName = intent.component?.packageName,
                        className = intent.component?.className,
                        hasExtras = intent.extras?.isEmpty == false,
                    ),
                )
            }
            ?.let { action ->
                ConfiguredShortcut(
                    label = result.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)?.takeIf { it.isNotBlank() },
                    action = action,
                )
            }

    private companion object {
        const val TAG = "ShortcutConfigurator"
    }
}

// Asked of the package manager alone, so a read that leaves shortcuts out never asks LauncherApps.
internal fun shortcutCreatorsOf(packageManager: PackageManager, packageName: String): Set<String> {
    val intent = Intent(Intent.ACTION_CREATE_SHORTCUT).setPackage(packageName)
    val flags = PackageManager.ResolveInfoFlags.of(0L)
    return packageManager.queryIntentActivities(intent, flags).mapTo(HashSet()) { it.activityInfo.name }
}
