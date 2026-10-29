package app.cue.launcher.system.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionStagesTest {

    private fun inbound(message: BridgeMessage) = BridgeInbound.Message(message, null)

    @Test
    fun `the Mac's hello of the same version establishes the session`() {
        assertEquals(SessionOpening.Established, sessionOpening(inbound(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION))))
    }

    @Test
    fun `another version answers bye and stops for good`() {
        val opening = sessionOpening(inbound(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION + 1)))

        assertEquals(
            SessionOpening.Refused(
                SessionEnd(BridgeLinkState.VERSION_MISMATCH, final = true),
                BridgeMessage.Bye(BYE_VERSION_MISMATCH),
            ),
            opening,
        )
    }

    @Test
    fun `anything but hello first ends the attempt without a reply`() {
        val unreachable = SessionOpening.Refused(SessionEnd(BridgeLinkState.UNREACHABLE))

        assertEquals(unreachable, sessionOpening(null))
        assertEquals(unreachable, sessionOpening(BridgeInbound.Unknown("future-type")))
        assertEquals(unreachable, sessionOpening(inbound(BridgeMessage.SyncRequest)))
        assertEquals(unreachable, sessionOpening(inbound(BridgeMessage.Bye(BYE_REPLACED))))
    }

    @Test
    fun `the Mac's requests go to the mirror and its signals to the receiver`() {
        assertEquals(SessionStep.Sync, sessionStep(BridgeMessage.SyncRequest))
        assertEquals(SessionStep.Icon(HASH), sessionStep(BridgeMessage.IconGet(HASH)))
        assertEquals(SessionStep.Signal("AQID"), sessionStep(BridgeMessage.Signal("AQID")))
        assertEquals(SessionStep.Reply("key", "text"), sessionStep(BridgeMessage.ReplyText("key", "text")))
    }

    @Test
    fun `a clipboard-set goes to the clipboard whether or not it is announced`() {
        assertEquals(
            SessionStep.Clipboard("text", sensitive = true),
            sessionStep(BridgeMessage.ClipboardSet("text", sensitive = true)),
        )
        assertEquals(
            SessionStep.Clipboard("text", sensitive = false),
            sessionStep(BridgeMessage.ClipboardSet("text", sensitive = false, announce = true)),
        )
    }

    @Test
    fun `a clipboard-set keeps the age the Mac gave it`() {
        assertEquals(
            SessionStep.Clipboard("text", sensitive = false, ageMs = 95_000),
            sessionStep(BridgeMessage.ClipboardSet("text", sensitive = false, ageMs = 95_000)),
        )
    }

    @Test
    fun `a message the Mac never sends on a session is a protocol error`() {
        listOf(
            BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION),
            BridgeMessage.PairRequest("commit"),
            BridgeMessage.PairResponse("pk"),
            BridgeMessage.PairReveal("pk"),
            BridgeMessage.PairConfirm("proof"),
            BridgeMessage.PairAbort(ABORT_CANCELLED),
            BridgeMessage.ActiveSet(emptyList()),
            BridgeMessage.IconData(HASH, 1),
        ).forEach { message ->
            assertEquals(SessionStep.ProtocolError, sessionStep(message)) { wireType(message) }
        }
    }

    @Test
    fun `bye reasons decide whether the link comes back by itself`() {
        assertEquals(SessionEnd(BridgeLinkState.VERSION_MISMATCH, final = true), byeEnd(BYE_VERSION_MISMATCH))
        assertEquals(SessionEnd(BridgeLinkState.REPLACED, onSummon = true), byeEnd(BYE_REPLACED))
        assertEquals(SessionEnd(BridgeLinkState.UNREACHABLE), byeEnd(null))
        assertEquals(SessionEnd(BridgeLinkState.UNREACHABLE), byeEnd("going-to-sleep"))
        assertEquals(SessionStep.End(byeEnd(BYE_REPLACED)), sessionStep(BridgeMessage.Bye(BYE_REPLACED)))
    }

    @Test
    fun `the backoff doubles up to a minute`() {
        val backoff = SessionBackoff()
        val drop = SessionEnd(BridgeLinkState.UNREACHABLE)

        val waits = List(9) { backoff.after(drop) }

        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L, 60_000L)
                .map(SessionRetry::After),
            waits,
        )
    }

    @Test
    fun `a stable session starts the backoff over`() {
        val backoff = SessionBackoff()
        repeat(4) { backoff.after(SessionEnd(BridgeLinkState.UNREACHABLE)) }

        assertEquals(SessionRetry.After(1_000L), backoff.after(SessionEnd(BridgeLinkState.UNREACHABLE, stable = true)))
        assertEquals(SessionRetry.After(2_000L), backoff.after(SessionEnd(BridgeLinkState.UNREACHABLE)))
    }

    @Test
    fun `a final end is never retried`() {
        assertEquals(SessionRetry.Never, SessionBackoff().after(byeEnd(BYE_VERSION_MISMATCH)))
    }

    @Test
    fun `a replaced link waits for the next summon and then starts the backoff over`() {
        val backoff = SessionBackoff()
        repeat(4) { backoff.after(SessionEnd(BridgeLinkState.UNREACHABLE)) }

        assertEquals(SessionRetry.OnSummon, backoff.after(byeEnd(BYE_REPLACED)))
        assertEquals(SessionRetry.After(1_000L), backoff.after(SessionEnd(BridgeLinkState.UNREACHABLE)))
    }

    @Test
    fun `a session counts as stable once it outlasted the longest backoff`() {
        val longest = SESSION_MAX_BACKOFF_MS * 1_000_000

        assertFalse(sessionStable(longest - 1))
        assertTrue(sessionStable(longest))
    }

    private companion object {
        const val HASH = "4c4b6a3be1314ab86138bef4314dde022e600960d8689a2c8f8631802d20dab6"
    }
}
