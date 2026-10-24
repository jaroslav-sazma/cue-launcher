package app.cue.launcher.system

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.UserHandle
import android.util.Log
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.AppProfile
import app.cue.launcher.data.scenario.TapAction
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

@SingleIn(AppScope::class)
@Inject
class AppActionsRepository(
    private val application: Application,
    private val profiles: UserProfiles,
    val shortcutConfig: ShortcutConfigurator,
) {

    private val launcherApps: LauncherApps = application.getSystemService(LauncherApps::class.java)

    private val packageManager: PackageManager = application.packageManager

    private val recipes: List<DeepLinkRecipe> by lazy { loadRecipes() }

    // Without shortcuts no shortcut API is asked at all, and the capabilities, which come from the
    // shortcuts, go with them.
    suspend fun actionsOf(packageName: String, shortcuts: Boolean = true): List<AppActionEntry> =
        withContext(Dispatchers.Default) {
            if (!shortcuts) {
                val creators = shortcutCreatorsOf(packageManager, packageName)
                return@withContext linkEntries(packageName) + activityEntries(packageName, creators)
            }
            val published = shortcutsOf(packageName)
            val pickers = shortcutConfig.pickersOf(packageName)
            shortcutEntries(packageName, published) +
                pickers.entries +
                linkEntries(packageName) +
                capabilityEntries(packageName, published) +
                activityEntries(packageName, pickers.creators)
        }

    // Links and app screens are started as intents, and an intent only ever reaches the launcher's
    // own profile. Of a work app only what LauncherApps starts in its profile is offered: shortcuts.
    suspend fun actionsOf(app: ComponentRef): AppActions = withContext(Dispatchers.Default) {
        val user = profiles.userOf(app.userSerial)
        val profile = user?.let(profiles::appProfileOf)
        when {
            user != null && profiles.isOwn(user) -> AppActions(AppActionSource.OWN, actionsOf(app.packageName))
            user == null || profile == null -> AppActions(AppActionSource.WORK, emptyList())
            !profiles.isReadable(user) -> AppActions(AppActionSource.WORK_PAUSED, emptyList())
            else -> {
                val published = shortcutsOf(app.packageName, user)
                val entries = shortcutEntries(app.packageName, published, profile) +
                    shortcutConfig.pickersOf(app.packageName, user).entries +
                    capabilityEntries(app.packageName, published, profile)
                AppActions(AppActionSource.WORK, entries)
            }
        }
    }

    // Asked first, as the query itself answers a launcher that is not the default with an exception,
    // logged with its stack for every one of the apps the chat brief reads.
    private fun shortcutsOf(packageName: String, user: UserHandle = profiles.own): List<ShortcutInfo> =
        if (shortcutConfig.canReadShortcuts()) {
            launcherApps.appShortcutInfos(packageName, user, TAG)
        } else {
            emptyList()
        }

    private fun shortcutEntries(
        packageName: String,
        shortcuts: List<ShortcutInfo>,
        profile: AppProfile? = null,
    ): List<AppActionEntry> = shortcuts
        .filter { it.isEnabled }
        .map { info ->
            AppActionEntry(
                kind = AppActionKind.SHORTCUT,

                label = info.shortLabel?.toString()?.takeIf { it.isNotBlank() }
                    ?: info.longLabel?.toString()?.takeIf { it.isNotBlank() }
                    ?: info.id,
                detail = null,
                action = TapAction.Shortcut(packageName = packageName, shortcutId = info.id, profile = profile),
            )
        }

    private fun capabilityEntries(
        packageName: String,
        shortcuts: List<ShortcutInfo>,
        profile: AppProfile? = null,
    ): List<AppActionEntry> = shortcuts
        .filter { it.isEnabled }
        .flatMap { info -> info.capabilities.map { it.name } }
        .distinct()
        .map { capability ->
            AppActionEntry(
                kind = AppActionKind.CAPABILITY,
                label = capabilityLabel(capability),
                detail = capability,
                action = TapAction.Capability(packageName = packageName, capability = capability, profile = profile),
            )
        }

    private fun linkEntries(packageName: String): List<AppActionEntry> =
        recipesFor(recipes, packageName)
            .filter { handlesUri(packageName, it.uri) }
            .map { recipe ->
                AppActionEntry(
                    kind = AppActionKind.LINK,
                    label = recipe.label,
                    detail = recipe.uri,
                    action = TapAction.DeepLink(recipe.uri),
                )
            }

    private fun handlesUri(packageName: String, uri: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(packageName)
        val flags = PackageManager.ResolveInfoFlags.of(0L)
        return packageManager.queryIntentActivities(intent, flags).isNotEmpty()
    }

    private fun activityEntries(packageName: String, creators: Set<String>): List<AppActionEntry> {
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())
        val activities = try {
            packageManager.getPackageInfo(packageName, flags).activities.orEmpty()
        } catch (missing: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Package $packageName is not visible", missing)
            return emptyList()
        }

        val main = launcherApps.getActivityList(packageName, profiles.own)
            .map { it.componentName.className }
            .toSet()
        val collator = Collator.getInstance()
        return activities
            .filter { info ->
                info.exported && info.enabled && info.name !in main &&
                    info.name !in creators && info.targetActivity !in creators
            }
            .map { info ->
                AppActionEntry(
                    kind = AppActionKind.ACTIVITY,
                    label = activityLabel(info),
                    detail = info.name.substringAfterLast('.'),
                    action = TapAction.ActivityLaunch(packageName = packageName, component = info.name),
                )
            }
            .sortedWith { first, second -> collator.compare(first.label, second.label) }
    }

    private fun activityLabel(info: ActivityInfo): String {
        if (info.labelRes == 0 && info.nonLocalizedLabel == null) {
            return info.name.substringAfterLast('.')
        }
        return info.loadLabel(packageManager).toString().ifBlank { info.name.substringAfterLast('.') }
    }

    private fun loadRecipes(): List<DeepLinkRecipe> = try {
        parseDeepLinkRecipes(application.assets.open(RECIPES_ASSET).bufferedReader().use { it.readText() })
    } catch (missing: java.io.IOException) {
        Log.w(TAG, "Could not read the recipe catalog", missing)
        emptyList()
    }

    private companion object {
        const val TAG = "AppActionsRepository"

        const val RECIPES_ASSET = "deeplink_recipes.json"
    }
}
