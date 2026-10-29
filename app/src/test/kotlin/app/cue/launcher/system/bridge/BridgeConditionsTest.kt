package app.cue.launcher.system.bridge

import app.cue.launcher.data.bridge.BridgePairingRecord
import app.cue.launcher.data.bridge.BridgeSealedKey
import app.cue.launcher.data.bridge.BridgeSettings
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.settings.MemorySettingsStore
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.engine.ConditionOutcome
import app.cue.launcher.engine.MacBridgeState
import app.cue.launcher.engine.MacLockState
import app.cue.launcher.engine.SystemState
import app.cue.launcher.engine.stateConditionOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BridgeConditionsTest {

    private val endpoint = MutableStateFlow<BridgeEndpoint?>(null)

    private var presenceBuilt = false

    private val presence = lazy {
        presenceBuilt = true
        object : BridgePresence {
            override fun endpoint(): Flow<BridgeEndpoint?> = endpoint
        }
    }

    private val locked = MutableStateFlow<Boolean?>(null)

    private var lockBuilt = false

    private val macLock = lazy {
        lockBuilt = true
        ReportedMacLock { locked }
    }

    @Test
    fun `with the bridge off the Mac is off and the presence is never built`() = runTest {
        val state = conditions(BridgeSettings()).observe().first()

        assertEquals(MacBridgeState.OFF, state.macBridge)
        assertFalse(presenceBuilt)
    }

    @Test
    fun `with the bridge off the lock state is unreported and never built`() = runTest {
        locked.value = true

        val state = conditions(BridgeSettings()).observe().first()

        assertEquals(MacLockState.UNREPORTED, state.macLock)
        assertBothFormsUnknown(state)
        assertFalse(lockBuilt)
    }

    @Test
    fun `a present Mac reads as locked or unlocked as it reports`() = runTest {
        val readings = conditions(PAIRED).observe()
        endpoint.value = BridgeEndpoint("192.168.1.20", PORT)

        assertEquals(MacLockState.UNREPORTED, readings.first().macLock)
        locked.value = true
        assertEquals(MacLockState.LOCKED, readings.first().macLock)
        locked.value = false
        assertEquals(MacLockState.UNLOCKED, readings.first().macLock)
    }

    @Test
    fun `a Mac away counts as neither locked nor unlocked even with a lock-state left over`() = runTest {
        locked.value = false

        val state = conditions(PAIRED).observe().first()

        assertEquals(MacBridgeState.AWAY, state.macBridge)
        assertEquals(MacLockState.UNREPORTED, state.macLock)
        assertBothFormsUnknown(state)
    }

    @Test
    fun `the tile reads the lock state the condition does, presence included`() = runTest {
        locked.value = true
        val tile = conditions(PAIRED).observeLocked()

        assertNull(tile.first(), "a lock-state left over from a Mac that is gone")
        endpoint.value = BridgeEndpoint("192.168.1.20", PORT)
        assertEquals(true, tile.first())
        locked.value = false
        assertEquals(false, tile.first())
        endpoint.value = null
        assertNull(tile.first(), "the presence ended, as a dev endpoint taken away")
    }

    @Test
    fun `with the bridge off the tile knows no lock state and builds nothing`() = runTest {
        locked.value = false

        assertNull(conditions(BridgeSettings()).observeLocked().first())
        assertFalse(lockBuilt)
        assertFalse(presenceBuilt)
    }

    @Test
    fun `a bridge turned on but not paired is off as well and builds nothing`() = runTest {
        val state = conditions(BridgeSettings(enabled = true)).observe().first()

        assertEquals(MacBridgeState.OFF, state.macBridge)
        assertFalse(presenceBuilt)
    }

    @Test
    fun `a paired Mac reads as away until the presence has it nearby`() = runTest {
        val readings = conditions(PAIRED).observe()

        assertEquals(MacBridgeState.AWAY, readings.first().macBridge)
        endpoint.value = BridgeEndpoint("192.168.1.20", PORT)
        assertEquals(MacBridgeState.NEARBY, readings.first().macBridge)
        assertTrue(presenceBuilt)
    }

    @Test
    fun `turning the bridge off drops a nearby Mac to off`() = runTest {
        val store = MemorySettingsStore(LauncherSettings(bridge = PAIRED))
        endpoint.value = BridgeEndpoint("192.168.1.20", PORT)
        val seen = mutableListOf<MacBridgeState?>()

        val readings = BridgeConditions(SettingsRepository(store), presence, macLock).observe()

        val collecting = launch { readings.take(2).toList().mapTo(seen) { it.macBridge } }
        runCurrent()
        store.updateData { it.copy(bridge = BridgeSettings()) }
        collecting.join()

        assertEquals(listOf(MacBridgeState.NEARBY, MacBridgeState.OFF), seen)
    }

    private fun assertBothFormsUnknown(state: SystemState) {
        assertEquals(ConditionOutcome.UNKNOWN, stateConditionOutcome(Condition.MacLocked(), state))
        assertEquals(ConditionOutcome.UNKNOWN, stateConditionOutcome(Condition.MacLocked(negate = true), state))
    }

    private fun conditions(bridge: BridgeSettings) =
        BridgeConditions(
            SettingsRepository(MemorySettingsStore(LauncherSettings(bridge = bridge))),
            presence,
            macLock,
        )

    private companion object {
        const val PORT = 7339

        val PAIRED = BridgeSettings(
            enabled = true,
            pairing = BridgePairingRecord(
                macName = "Example-Mac",
                host = "192.168.1.20",
                port = PORT,
                macFingerprint = "00",
                bleKey = BridgeSealedKey("", ""),
            ),
        )
    }
}
