package app.cue.launcher.data.icons

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.layout.StoredPackage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
@Inject
class IconOverrideRepository(private val dao: IconOverrideDao) {

    fun observeOverrides(): Flow<Set<ComponentRef>> =
        dao.observeOverrides().map { rows -> rows.mapTo(mutableSetOf(), IconOverrideEntity::toComponentRef) }

    suspend fun setOverride(component: ComponentRef, native: Boolean) {
        if (native) {
            dao.insert(component.toIconOverrideEntity())
        } else {
            dao.delete(component.packageName, component.className, component.userSerial)
        }
    }

    suspend fun overrides(): Set<ComponentRef> =
        dao.overrides().mapTo(mutableSetOf(), IconOverrideEntity::toComponentRef)

    suspend fun replaceAll(overrides: Collection<ComponentRef>) {
        dao.replaceAll(overrides.map(ComponentRef::toIconOverrideEntity))
    }

    suspend fun removePackage(packageName: String, userSerial: Long): Int =
        dao.deleteByPackage(packageName, userSerial)

    suspend fun storedProfiles(): List<Long> = dao.profiles()

    suspend fun storedPackages(): List<StoredPackage> = dao.packages()

    suspend fun removeProfile(userSerial: Long): Int = dao.deleteByProfile(userSerial)
}
