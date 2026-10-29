package app.cue.launcher.system.bridge

import app.cue.launcher.data.notifications.NotificationListenerState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MirrorSyncTest {

    @Test
    fun `only denied access sends an empty set`() {
        assertEquals(MirrorSync.SEND_EMPTY, mirrorSyncOf(DENIED))
    }

    @Test
    fun `a listener still binding makes the set wait`() {
        assertEquals(MirrorSync.WAIT, mirrorSyncOf(BINDING))
    }

    @Test
    fun `a bound listener has the shade read`() {
        assertEquals(MirrorSync.READ_SHADE, mirrorSyncOf(BOUND))
    }

    @Test
    fun `the first binding sends the set, the wait before it nothing`() = runTest {
        assertEquals(listOf(MirrorSync.READ_SHADE), flowOf(BINDING, BOUND).mirrorSyncTriggers().toList())
    }

    @Test
    fun `binding again after a drop sends the whole set once more`() = runTest {
        val triggers = flowOf(BOUND, BINDING, BOUND).mirrorSyncTriggers().toList()

        assertEquals(listOf(MirrorSync.READ_SHADE, MirrorSync.READ_SHADE), triggers)
    }

    @Test
    fun `losing access clears the Mac's mirror, and granting it fills it again`() = runTest {
        val triggers = flowOf(BOUND, DENIED, BINDING, BOUND).mirrorSyncTriggers().toList()

        assertEquals(listOf(MirrorSync.READ_SHADE, MirrorSync.SEND_EMPTY, MirrorSync.READ_SHADE), triggers)
    }

    @Test
    fun `an unchanged state sends nothing more`() = runTest {
        assertEquals(listOf(MirrorSync.SEND_EMPTY), flowOf(DENIED, DENIED).mirrorSyncTriggers().toList())
    }

    @Test
    fun `a set that fits one frame goes out whole`() {
        val details = listOf(detail("a", 1), detail("b", 2))

        val fitted = fitActiveSet(details)

        assertSame(details, fitted.set.notifications)
        assertEquals(0, fitted.dropped)
    }

    @Test
    fun `a set too large for one frame leaves out the oldest`() {
        val details = listOf(detail("new", 30), detail("old", 10), detail("mid", 20))
        val limit = bridgeBody(BridgeMessage.ActiveSet(details.take(1) + details.drop(2))).size

        val fitted = fitActiveSet(details, limit)

        assertEquals(listOf("new", "mid"), fitted.set.notifications.map { it.key })
        assertEquals(1, fitted.dropped)
    }

    @Test
    fun `the largest shade still fits the body limit`() {
        val text = "😀\"".repeat(MAX_TEXT_CHARS)
        val details = (0 until 200).map { detail("k$it", it.toLong(), clipText(text, MAX_TEXT_CHARS)) }

        val fitted = fitActiveSet(details)

        assertTrue(fitted.dropped > 0)
        assertTrue(BridgeFraming.encode(fitted.set).size <= BRIDGE_LENGTH_BYTES + BRIDGE_MAX_BODY_BYTES)
        assertEquals(details.size - fitted.dropped, fitted.set.notifications.size)
        assertTrue(fitted.set.notifications.all { it.postedAt >= fitted.dropped })
    }

    @Test
    fun `an element alone above the limit leaves the set empty`() {
        val fitted = fitActiveSet(listOf(detail("a", 1, "x".repeat(200))), limit = 100)

        assertEquals(emptyList<BridgeMessage.NotifDetail>(), fitted.set.notifications)
        assertEquals(1, fitted.dropped)
    }

    private fun detail(key: String, postedAt: Long, text: String = "Are you coming?") = BridgeMessage.NotifDetail(
        key = key,
        pkg = "com.example.chat",
        appLabel = "Chat",
        title = "Alice",
        text = text,
        postedAt = postedAt,
        canReply = false,
        isClearable = true,
    )

    private companion object {
        val DENIED = NotificationListenerState(enabled = false, connected = false)
        val BINDING = NotificationListenerState(enabled = true, connected = false)
        val BOUND = NotificationListenerState(enabled = true, connected = true)
    }
}
