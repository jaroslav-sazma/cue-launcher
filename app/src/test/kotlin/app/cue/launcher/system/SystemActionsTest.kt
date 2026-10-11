package app.cue.launcher.system

import android.media.AudioManager
import app.cue.launcher.data.scenario.RingerModeState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SystemActionsTest {

    @Test
    fun `the torch is the back camera with a flash`() {
        val cameras = listOf(
            TorchCamera(id = "1", hasFlash = true, facingBack = false),
            TorchCamera(id = "0", hasFlash = true, facingBack = true),
        )

        assertEquals("0", torchCameraId(cameras))
    }

    @Test
    fun `a camera without a flash is skipped`() {
        val cameras = listOf(
            TorchCamera(id = "0", hasFlash = false, facingBack = true),
            TorchCamera(id = "2", hasFlash = true, facingBack = true),
        )

        assertEquals("2", torchCameraId(cameras))
    }

    @Test
    fun `without a back flash it takes the first one with a flash`() {
        val cameras = listOf(
            TorchCamera(id = "1", hasFlash = true, facingBack = false),
            TorchCamera(id = "3", hasFlash = true, facingBack = false),
        )

        assertEquals("1", torchCameraId(cameras))
    }

    @Test
    fun `without a flash there is no torch`() {
        assertNull(torchCameraId(emptyList()))
        assertNull(torchCameraId(listOf(TorchCamera(id = "0", hasFlash = false, facingBack = true))))
    }

    @Test
    fun `the ringer mode has a value for the system API`() {
        assertEquals(AudioManager.RINGER_MODE_SILENT, ringerModeValue(RingerModeState.SILENT))
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, ringerModeValue(RingerModeState.VIBRATE))
        assertEquals(AudioManager.RINGER_MODE_NORMAL, ringerModeValue(RingerModeState.NORMAL))
    }

    @Test
    fun `do not disturb is not a ringer mode`() {
        assertNull(ringerModeValue(RingerModeState.DND))
    }
}
