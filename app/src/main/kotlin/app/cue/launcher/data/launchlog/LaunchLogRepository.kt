package app.cue.launcher.data.launchlog

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.layout.StoredPackage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
@Inject
class LaunchLogRepository(private val dao: AppLaunchDao) {

    fun observeLaunchStats(): Flow<Map<ComponentRef, LaunchStats>> =
        dao.observeLaunches().map(::launchStatsOf)

    suspend fun record(
        component: ComponentRef,
        fromSearch: Boolean = false,
        at: Long = System.currentTimeMillis(),
    ) {
        dao.record(
            packageName = component.packageName,
            activityClassName = component.className,
            userSerial = component.userSerial,
            launchedAt = at,
            searchHit = if (fromSearch) 1 else 0,
        )
    }

    suspend fun removePackage(packageName: String, userSerial: Long): Int =
        dao.deleteByPackage(packageName, userSerial)

    suspend fun storedProfiles(): List<Long> = dao.profiles()

    suspend fun storedPackages(): List<StoredPackage> = dao.packages()

    suspend fun removeProfile(userSerial: Long): Int = dao.deleteByProfile(userSerial)

    suspend fun clear() {
        dao.deleteAll()
    }
}
