package app.cue.launcher.system.bridge

import app.cue.launcher.data.bridge.BridgePairingRecord
import app.cue.launcher.data.bridge.BridgeSealedKey
import app.cue.launcher.data.bridge.BridgeSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MacLockCacheTest {

    private val cache = MacLockCache()

    @Test
    fun `the state is unknown until the first lock-state and known from it on`() {
        assertNull(cache.locked)

        assertTrue(cache.received(KEY, 5, locked = true))

        assertEquals(true, cache.locked)
    }

    @Test
    fun `a newer lock-state replaces the state`() {
        cache.received(KEY, 5, locked = true)

        assertTrue(cache.received(KEY, 9, locked = false))

        assertEquals(false, cache.locked)
    }

    @Test
    fun `a lock-state sealed before the last applied one changes nothing`() {
        cache.received(KEY, 9, locked = false)

        assertFalse(cache.received(KEY, 8, locked = true))
        assertFalse(cache.received(KEY, 9, locked = true))

        assertEquals(false, cache.locked)
    }

    @Test
    fun `a new key starts the order over`() {
        cache.received(KEY, 900, locked = false)

        assertTrue(cache.received(OTHER_KEY, 0, locked = true))

        assertEquals(true, cache.locked)
    }

    @Test
    fun `forgetting clears the state but keeps the order`() {
        cache.received(KEY, 9, locked = true)

        cache.forget()

        assertNull(cache.locked)
        assertFalse(cache.received(KEY, 8, locked = false), "a frame from before is no newer for the forgetting")
        assertNull(cache.locked)
        assertTrue(cache.received(KEY, 10, locked = false))
        assertEquals(false, cache.locked)
    }

    @Test
    fun `the state is kept only while the paired Mac is present`() {
        assertTrue(macLockKnowable(PAIRED, FINGERPRINT, MacPresence.NEARBY))
        assertTrue(macLockKnowable(PAIRED, FINGERPRINT, MacPresence.LINKED))
        assertFalse(macLockKnowable(PAIRED, FINGERPRINT, MacPresence.ABSENT))
    }

    @Test
    fun `a lock-state for a pairing that is gone is not kept`() {
        assertFalse(macLockKnowable(PAIRED, "ff", MacPresence.LINKED), "another pairing's")
        assertFalse(macLockKnowable(PAIRED.copy(pairing = null), FINGERPRINT, MacPresence.LINKED), "unpaired")
        assertFalse(macLockKnowable(PAIRED.copy(enabled = false), FINGERPRINT, MacPresence.LINKED), "bridge off")
    }

    private companion object {
        const val KEY = "key-a"
        const val OTHER_KEY = "key-b"
        const val FINGERPRINT = "00"

        val PAIRED = BridgeSettings(
            enabled = true,
            pairing = BridgePairingRecord(
                macName = "Example-Mac",
                host = "192.168.1.20",
                port = 7339,
                macFingerprint = FINGERPRINT,
                bleKey = BridgeSealedKey("", ""),
            ),
        )
    }
}
