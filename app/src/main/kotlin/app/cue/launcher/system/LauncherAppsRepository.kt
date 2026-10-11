package app.cue.launcher.system

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import app.cue.launcher.data.launchlog.LaunchLogRepository
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.IconMode
import app.cue.launcher.designsystem.IconTint
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions")
@SingleIn(AppScope::class)
@Inject
class LauncherAppsRepository(
    private val application: Application,
    private val iconLoader: AppIconLoader,
    private val englishLabels: EnglishLabels,
    private val launchLog: LaunchLogRepository,
    private val processScope: CoroutineScope,
    val profiles: UserProfiles,
) {

    private val launcherApps: LauncherApps = application.getSystemService(LauncherApps::class.java)

    private val order = installedAppOrder()

    suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.Default) { queryApps() }

    fun observeInstalledApps(): Flow<List<InstalledApp>> =
        appListChanges()
            .map { queryApps() }
            .flowOn(Dispatchers.Default)
            .conflate()

    fun observeSearchableApps(): Flow<List<SearchableApp>> =
        appListChanges()
            .transform {
                val activities = queryActivities()
                emit(activities.map { (info, serial) -> SearchableApp(info.toInstalledApp(serial)) })
                emit(
                    activities.map { (info, serial) ->
                        SearchableApp(info.toInstalledApp(serial), englishLabels.labelOf(info))
                    },
                )
            }
            .flowOn(Dispatchers.Default)
            .conflate()

    suspend fun shortcut(component: ComponentRef): ShortcutApp? = withContext(Dispatchers.Default) {
        val user = profiles.userOf(component.userSerial) ?: return@withContext null
        val info = activityInfo(component, user) ?: return@withContext null
        ShortcutApp(
            app = info.toInstalledApp(component.userSerial),
            icon = iconLoader.icon(
                component = component,
                load = { info.getIcon(iconLoader.densityDpi) },
                badge = badgeFor(user),
            ),
        )
    }

    suspend fun icon(component: ComponentRef): ImageBitmap? {
        val user = profiles.userOf(component.userSerial)
        return iconLoader.icon(
            component = component,
            load = { user?.let { activityInfo(component, it)?.getIcon(iconLoader.densityDpi) } },
            badge = user?.let(::badgeFor),
        )
    }

    suspend fun previewIcon(
        component: ComponentRef,
        mode: IconMode,
        tint: IconTint? = null,
    ): ImageBitmap? = renderIcon(component, IconRequest(mode = mode, tint = tint))

    suspend fun surfaceIcon(component: ComponentRef, tint: IconTint): ImageBitmap? =
        renderIcon(component, IconRequest(tint = tint))

    // Tinted whatever the appearance setting says, for surfaces that hold their own colour.
    suspend fun tintedIcon(component: ComponentRef, tint: IconTint): ImageBitmap? =
        renderIcon(component, IconRequest(mode = IconMode.TINTED, tint = tint))

    private suspend fun renderIcon(component: ComponentRef, request: IconRequest): ImageBitmap? {
        val user = profiles.userOf(component.userSerial)
        return iconLoader.render(
            component = component,
            request = request,
            load = { user?.let { activityInfo(component, it)?.getIcon(iconLoader.densityDpi) } },
            badge = user?.let(::badgeFor),
        )
    }

    fun cachedIcon(component: ComponentRef): ImageBitmap? = iconLoader.cachedIcon(component)

    val iconRevision: StateFlow<Int> get() = iconLoader.revision

    val icons: IconAppearance get() = iconLoader.appearance

    suspend fun mainActivityOf(packageName: String): ComponentRef? =
        withContext(Dispatchers.Default) {
            val serial = profiles.serialOf(profiles.own)
            launcherApps.getActivityList(packageName, profiles.own)
                .firstOrNull()
                ?.componentName
                ?.toComponentRef(serial)
        }

    suspend fun labelOf(packageName: String): String? =
        withContext(Dispatchers.Default) {
            launcherApps.getActivityList(packageName, profiles.own).firstOrNull()?.label?.toString()
        }

    // The profiles whose packages can be asked about right now, by serial. A paused or locked
    // profile, or a private space this launcher may not see, answers every query with nothing,
    // which says nothing about whether its apps are still installed.
    suspend fun readableProfiles(): Map<Long, UserHandle> = withContext(Dispatchers.Default) {
        launcherApps.profiles.filter(profiles::isReadable).associateBy(profiles::serialOf)
    }

    // Where the installed package lives, or null once it is uninstalled. A disabled or archived app,
    // or one that hides its launcher entry, still counts as installed and keeps its places. The
    // location moves with every update, which is how an update in another profile is told apart.
    suspend fun installedLocation(packageName: String, user: UserHandle): String? =
        withContext(Dispatchers.Default) {
            try {
                val info = launcherApps.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES, user)
                when {
                    info.flags and ApplicationInfo.FLAG_INSTALLED != 0 -> info.sourceDir.orEmpty()
                    info.isArchived -> ARCHIVED
                    else -> null
                }
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }

    // Followed only while the launcher is on screen; what happened in between is caught up through
    // catchUpPackages when it returns. Listening comes first, once both are registered, so a catch-up
    // started on it leaves no gap a change could slip through.
    fun observePackageEvents(): Flow<PackageEvent> = callbackFlow {
        val callback = packageCallback { packageNames, user, removed ->
            trySend(PackageEvent.Changed(packageNames, profiles.serialOf(user), removed))
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))

        val profileReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(PackageEvent.ProfileRemoved(removedProfileSerial(intent)))
            }
        }
        application.registerReceiver(profileReceiver, PROFILE_REMOVED, Context.RECEIVER_NOT_EXPORTED)
        send(PackageEvent.Listening)

        awaitClose {
            launcherApps.unregisterCallback(callback)
            application.unregisterReceiver(profileReceiver)
        }
    }.buffer(Channel.UNLIMITED)

    suspend fun catchUpPackages(previous: Int?): PackageCatchUp = withContext(Dispatchers.Default) {
        packageCatchUp(previous) { sequence ->
            application.packageManager.getChangedPackages(sequence)
                ?.let { ChangedSince(it.sequenceNumber, it.packageNames) }
        }
    }

    // A changed app may bring a new icon or label. Whether its icon is still cached says nothing about
    // whether an old bitmap is on screen, so every icon is asked for again.
    fun refreshPackages(packageNames: Collection<String>, userSerial: Long) {
        if (packageNames.isEmpty()) return
        packageNames.forEach(englishLabels::forget)
        iconLoader.refreshPackages(packageNames, userSerial)
    }

    private fun packageCallback(
        onEvent: (packageNames: List<String>, user: UserHandle, removed: Boolean) -> Unit,
    ): LauncherApps.Callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) =
            onEvent(listOf(packageName), user, false)

        override fun onPackageRemoved(packageName: String, user: UserHandle) =
            onEvent(listOf(packageName), user, true)

        override fun onPackageChanged(packageName: String, user: UserHandle) =
            onEvent(listOf(packageName), user, false)

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            onEvent(packageNames.asList(), user, false)

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            onEvent(packageNames.asList(), user, false)
    }

    private fun removedProfileSerial(intent: Intent): Long? {
        val user = intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle::class.java) ?: return null
        return profiles.serialOf(user).takeIf { it != UNKNOWN_SERIAL }
    }

    fun launch(
        component: ComponentRef,
        sourceBounds: Rect? = null,
        fromSearch: Boolean = false,
    ): Boolean {
        val user = profiles.userOf(component.userSerial) ?: return false
        return try {
            launcherApps.startMainActivity(component.toComponentName(), user, sourceBounds, null)
            recordLaunch(component, fromSearch)
            true
        } catch (failure: ActivityNotFoundException) {
            Log.w(TAG, "Activity $component cannot be started", failure)
            false
        } catch (denied: SecurityException) {
            Log.w(TAG, "Launching $component was denied", denied)
            false
        }
    }

    fun recordLaunch(component: ComponentRef, fromSearch: Boolean) {
        processScope.launch { launchLog.record(component, fromSearch) }
    }

    suspend fun appShortcuts(component: ComponentRef): List<AppShortcut> =
        withContext(Dispatchers.Default) {
            val user = profiles.userOf(component.userSerial) ?: return@withContext emptyList()
            val offers = launcherApps.appShortcutInfos(component.packageName, user, TAG).map { info ->
                AppShortcutOffer(
                    id = info.id,

                    label = info.shortLabel?.toString()?.takeIf { it.isNotBlank() }
                        ?: info.longLabel?.toString().orEmpty(),
                    activityClassName = info.activity?.className,
                    enabled = info.isEnabled,
                    declaredInManifest = info.isDeclaredInManifest,
                    rank = info.rank,
                )
            }
            iconMenuShortcuts(offers, component.className).map { offer ->
                AppShortcut(component = component, id = offer.id, label = offer.label)
            }
        }

    fun startShortcut(shortcut: AppShortcut, sourceBounds: Rect? = null): Boolean {
        val component = shortcut.component
        val user = profiles.userOf(component.userSerial) ?: return false
        val outcome = startShortcutOutcome(component.packageName, TAG) {
            launcherApps.startShortcut(component.packageName, shortcut.id, sourceBounds, null, user)
        }
        if (outcome == ActionOutcome.DONE) processScope.launch { launchLog.record(component) }
        return outcome == ActionOutcome.DONE
    }

    private fun badgeFor(user: UserHandle): ((Drawable) -> Drawable)? =
        if (profiles.isOwn(user)) {
            null
        } else {
            { icon -> application.packageManager.getUserBadgedIcon(icon, user) }
        }

    private fun queryApps(): List<InstalledApp> =
        launcherApps.profiles
            .flatMap { user ->
                val serial = profiles.serialOf(user)
                launcherApps.getActivityList(null, user).map { it.toInstalledApp(serial) }
            }
            .sortedWith(order)

    private fun queryActivities(): List<Pair<LauncherActivityInfo, Long>> =
        launcherApps.profiles.flatMap { user ->
            val serial = profiles.serialOf(user)
            launcherApps.getActivityList(null, user).map { info -> info to serial }
        }

    private fun activityInfo(component: ComponentRef, user: UserHandle): LauncherActivityInfo? =
        launcherApps.getActivityList(component.packageName, user)
            .firstOrNull { it.componentName.className == component.className }

    private fun appListChanges(): Flow<Unit> = callbackFlow {
        // Icons of a changed app are refreshed by the session's own package callback; its English
        // label is forgotten here too, so the list read next does not race that.
        val callback = packageCallback { packageNames, _, _ ->
            packageNames.forEach(englishLabels::forget)
            trySend(Unit)
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))

        val profileReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                iconLoader.clear()
                englishLabels.clear()
                trySend(Unit)
            }
        }
        application.registerReceiver(profileReceiver, PROFILE_CHANGES, Context.RECEIVER_NOT_EXPORTED)

        send(Unit)
        awaitClose {
            launcherApps.unregisterCallback(callback)
            application.unregisterReceiver(profileReceiver)
        }
    }

    private companion object {
        const val TAG = "LauncherApps"

        const val ARCHIVED = "archived"

        val PROFILE_CHANGES = IntentFilter().apply {
            addAction(Intent.ACTION_PROFILE_ADDED)
            addAction(Intent.ACTION_PROFILE_REMOVED)
            addAction(Intent.ACTION_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
        }

        val PROFILE_REMOVED = IntentFilter(Intent.ACTION_PROFILE_REMOVED)

        const val UNKNOWN_SERIAL = -1L
    }
}

sealed interface PackageEvent {

    data object Listening : PackageEvent

    data class Changed(val packageNames: List<String>, val userSerial: Long, val removed: Boolean) : PackageEvent

    data class ProfileRemoved(val userSerial: Long?) : PackageEvent
}

data class ShortcutApp(val app: InstalledApp, val icon: ImageBitmap?)

private fun LauncherActivityInfo.toInstalledApp(userSerial: Long): InstalledApp = InstalledApp(
    component = componentName.toComponentRef(userSerial),
    label = label.toString(),
)
