package app.cue.launcher.system.bridge

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BridgeLockMacTest {

    private val key = BridgeFixtures.load("frames.json").hex("kBle")

    private val tried = mutableListOf<String>()

    private suspend fun lock(gatt: Boolean, session: Boolean, oneShot: LockMacOutcome = LockMacOutcome.SENT) =
        lockOver(
            gatt = { gatt.also { tried += "gatt" } },
            session = { session.also { tried += "session" } },
            oneShot = { oneShot.also { tried += "one-shot" } },
        )

    @Test
    fun `a subscribed GATT link carries the lock and nothing else is tried`() = runTest {
        assertEquals(LockMacOutcome.SENT, lock(gatt = true, session = true))
        assertEquals(listOf("gatt"), tried)
    }

    @Test
    fun `without GATT the session carries it`() = runTest {
        assertEquals(LockMacOutcome.SENT, lock(gatt = false, session = true))
        assertEquals(listOf("gatt", "session"), tried)
    }

    @Test
    fun `without either a connection of its own carries it and says how that went`() = runTest {
        assertEquals(LockMacOutcome.UNREACHABLE, lock(gatt = false, session = false, LockMacOutcome.UNREACHABLE))
        assertEquals(listOf("gatt", "session", "one-shot"), tried)
    }

    @Test
    fun `only a lock-state is picked off the one-shot connection`() {
        val lockState = macFrame(BleSignal.LockState(locked = true))

        assertArrayEquals(lockState, lockStateFrame(signal(lockState)))
        assertNull(lockStateFrame(signal(macFrame(BleSignal.SyncRequest))))
        assertNull(lockStateFrame(BridgeInbound.Message(BridgeMessage.Bye(), null)))
        assertNull(lockStateFrame(BridgeInbound.Message(BridgeMessage.Signal("not base64!"), null)))
    }

    @Test
    fun `the lock-state the Mac sends behind the bye is read before the one-shot connection closes`() = runBlocking {
        val lockState = macFrame(BleSignal.LockState(locked = false))
        val fromMac = ByteArrayInputStream(
            BridgeFraming.encode(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION)) +
                BridgeFraming.encode(BridgeMessage.Signal(encodeBase64(lockState))),
        )
        val toMac = ByteArrayOutputStream()
        val drained = mutableListOf<ByteArray>()

        OneShotPush("test", log = {}) { inbound -> lockStateFrame(inbound)?.let(drained::add) }
            .push(Carrier.TCP, BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION), LOCK, transferMillis = null) {
                BridgeConnection(fromMac, toMac, InMemoryCarrier())
            }

        assertEquals(1, drained.size)
        assertArrayEquals(lockState, drained.single())
        val sent = ByteArrayInputStream(toMac.toByteArray()).let { input ->
            generateSequence { BridgeFraming.read(input) }.map { (it as BridgeInbound.Message).message }.toList()
        }
        assertEquals(listOf(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION), LOCK, BridgeMessage.Bye()), sent)
    }

    private fun macFrame(signal: BleSignal): ByteArray =
        BleFrameCodec.seal(key, BleDirection.MAC_TO_PHONE, signal.type.code, SEQ, encodeBleSignal(signal))

    private fun signal(frame: ByteArray) = BridgeInbound.Message(BridgeMessage.Signal(encodeBase64(frame)), null)

    private class InMemoryCarrier : BridgeCarrier {
        override fun setReadTimeout(millis: Int) = Unit
        override fun close() = Unit
    }

    private companion object {
        const val SEQ = 7L
        val LOCK = BridgeMessage.Signal("bG9jaw==")
    }
}
