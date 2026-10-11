package app.cue.launcher.system.aichat

import android.content.Intent
import app.cue.launcher.system.AI_ASSISTANT_PACKAGE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class AiChatHandoffTest {

    @Test
    fun `the assistant setting names its holder by component`() {
        assertEquals(
            "com.openai.chatgpt",
            assistantPackageOf("com.openai.chatgpt/com.openai.voice.AssistantService"),
        )
    }

    @Test
    fun `an unset or cleared assistant setting names no holder`() {
        assertNull(assistantPackageOf(null))
        assertNull(assistantPackageOf(""))
        assertNull(assistantPackageOf("  "))
    }

    @Test
    fun `another assistant is tried first, Gemini after it`() {
        assertEquals(listOf("com.openai.chatgpt", AI_ASSISTANT_PACKAGE), chatCandidates("com.openai.chatgpt"))
    }

    @Test
    fun `the Google app stands for Gemini, whose share it does not take`() {
        assertEquals(listOf(AI_ASSISTANT_PACKAGE), chatCandidates(GOOGLE_APP_PACKAGE))
    }

    @Test
    fun `without a known holder only Gemini is tried`() {
        assertEquals(listOf(AI_ASSISTANT_PACKAGE), chatCandidates(null))
        assertEquals(listOf(AI_ASSISTANT_PACKAGE), chatCandidates(AI_ASSISTANT_PACKAGE))
    }

    @Test
    fun `the share is an explicit send of the file with the message and a read grant`() {
        val share = BriefShare("com.openai.chatgpt", STREAM)

        assertEquals(Intent.ACTION_SEND, share.action)
        assertEquals("com.openai.chatgpt", share.packageName)
        assertEquals("text/plain", share.type)
        assertEquals(STREAM, share.stream)
        assertEquals(AI_CHAT_FILE_PROMPT, share.text)
        assertEquals(AI_CHAT_CONTEXT_FILE_NAME, share.clipLabel)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, share.flags)
    }

    @Test
    fun `the holder that takes the share gets the file just written`() = runTest {
        val channel = FakeChannel(assistant = "com.openai.chatgpt", accepting = setOf("com.openai.chatgpt"))

        assertEquals(HandoffOutcome.SENT, handOff(BRIEF, channel, Dispatchers.Unconfined))
        assertEquals(BRIEF, channel.written)
        assertEquals(listOf(BriefShare("com.openai.chatgpt", STREAM)), channel.started)
        assertEquals(listOf("com.openai.chatgpt"), channel.asked)
    }

    @Test
    fun `a holder that takes no share hands over to Gemini`() = runTest {
        val channel = FakeChannel(assistant = "com.openai.chatgpt", accepting = setOf(AI_ASSISTANT_PACKAGE))

        assertEquals(HandoffOutcome.SENT, handOff(BRIEF, channel, Dispatchers.Unconfined))
        assertEquals(listOf("com.openai.chatgpt", AI_ASSISTANT_PACKAGE), channel.asked)
        assertEquals(listOf(AI_ASSISTANT_PACKAGE), channel.started.map(BriefShare::packageName))
    }

    @Test
    fun `a holder that fails to start hands over to Gemini`() = runTest {
        val both = setOf("com.openai.chatgpt", AI_ASSISTANT_PACKAGE)
        val channel = FakeChannel("com.openai.chatgpt", accepting = both, failing = setOf("com.openai.chatgpt"))

        assertEquals(HandoffOutcome.SENT, handOff(BRIEF, channel, Dispatchers.Unconfined))
        assertEquals(listOf(AI_ASSISTANT_PACKAGE), channel.started.map(BriefShare::packageName))
    }

    @Test
    fun `the Google app as holder sends the brief to Gemini, never to itself`() = runTest {
        val channel = FakeChannel(GOOGLE_APP_PACKAGE, accepting = setOf(GOOGLE_APP_PACKAGE, AI_ASSISTANT_PACKAGE))

        assertEquals(HandoffOutcome.SENT, handOff(BRIEF, channel, Dispatchers.Unconfined))
        assertEquals(listOf(AI_ASSISTANT_PACKAGE), channel.asked)
    }

    @Test
    fun `when no candidate takes it nothing is started`() = runTest {
        val channel = FakeChannel(assistant = "com.openai.chatgpt")

        assertEquals(HandoffOutcome.NO_CHAT_APP, handOff(BRIEF, channel, Dispatchers.Unconfined))
        assertTrue(channel.started.isEmpty())
    }

    @Test
    fun `a brief that cannot be written is reported, not thrown`() = runTest {
        val full = FakeChannel(accepting = setOf(AI_ASSISTANT_PACKAGE), write = { throw IOException("ENOSPC") })
        val uncovered =
            FakeChannel(accepting = setOf(AI_ASSISTANT_PACKAGE), write = { throw IllegalArgumentException() })

        assertEquals(HandoffOutcome.BRIEF_UNWRITABLE, handOff(BRIEF, full, Dispatchers.Unconfined))
        assertEquals(HandoffOutcome.BRIEF_UNWRITABLE, handOff(BRIEF, uncovered, Dispatchers.Unconfined))
        assertTrue(full.asked.isEmpty() && uncovered.asked.isEmpty())
    }

    private class FakeChannel(
        private val assistant: String? = null,
        private val accepting: Set<String> = emptySet(),
        private val failing: Set<String> = emptySet(),
        private val write: (String) -> String = { STREAM },
    ) : BriefChannel {
        val asked = mutableListOf<String>()
        val started = mutableListOf<BriefShare>()
        var written: String? = null

        override fun assistantPackage(): String? = assistant

        override fun writeBrief(brief: String): String = write(brief).also { written = brief }

        override fun accepts(share: BriefShare): Boolean {
            asked += share.packageName
            return share.packageName in accepting
        }

        override fun start(share: BriefShare): Boolean {
            if (share.packageName in failing) return false
            started += share
            return true
        }
    }

    private companion object {
        const val BRIEF = "# Cue: scenario brief"
        const val STREAM = "content://app.cue.launcher.aichat/aichat/cue-scenario-context.md"
    }
}
