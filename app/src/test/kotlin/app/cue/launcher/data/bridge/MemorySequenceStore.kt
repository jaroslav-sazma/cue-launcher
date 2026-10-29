package app.cue.launcher.data.bridge

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

// What it holds is what a restart would find on disk.
internal class MemorySequenceStore(initial: BridgeSequenceFile = BridgeSequenceFile()) : DataStore<BridgeSequenceFile> {
    private val state = MutableStateFlow(initial)

    var writes = 0
        private set

    val stored: BridgeSequenceState? get() = state.value.state

    override val data: Flow<BridgeSequenceFile> = state

    override suspend fun updateData(
        transform: suspend (t: BridgeSequenceFile) -> BridgeSequenceFile,
    ): BridgeSequenceFile = transform(state.value).also {
        writes++
        state.value = it
    }
}
