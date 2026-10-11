package app.cue.launcher.system

import android.util.Log
import app.cue.launcher.data.drawer.HiddenAppRepository
import app.cue.launcher.data.icons.IconOverrideRepository
import app.cue.launcher.data.launchlog.LaunchLogRepository
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.data.layout.LauncherLayoutRepository
import app.cue.launcher.data.layout.StoredPackage
import app.cue.launcher.data.layout.dockSeedPlacements
import app.cue.launcher.data.settings.SettingsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

@Suppress("LongParameterList")
@SingleIn(AppScope::class)
@Inject
class LayoutMaintenance(
    private val launcherApps: LauncherAppsRepository,
    private val defaultApps: DefaultApps,
    private val layout: LauncherLayoutRepository,
    private val launchLog: LaunchLogRepository,
    private val iconOverrides: IconOverrideRepository,
    private val hiddenApps: HiddenAppRepository,
    private val settings: SettingsRepository,
) {

    private val catchingUp = Mutex()

    // Guarded by catchingUp, and only moved once a catch-up has finished: a session cut short must
    // not leave the packages it never got to behind the mark.
    private var mark: Int? = null

    // Where each package of another profile was installed at the last catch-up. getChangedPackages
    // cannot see those profiles, so an update there shows only as a package that moved.
    private val locations = ConcurrentHashMap<StoredPackage, String>()

    suspend fun seedDock() {
        if (settings.settings.first().dockSeeded) return
        if (layout.isEmpty()) {
            val placements = dockSeedPlacements(defaultApps.dockDefaults())
            if (placements.isEmpty()) {
                Log.w(TAG, "Dock seed: none of the default apps were found, will retry")
                return
            }
            placements.forEach { (position, component) ->
                layout.placeShortcut(LayoutContainer.DOCK, position, component)
            }
            Log.i(TAG, "Dock seed: ${placements.size} shortcuts")
        }
        settings.setDockSeeded(true)
    }

    // Runs while the launcher is on screen. Nothing listens for packages in the background, so the
    // first thing, as soon as the callback is registered, is to catch up on what changed while it
    // was away. Every step checks before it removes anything, so meeting a change again that the
    // live callback also reports is harmless.
    suspend fun runForeground() {
        launcherApps.observePackageEvents().collect(::apply)
    }

    private suspend fun catchUp() = catchingUp.withLock {
        val catchUp = launcherApps.catchUpPackages(mark)
        dropRemovedProfiles()
        val ownSerial = launcherApps.profiles.ownSerial
        val readable = launcherApps.readableProfiles()
        val moved = mutableListOf<StoredPackage>()
        packagesToCheck(storedPackages(), catchUp.changed, ownSerial).forEach { stored ->
            val user = readable[stored.userSerial] ?: return@forEach
            val location = launcherApps.installedLocation(stored.packageName, user)
            if (location == null) {
                drop(stored.packageName, stored.userSerial)
            } else if (stored.userSerial != ownSerial) {
                if (locations.put(stored, location).let { it != null && it != location }) moved += stored
            }
        }
        catchUp.changed?.let { launcherApps.refreshPackages(it, ownSerial) }
        moved.groupBy(StoredPackage::userSerial).forEach { (serial, packages) ->
            launcherApps.refreshPackages(packages.map(StoredPackage::packageName), serial)
        }
        mark = catchUp.mark
    }

    private suspend fun apply(event: PackageEvent) {
        when (event) {
            PackageEvent.Listening -> catchUp()
            is PackageEvent.Changed -> {
                if (event.removed) {
                    val user = launcherApps.readableProfiles()[event.userSerial]
                    event.packageNames
                        .filter { user != null && launcherApps.installedLocation(it, user) == null }
                        .forEach { drop(it, event.userSerial) }
                }
                launcherApps.refreshPackages(event.packageNames, event.userSerial)
            }
            is PackageEvent.ProfileRemoved -> event.userSerial?.let { dropProfile(it) } ?: dropRemovedProfiles()
        }
    }

    private suspend fun storedPackages(): List<StoredPackage> =
        layout.storedPackages() + launchLog.storedPackages() +
            iconOverrides.storedPackages() + hiddenApps.storedPackages()

    private suspend fun drop(packageName: String, userSerial: Long) {
        locations.remove(StoredPackage(packageName, userSerial))
        val removed = layout.removePackage(packageName, userSerial)

        launchLog.removePackage(packageName, userSerial)

        iconOverrides.removePackage(packageName, userSerial)

        hiddenApps.removePackage(packageName, userSerial)
        if (removed > 0) {
            Log.i(TAG, "Uninstall of $packageName: $removed positions released")
        }
    }

    private suspend fun dropProfile(userSerial: Long) {
        val removed = layout.removeProfile(userSerial)
        launchLog.removeProfile(userSerial)
        iconOverrides.removeProfile(userSerial)
        hiddenApps.removeProfile(userSerial)
        if (removed > 0) {
            Log.i(TAG, "Removed profile $userSerial: $removed positions released")
        }
    }

    private suspend fun dropRemovedProfiles() {
        (
            layout.storedProfiles() + launchLog.storedProfiles() +
                iconOverrides.storedProfiles() + hiddenApps.storedProfiles()
            )
            .distinct()
            .filter { launcherApps.profiles.userOf(it) == null }
            .forEach { dropProfile(it) }
    }

    private companion object {
        const val TAG = "LayoutMaintenance"
    }
}
