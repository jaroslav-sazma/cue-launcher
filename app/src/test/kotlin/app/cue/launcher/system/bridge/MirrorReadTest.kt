package app.cue.launcher.system.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MirrorReadTest {

    @Test
    fun `a conversation shortcut threads the notification`() {
        assertEquals("com.example.chat/alice", mirrorThreadId("com.example.chat", "alice", "inbox"))
    }

    @Test
    fun `without a shortcut the app's group threads it`() {
        assertEquals("com.example.chat/inbox", mirrorThreadId("com.example.chat", null, "inbox"))
        assertEquals("com.example.chat/inbox", mirrorThreadId("com.example.chat", "", "inbox"))
    }

    @Test
    fun `with neither the thread is left to the Mac`() {
        assertNull(mirrorThreadId("com.example.chat", null, null))
        assertNull(mirrorThreadId("com.example.chat", "", ""))
    }

    @Test
    fun `a redacted notification goes as the public version the app provides`() {
        assertEquals(
            MirrorText("Chat", "2 new messages"),
            redactedText("Chat", "2 new messages", "Example Chat", HIDDEN),
        )
    }

    @Test
    fun `a public version without a title goes under the app's name`() {
        assertEquals(MirrorText(APP, "New message"), redactedText(null, "New message", APP, HIDDEN))
        assertEquals(MirrorText("New message", null), redactedText("New message", null, APP, HIDDEN))
    }

    @Test
    fun `without a public version only the app's name and the hidden text go`() {
        assertEquals(MirrorText("Example Chat", HIDDEN), redactedText(null, null, "Example Chat", HIDDEN))
    }

    @Test
    fun `short text is kept whole`() {
        assertEquals("Are you coming?", clipText("Are you coming?", MAX_TEXT_CHARS))
    }

    @Test
    fun `long text is cut at the limit`() {
        assertEquals("abc", clipText("abcdef", 3))
    }

    @Test
    fun `a cut never splits a surrogate pair`() {
        val text = "ab😀cd"

        assertEquals("ab", clipText(text, 3))
        assertEquals("ab😀", clipText(text, 4))
    }

    private companion object {
        const val HIDDEN = "Content hidden"
        const val APP = "Example Chat"
    }
}
