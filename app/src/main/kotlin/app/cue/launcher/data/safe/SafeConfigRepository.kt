package app.cue.launcher.data.safe

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@SingleIn(AppScope::class)
@Inject
class SafeConfigRepository(private val store: DataStore<SafeSettings>) {

    val settings: Flow<SafeSettings> = store.data

    suspend fun current(): SafeSettings = store.data.first()

    // The stored key stays: unlocking compares its vault_id with the file's header, so the same vault
    // chosen again under a new URI opens without the password, and another vault asks for it. The same
    // URI chosen again is the same file, so it keeps its written_at and only takes a fresh name.
    suspend fun choose(uri: String, displayName: String) {
        store.updateData { current ->
            val same = current.container?.takeIf { it.uri == uri }
            val container = same?.copy(displayName = displayName.ifBlank { same.displayName })
                ?: SafeContainer(uri, displayName)
            current.copy(container = container)
        }
    }

    suspend fun storeKey(key: SafeStoredKey) {
        store.updateData { it.copy(key = key) }
    }

    suspend fun clearKey() {
        store.updateData { it.copy(key = null) }
    }

    suspend fun setWrittenAt(writtenAt: Long) {
        store.updateData { it.copy(container = it.container?.copy(writtenAt = writtenAt)) }
    }

    suspend fun setLockAfterMinutes(minutes: Int) {
        store.updateData { it.copy(lockAfterMinutes = minutes.coerceIn(SAFE_LOCK_AFTER_MINUTES)) }
    }

    suspend fun setOpenInFiles(enabled: Boolean) {
        store.updateData { it.copy(openInFiles = enabled) }
    }

    // A forgotten vault leaves nothing behind, its lock time and its switch included.
    suspend fun forget() {
        store.updateData { SafeSettings() }
    }
}
