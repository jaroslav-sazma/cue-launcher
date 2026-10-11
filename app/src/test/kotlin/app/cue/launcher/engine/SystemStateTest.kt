package app.cue.launcher.engine

import app.cue.launcher.data.scenario.RingerModeState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SystemStateTest {

    @Test
    fun `merging fills in fields from both readings`() {
        val merged = SystemState(charging = true).mergedWith(SystemState(audioOutput = true))

        assertEquals(true, merged.charging)
        assertEquals(true, merged.audioOutput)
    }

    @Test
    fun `an unset field overwrites nothing`() {
        val merged = SystemState(charging = true, ringerMode = RingerModeState.SILENT)
            .mergedWith(SystemState(audioOutput = false))

        assertEquals(true, merged.charging)
        assertEquals(RingerModeState.SILENT, merged.ringerMode)
    }

    @Test
    fun `a newer measurement wins`() {
        val merged = SystemState(charging = true).mergedWith(SystemState(charging = false))

        assertEquals(false, merged.charging)
    }

    @Test
    fun `an empty snapshot has no state`() {
        val state = SystemState()

        assertNull(state.charging)
        assertNull(state.bluetooth)
        assertNull(state.wifi)
        assertNull(state.doNotDisturb)
    }

    @Test
    fun `bt without an address watches any device`() {
        assertTrue(BluetoothState(setOf("AA:BB:CC:DD:EE:FF")).isConnected(null))
        assertFalse(BluetoothState().isConnected(null))
    }

    @Test
    fun `the bt address ignores case`() {
        val state = BluetoothState(setOf("AA:BB:CC:DD:EE:FF"))

        assertTrue(state.isConnected("aa:bb:cc:dd:ee:ff"))
        assertFalse(state.isConnected("AA:BB:CC:DD:EE:00"))
    }
}
