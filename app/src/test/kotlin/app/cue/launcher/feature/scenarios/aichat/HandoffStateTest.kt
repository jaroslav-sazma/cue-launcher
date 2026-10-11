package app.cue.launcher.feature.scenarios.aichat

import androidx.compose.runtime.saveable.SaverScope
import app.cue.launcher.data.aichat.AiChatOffer
import app.cue.launcher.data.aichat.AiChatShare
import app.cue.launcher.system.aichat.HandoffOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HandoffStateTest {

    private class Harness(
        scope: CoroutineScope,
        outcome: HandoffOutcome? = null,
        val answer: HandoffOutcome = SENT,
        share: AiChatShare = AiChatShare.ALL,
        offer: AiChatOffer = AiChatOffer.ALL,
    ) {
        var reads = 0
        val readShares = mutableListOf<AiChatShare>()
        val readOffers = mutableListOf<AiChatOffer>()
        val sent = mutableListOf<String>()
        val persisted = mutableListOf<AiChatShare>()
        var sentCalls = 0
        var clears = 0
        var gate: CompletableDeferred<Unit>? = null

        val state = HandoffState(
            readBrief = { chosen, available ->
                reads++
                readShares += chosen
                readOffers += available
                gate?.await()
                "brief $reads"
            },
            sendBrief = { brief -> sent += brief; answer },
            clear = { clears++ },
            scope = scope,
            outcome = outcome,
            share = share,
            offer = offer,
            persistShare = { persisted += it },
            onSent = { sentCalls++ },
        )
    }

    @Test
    fun `starting reads and sends nothing until the user asks`() = runTest {
        val harness = Harness(this)

        harness.state.start(AiChatOffer.ALL)
        advanceUntilIdle()

        assertEquals(0, harness.reads)
        assertNull(harness.state.outcome)
        assertFalse(harness.state.sending)
    }

    @Test
    fun `sending reads the brief for the chosen parts and sends it`() = runTest {
        val partial = AiChatShare(bluetooth = false)
        val harness = Harness(this, share = partial)

        harness.state.send()
        assertTrue(harness.state.sending)
        advanceUntilIdle()

        assertEquals(SENT, harness.state.outcome)
        assertFalse(harness.state.sending)
        assertEquals(listOf(partial), harness.readShares)
        assertEquals(listOf("brief 1"), harness.sent)
        assertEquals(1, harness.sentCalls)
    }

    @Test
    fun `a send no app took leaves the hand-off unrecorded`() = runTest {
        val harness = Harness(this, answer = HandoffOutcome.NO_CHAT_APP)

        harness.state.send()
        advanceUntilIdle()

        assertEquals(HandoffOutcome.NO_CHAT_APP, harness.state.outcome)
        assertEquals(0, harness.sentCalls)
    }

    @Test
    fun `leaving mid-read sends nothing and does not send on return`() = runTest {
        val harness = Harness(this).apply { gate = CompletableDeferred() }

        harness.state.send()
        runCurrent()
        harness.state.stop()
        harness.gate = null
        advanceUntilIdle()

        assertNull(harness.state.outcome)
        assertFalse(harness.state.sending)
        assertEquals(emptyList<String>(), harness.sent)
        assertEquals(1, harness.clears)

        harness.state.start(AiChatOffer.ALL)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), harness.sent)
        assertEquals(0, harness.sentCalls)
    }

    @Test
    fun `a sent brief is not sent again on return`() = runTest {
        val harness = Harness(this, outcome = SENT)

        harness.state.start(AiChatOffer.ALL)
        advanceUntilIdle()

        assertEquals(0, harness.reads)
    }

    @Test
    fun `after the fallback, a return reads the brief for saving without sending it`() = runTest {
        val harness = Harness(this, outcome = HandoffOutcome.NO_CHAT_APP)

        harness.state.start(AiChatOffer.ALL)
        advanceUntilIdle()

        assertEquals("brief 1", harness.state.brief)
        assertEquals(emptyList<String>(), harness.sent)
        assertEquals(HandoffOutcome.NO_CHAT_APP, harness.state.outcome)
    }

    @Test
    fun `sending again reads a fresh brief with the current choice and sends it`() = runTest {
        val harness = Harness(this)
        harness.state.send()
        advanceUntilIdle()

        val partial = AiChatShare(homeAssistant = false)
        harness.state.choose(partial)
        harness.state.send()
        advanceUntilIdle()

        assertEquals(listOf("brief 1", "brief 2"), harness.sent)
        assertEquals(listOf(AiChatShare.ALL, partial), harness.readShares)
        assertEquals(listOf(partial), harness.persisted)
        assertEquals(2, harness.sentCalls)
    }

    @Test
    fun `a new choice drops the brief read for the old one and the fallback reads it again`() = runTest {
        val harness = Harness(this, outcome = HandoffOutcome.BRIEF_UNWRITABLE)
        harness.state.start(AiChatOffer.ALL)
        advanceUntilIdle()

        harness.state.choose(AiChatShare(shortcuts = false))
        advanceUntilIdle()

        assertEquals("brief 2", harness.state.brief)
        assertEquals(AiChatShare(shortcuts = false), harness.readShares.last())
        assertEquals(emptyList<String>(), harness.sent)
    }

    @Test
    fun `the choice cannot change while a send is under way`() = runTest {
        val harness = Harness(this).apply { gate = CompletableDeferred() }
        harness.state.send()
        runCurrent()

        harness.state.choose(AiChatShare(bluetooth = false))

        assertEquals(AiChatShare.ALL, harness.state.share)
        assertTrue(harness.persisted.isEmpty())
        harness.gate?.complete(Unit)
    }

    @Test
    fun `the brief is read for what the phone offers at the last start`() = runTest {
        val harness = Harness(this)
        val bare = AiChatOffer(bluetooth = false, homeAssistant = false)

        harness.state.start(bare)
        harness.state.send()
        advanceUntilIdle()

        assertEquals(bare, harness.state.offer)
        assertEquals(listOf(bare), harness.readOffers)
        assertEquals(listOf(AiChatShare.ALL), harness.readShares)
    }

    @Test
    fun `an offer that changed while away drops the brief held for saving and reads it again`() = runTest {
        val harness = Harness(this, outcome = HandoffOutcome.NO_CHAT_APP)
        harness.state.start(AiChatOffer(bluetooth = false, homeAssistant = true))
        advanceUntilIdle()
        harness.state.stop()

        harness.state.start(AiChatOffer.ALL)
        advanceUntilIdle()

        assertEquals("brief 2", harness.state.brief)
        assertEquals(AiChatOffer.ALL, harness.readOffers.last())

        harness.state.stop()
        harness.state.start(AiChatOffer.ALL)
        advanceUntilIdle()

        assertEquals(2, harness.reads)
    }

    @Test
    fun `a preview reads the brief for the current choice and never sends it`() = runTest {
        val partial = AiChatShare(bluetooth = false)
        val harness = Harness(this, share = partial)

        harness.state.preview()
        assertTrue(harness.state.previewing)
        advanceUntilIdle()

        assertEquals("brief 1", harness.state.brief)
        assertEquals(listOf(partial), harness.readShares)
        assertEquals(emptyList<String>(), harness.sent)
        assertEquals(0, harness.sentCalls)
        assertNull(harness.state.outcome)
        assertFalse(harness.state.sending)

        harness.state.closePreview()
        harness.state.preview()
        advanceUntilIdle()

        assertEquals(1, harness.reads)
    }

    @Test
    fun `leaving mid-preview cancels the read and a return reads it again`() = runTest {
        val harness = Harness(this).apply { gate = CompletableDeferred() }

        harness.state.preview()
        runCurrent()
        harness.state.stop()
        harness.gate = null
        advanceUntilIdle()

        assertNull(harness.state.brief)

        harness.state.start(AiChatOffer.ALL)
        advanceUntilIdle()

        assertEquals("brief 2", harness.state.brief)
        assertEquals(emptyList<String>(), harness.sent)
    }

    @Test
    fun `a preview left open re-reads on return rather than show the brief read before`() = runTest {
        val harness = Harness(this)
        harness.state.preview()
        advanceUntilIdle()

        harness.state.stop()
        assertNull(harness.state.brief)
        harness.state.start(AiChatOffer.ALL)
        advanceUntilIdle()

        assertEquals("brief 2", harness.state.brief)

        harness.state.closePreview()
        harness.state.stop()
        harness.state.start(AiChatOffer.ALL)
        harness.state.preview()
        advanceUntilIdle()

        assertEquals("brief 3", harness.state.brief)
        assertEquals(emptyList<String>(), harness.sent)
    }

    @Test
    fun `a save after a process death reads the brief it does not hold yet`() = runTest {
        val harness = Harness(this, outcome = HandoffOutcome.BRIEF_UNWRITABLE)

        assertEquals("brief 1", harness.state.briefForSaving())
        assertEquals("brief 1", harness.state.briefForSaving())
        assertEquals(1, harness.reads)
    }

    @Test
    fun `only the outcome survives a process death`() = runTest {
        val harness = Harness(this, answer = HandoffOutcome.NO_CHAT_APP)
        harness.state.send()
        advanceUntilIdle()

        val restored = roundTrip(harness.state)

        assertEquals(HandoffOutcome.NO_CHAT_APP, restored.outcome)
        assertNull(restored.brief)
    }

    @Test
    fun `a state that has not sent yet is restored as not sent`() = runTest {
        assertNull(roundTrip(Harness(this).state).outcome)
    }

    private fun TestScope.roundTrip(state: HandoffState): HandoffState {
        val saver = HandoffState.saver { outcome -> Harness(this, outcome).state }
        val saved = with(saver) { AnySaverScope.save(state) }
        return saver.restore(requireNotNull(saved)) ?: error("not restored")
    }

    private object AnySaverScope : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private companion object {
        val SENT = HandoffOutcome.SENT
    }
}
