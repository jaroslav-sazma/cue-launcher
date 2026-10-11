package app.cue.launcher.system

import android.app.Application
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.Locale

@SingleIn(AppScope::class)
@Inject
class EnglishLabels(private val application: Application) {

    private val resourcesByPackage = mutableMapOf<String, Resources>()

    private val needed: Boolean
        get() = Locale.getDefault().language != Locale.ENGLISH.language

    fun labelOf(info: LauncherActivityInfo): String? {
        if (!needed) return null
        val activityInfo = info.activityInfo

        val labelRes = when {
            activityInfo.labelRes != 0 -> activityInfo.labelRes
            else -> activityInfo.applicationInfo?.labelRes ?: 0
        }
        val resources = if (labelRes == 0) null else resourcesOf(activityInfo.packageName)
        return resources?.let { label(it, labelRes, activityInfo.packageName) }
    }

    private fun label(resources: Resources, labelRes: Int, packageName: String): String? = try {
        resources.getString(labelRes)
    } catch (missing: Resources.NotFoundException) {
        Log.w(TAG, "Label $labelRes is missing in $packageName", missing)
        null
    }

    @Synchronized
    fun forget(packageName: String) {
        resourcesByPackage.remove(packageName)
    }

    @Synchronized
    fun clear() {
        resourcesByPackage.clear()
    }

    @Synchronized
    private fun resourcesOf(packageName: String): Resources? =
        resourcesByPackage[packageName] ?: englishResources(packageName)?.also {
            resourcesByPackage[packageName] = it
        }

    private fun englishResources(packageName: String): Resources? = try {
        val packageContext = application.createPackageContext(packageName, 0)
        val configuration = Configuration(packageContext.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        packageContext.createConfigurationContext(configuration).resources
    } catch (missing: PackageManager.NameNotFoundException) {

        Log.d(TAG, "Resources of $packageName cannot be opened", missing)
        null
    }

    private companion object {
        const val TAG = "EnglishLabels"
    }
}
