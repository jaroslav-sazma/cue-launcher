package app.cue.launcher.data.drawer

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.layout.StoredPackage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
@Inject
class HiddenAppRepository(private val dao: HiddenAppDao) {

    fun observeHidden(): Flow<Set<ComponentRef>> =
        dao.observeHidden().map { rows -> rows.mapTo(mutableSetOf(), HiddenAppEntity::toComponentRef) }

    suspend fun setHidden(component: ComponentRef, hidden: Boolean) {
        if (hidden) {
            dao.insert(component.toHiddenAppEntity())
        } else {
            dao.delete(component.packageName, component.className, component.userSerial)
        }
    }

    suspend fun hidden(): Set<ComponentRef> =
        dao.hidden().mapTo(mutableSetOf(), HiddenAppEntity::toComponentRef)

    suspend fun replaceAll(hidden: Collection<ComponentRef>) {
        dao.replaceAll(hidden.map(ComponentRef::toHiddenAppEntity))
    }

    suspend fun removePackage(packageName: String, userSerial: Long): Int =
        dao.deleteByPackage(packageName, userSerial)

    suspend fun storedProfiles(): List<Long> = dao.profiles()

    suspend fun storedPackages(): List<StoredPackage> = dao.packages()

    suspend fun removeProfile(userSerial: Long): Int = dao.deleteByProfile(userSerial)
}
