package app.cue.launcher.system.bridge

import androidx.datastore.core.DataStore
import app.cue.launcher.data.bridge.BridgeConfigRepository
import app.cue.launcher.data.bridge.BridgeKeySealer
import app.cue.launcher.data.bridge.BridgePairingRecord
import app.cue.launcher.data.bridge.BridgeSealedKey
import app.cue.launcher.data.bridge.BridgeSequences
import app.cue.launcher.data.bridge.BridgeSettings
import app.cue.launcher.data.bridge.MemorySequenceStore
import app.cue.launcher.data.bridge.OpenedPairing
import app.cue.launcher.data.settings.LauncherSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BridgeMacLockTest {

    // A read takes the settings as they are when it starts and hands them over once let through, as
    // a DataStore read still in flight while an unpair wipes them would.
    private class HeldStore(initial: LauncherSettings) : DataStore<LauncherSettings> {

        private val state = MutableStateFlow(initial)

        var hold: CompletableDeferred<Unit>? = null

        override val data: Flow<LauncherSettings> = flow {
            val read = state.value
            hold?.await()
            emit(read)
        }

        override suspend fun updateData(
            transform: suspend (t: LauncherSettings) -> LauncherSettings,
        ): LauncherSettings = transform(state.value).also { state.value = it }
    }

    private object PlainSealer : BridgeKeySealer {
        override fun seal(plain: ByteArray) = BridgeSealedKey("", "")
        override fun open(sealed: BridgeSealedKey): ByteArray? = null
        override fun destroy() = Unit
    }

    private val store = HeldStore(LauncherSettings(bridge = PAIRED))

    private val nearby = BridgeNearby().apply { publish(MacPresence.LINKED) }

    private val macLock = BridgeMacLock(
        BridgeConfigRepository(store, PlainSealer, BridgeSequences(MemorySequenceStore())),
        nearby,
    )

    private val pairing = OpenedPairing("Example-Mac", "192.168.1.20", PORT, FINGERPRINT, ByteArray(32))

    @Test
    fun `a lock-state from the paired Mac nearby is applied`() = runTest {
        assertTrue(macLock.record(pairing, seq = 3, locked = true))

        assertEquals(true, macLock.locked.value)
    }

    @Test
    fun `a lock-state whose settings were read before an unpair stays forgotten`() = runTest {
        val held = CompletableDeferred<Unit>()
        store.hold = held

        val receiving = async { macLock.record(pairing, seq = 3, locked = true) }
        runCurrent()
        store.updateData { it.copy(bridge = BridgeSettings()) }
        macLock.forget()
        held.complete(Unit)

        assertFalse(receiving.await())
        assertNull(macLock.locked.value)
    }

    private companion object {
        const val PORT = 7339
        const val FINGERPRINT = "00"

        val PAIRED = BridgeSettings(
            enabled = true,
            pairing = BridgePairingRecord(
                macName = "Example-Mac",
                host = "192.168.1.20",
                port = PORT,
                macFingerprint = FINGERPRINT,
                bleKey = BridgeSealedKey("", ""),
            ),
        )
    }
}
