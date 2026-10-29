package app.cue.launcher.system.notifications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ListenerHoldTest {

    private val store = MemoryStore()

    @Test
    fun `nothing is wanted until something needs the posts`() {
        assertFalse(ListenerHold(store).wanted)
    }

    @Test
    fun `the need outlives the session`() {
        val hold = ListenerHold(store)
        hold.enter()
        hold.need()

        assertFalse(hold.leave())
        assertTrue(hold.wanted)
    }

    @Test
    fun `the need survives the process, so a restarted one keeps the binding`() {
        val first = ListenerHold(store)
        first.enter()
        first.need()

        assertTrue(ListenerHold(store).wanted)
    }

    @Test
    fun `nothing needing the posts any more releases the listener`() {
        val hold = ListenerHold(store)
        hold.need()

        assertTrue(hold.unneed())
        assertFalse(hold.wanted)
        assertFalse(ListenerHold(store).wanted)
    }

    @Test
    fun `releasing what was never needed does nothing`() {
        assertFalse(ListenerHold(store).unneed())
    }

    @Test
    fun `a sample binds only within a session and is released with it`() {
        val hold = ListenerHold(store)
        assertFalse(hold.sample())

        hold.enter()
        assertTrue(hold.sample())
        assertTrue(hold.wanted)

        assertTrue(hold.leave())
        assertFalse(hold.wanted)
    }

    @Test
    fun `a sample taken while the posts are needed leaves the binding in place`() {
        val hold = ListenerHold(store)
        hold.enter()
        hold.need()
        hold.sample()

        assertFalse(hold.leave())
        assertTrue(hold.wanted)
    }

    @Test
    fun `while a sample is being picked the need going away keeps the binding`() {
        val hold = ListenerHold(store)
        hold.enter()
        hold.need()
        hold.sample()

        assertFalse(hold.unneed())
        assertTrue(hold.wanted)
    }

    @Test
    fun `overlapping sessions release a sample only when the last one leaves`() {
        val hold = ListenerHold(store)
        hold.enter()
        hold.enter()
        hold.sample()

        assertFalse(hold.leave())
        assertTrue(hold.wanted)
        assertTrue(hold.leave())
    }

    @Test
    fun `the bridge binds the listener only while it holds it and is not kept on disk`() {
        val hold = ListenerHold(store)
        hold.bridge()

        assertTrue(hold.wanted)
        assertFalse(ListenerHold(store).wanted)
        assertTrue(hold.unbridge())
        assertFalse(hold.wanted)
    }

    @Test
    fun `the bridge letting go leaves a binding something else needs`() {
        val hold = ListenerHold(store)
        hold.enter()
        hold.need()
        hold.bridge()

        assertFalse(hold.unbridge())
        assertTrue(hold.wanted)
    }

    @Test
    fun `while the bridge holds it neither the need nor a sample going away releases the listener`() {
        val hold = ListenerHold(store)
        hold.enter()
        hold.need()
        hold.sample()
        hold.bridge()

        assertFalse(hold.unneed())
        assertFalse(hold.leave())
        assertTrue(hold.wanted)
        assertTrue(hold.unbridge())
    }

    // The session link and the companion window hold it each on their own.
    @Test
    fun `the listener stays bound until the last of two bridge holders lets go`() {
        val hold = ListenerHold(store)
        hold.bridge()
        hold.bridge()

        assertFalse(hold.unbridge())
        assertTrue(hold.wanted)
        assertTrue(hold.unbridge())
        assertFalse(hold.wanted)
        assertFalse(hold.unbridge())
    }

    @Test
    fun `letting go of a bridge that never held does nothing`() {
        assertFalse(ListenerHold(store).unbridge())
    }

    @Test
    fun `a process that follows one which needed the posts replays from its death`() {
        store.value = true
        val hold = ListenerHold(store)

        assertTrue(hold.neededAtStart)
        assertEquals(400L, hold.connected(now = 1_000) { 400 })
    }

    @Test
    fun `a process that follows one which did not need them replays nothing`() {
        val hold = ListenerHold(store)
        hold.enter()
        hold.need()

        assertFalse(hold.neededAtStart)
        assertEquals(1_000L, hold.connected(now = 1_000) { error("no death to look up") })
    }

    @Test
    fun `later only a lost binding opens a window`() {
        store.value = true
        val hold = ListenerHold(store)
        hold.connected(now = 1_000) { 400 }

        hold.lost(now = 2_000)

        assertEquals(2_000L, hold.connected(now = 2_500) { 400 })
        assertEquals(3_000L, hold.connected(now = 3_000) { 400 })
    }

    @Test
    fun `a release on purpose closes the window, also before the first connect`() {
        store.value = true
        val hold = ListenerHold(store)

        assertTrue(hold.unneed())
        hold.need()

        assertEquals(5_000L, hold.connected(now = 5_000) { 400 })
    }

    private class MemoryStore : NeedStore {
        var value = false

        override fun read(): Boolean = value

        override fun write(needed: Boolean) {
            value = needed
        }
    }
}
