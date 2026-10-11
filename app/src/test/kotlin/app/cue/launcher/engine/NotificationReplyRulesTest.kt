package app.cue.launcher.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NotificationReplyRulesTest {

    @Test
    fun `an action marked as a reply takes priority`() {
        val candidates = listOf(
            ReplyCandidate(hasFreeFormInput = true, hint = "Označit jako přečtené"),
            ReplyCandidate(hasFreeFormInput = true, isReplySemantic = true, hint = "Odpovědět Honzovi"),
        )

        assertEquals(1, replyCandidateIndex(candidates))
    }

    @Test
    fun `without semantics the order decides`() {
        val candidates = listOf(
            ReplyCandidate(hasFreeFormInput = false),
            ReplyCandidate(hasFreeFormInput = true, hint = "Odpověď"),
            ReplyCandidate(hasFreeFormInput = true),
        )

        assertEquals(1, replyCandidateIndex(candidates))
    }

    @Test
    fun `an action without a free-form field is not selected`() {
        val candidates = listOf(
            ReplyCandidate(hasFreeFormInput = false, isReplySemantic = true),
            ReplyCandidate(hasFreeFormInput = false),
        )

        assertNull(replyCandidateIndex(candidates))
    }

    @Test
    fun `a notification without actions cannot reply`() {
        assertNull(replyCandidateIndex(emptyList()))
    }

    @Test
    fun `a semantic action without a field does not override a usable one`() {
        val candidates = listOf(
            ReplyCandidate(hasFreeFormInput = false, isReplySemantic = true),
            ReplyCandidate(hasFreeFormInput = true, hint = "Odpovědět"),
        )

        assertEquals(1, replyCandidateIndex(candidates))
    }

    @Test
    fun `the reply text is trimmed`() {
        assertEquals("už jedu", replyDraft("  už jedu \n"))
    }

    @Test
    fun `an empty reply is not sent`() {
        assertNull(replyDraft(""))
        assertNull(replyDraft("   \n "))
    }
}
