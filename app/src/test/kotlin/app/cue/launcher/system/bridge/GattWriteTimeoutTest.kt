package app.cue.launcher.system.bridge

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GattWriteTimeoutTest {

    @Test
    fun `a write the stack never answers gives up after the timeout`() = runTest {
        val written = awaitGattWrite(CompletableDeferred(), GATT_WRITE_TIMEOUT_MS)

        assertEquals(GattWrite.TIMED_OUT, written)
        assertEquals(GATT_WRITE_TIMEOUT_MS, testScheduler.currentTime)
    }

    @Test
    fun `a write the stack answers in time is sent or failed by its status`() = runTest {
        assertEquals(GattWrite.SENT, awaitGattWrite(CompletableDeferred(true), GATT_WRITE_TIMEOUT_MS))
        assertEquals(GattWrite.FAILED, awaitGattWrite(CompletableDeferred(false), GATT_WRITE_TIMEOUT_MS))
        assertEquals(0L, testScheduler.currentTime)
    }
}
