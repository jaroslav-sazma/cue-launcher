package app.cue.launcher.system.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.ByteArrayInputStream

class WireTypeTest {

    private val fixture = BridgeFixtures.load("messages.json")

    @TestFactory
    fun `every message is logged under its wire type`(): List<DynamicTest> = fixture.objects("examples").map { v ->
        DynamicTest.dynamicTest(v.str("type")) {
            val inbound = BridgeFraming.read(ByteArrayInputStream(v.hex("frame"))) as BridgeInbound.Message

            assertEquals(v.str("type"), wireType(inbound.message))
        }
    }

    @Test
    fun `frame types are logged under their spec names`() {
        assertEquals("notif-posted", BleFrameType.NOTIF_POSTED.wireName)
        assertEquals("clipboard-small", BleFrameType.CLIPBOARD_SMALL.wireName)
        assertEquals("sync-request", BleFrameType.SYNC_REQUEST.wireName)
        assertEquals("reply-error", BleFrameType.REPLY_ERROR.wireName)
        assertEquals("ping", BleFrameType.PING.wireName)
        assertEquals("clipboard-pending", BleFrameType.CLIPBOARD_PENDING.wireName)
        assertEquals("reply-ok", BleFrameType.REPLY_OK.wireName)
        assertEquals("lock-state", BleFrameType.LOCK_STATE.wireName)
        assertEquals("lock", BleFrameType.LOCK.wireName)
    }
}
