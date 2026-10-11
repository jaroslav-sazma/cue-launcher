package app.cue.launcher.data.settings

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class MemorySettingsStore(initial: LauncherSettings = LauncherSettings()) : DataStore<LauncherSettings> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<LauncherSettings> = state

    override suspend fun updateData(
        transform: suspend (t: LauncherSettings) -> LauncherSettings,
    ): LauncherSettings = transform(state.value).also { state.value = it }
}
