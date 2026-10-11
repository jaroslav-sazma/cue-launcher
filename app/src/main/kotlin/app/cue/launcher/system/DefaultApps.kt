package app.cue.launcher.system

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.layout.DockRole
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
@Inject
class DefaultApps(
    private val application: Application,
    private val launcherApps: LauncherAppsRepository,
) {

    private val packageManager: PackageManager get() = application.packageManager

    suspend fun dockDefaults(): Map<DockRole, ComponentRef?> = withContext(Dispatchers.Default) {
        DockRole.entries.associateWith { role ->
            candidatePackages(role).firstNotNullOfOrNull { launcherApps.mainActivityOf(it) }
        }
    }

    suspend fun browserPackage(): String? = packageManager.browserPackage()

    private fun candidatePackages(role: DockRole): List<String> = when (role) {
        DockRole.PHONE ->
            listOfNotNull(defaultDialerPackage()) + packageManager.handlersOf(Intent(Intent.ACTION_DIAL))

        DockRole.MESSAGES -> listOfNotNull(Telephony.Sms.getDefaultSmsPackage(application)) +
            packageManager.handlersOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING))

        DockRole.CAMERA -> packageManager.handlersOf(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))

        DockRole.BROWSER -> packageManager.handlersOf(webProbe())
    }

    private fun defaultDialerPackage(): String? =
        application.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
}

internal suspend fun PackageManager.browserPackage(): String? = withContext(Dispatchers.Default) {
    handlersOf(webProbe()).firstOrNull()
}

private fun PackageManager.handlersOf(intent: Intent): List<String> {
    val preferred = resolveActivity(intent, DEFAULT_ONLY)
    val all = queryIntentActivities(intent, ANY)
    return (listOfNotNull(preferred) + all).map { it.activityInfo.packageName }.distinct()
}

private fun webProbe(): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)

private val DEFAULT_ONLY: PackageManager.ResolveInfoFlags =
    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())

private val ANY: PackageManager.ResolveInfoFlags = PackageManager.ResolveInfoFlags.of(0)
