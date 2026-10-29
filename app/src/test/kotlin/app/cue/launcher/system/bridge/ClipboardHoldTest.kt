package app.cue.launcher.system.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClipboardHoldTest {

    private var now = 1_000_000L
    private val hold = ClipboardHold { now }

    private fun arrive(text: String, locked: Boolean, ageMs: Long? = null) =
        hold.arrived(text, sensitive = false, ageMs = ageMs, locked = locked, limitMs = LIMIT)

    @Test
    fun `an unlocked phone applies a fresh clip at once and holds nothing`() {
        assertEquals(ClipArrival.APPLY, arrive("hello", locked = false))
        assertEquals(ClipArrival.APPLY, arrive("hello", locked = false, ageMs = LIMIT))
        assertFalse(hold.pending)
    }

    @Test
    fun `a clip older than the limit on arrival is dropped, locked or not`() {
        assertEquals(ClipArrival.STALE, arrive("old", locked = false, ageMs = LIMIT + 1))
        assertEquals(ClipArrival.STALE, arrive("old", locked = true, ageMs = LIMIT + 1))
        assertFalse(hold.pending)
    }

    @Test
    fun `a negative age counts as fresh`() {
        assertEquals(ClipArrival.APPLY, arrive("hello", locked = false, ageMs = -5))
    }

    @Test
    fun `a locked phone holds the clip and applies it at unlock within the limit`() {
        val arrival = hold.arrived("secret", sensitive = true, ageMs = null, locked = true, limitMs = LIMIT)

        assertEquals(ClipArrival.HOLD, arrival)
        assertTrue(hold.pending)
        now += LIMIT

        val step = hold.unlocked(LIMIT)

        assertEquals("secret", step.clip?.text)
        assertEquals(true, step.clip?.sensitive)
        assertFalse(step.staleClip)
        assertEquals(OwedOutcome.NONE, step.owed)
        assertFalse(hold.pending)
    }

    @Test
    fun `the age the Mac reported and the time held add up`() {
        arrive("copied a while ago", locked = true, ageMs = LIMIT - 1_000)
        now += 1_001

        val step = hold.unlocked(LIMIT)

        assertNull(step.clip)
        assertTrue(step.staleClip)
        assertFalse(hold.pending)
    }

    @Test
    fun `the limit in force at unlock decides`() {
        arrive("held", locked = true)
        now += 2 * MINUTE

        assertNull(hold.unlocked(MINUTE).clip)
    }

    @Test
    fun `a newer clip replaces the held one, even a stale one`() {
        arrive("first", locked = true)
        arrive("second", locked = true)
        assertEquals("second", hold.unlocked(LIMIT).clip?.text)

        arrive("first", locked = true)
        assertEquals(ClipArrival.STALE, arrive("second", locked = true, ageMs = LIMIT + 1))
        assertFalse(hold.pending)
    }

    @Test
    fun `a pending clip on an unlocked phone summons at once and holds nothing`() {
        assertTrue(hold.owed(locked = false))
        assertFalse(hold.pending)
    }

    @Test
    fun `a pending clip on a locked phone summons at unlock within the limit`() {
        assertFalse(hold.owed(locked = true))
        assertTrue(hold.pending)
        now += LIMIT

        val step = hold.unlocked(LIMIT)

        assertEquals(OwedOutcome.SUMMON, step.owed)
        assertNull(step.clip)
        assertFalse(hold.pending)
    }

    @Test
    fun `a pending clip past the limit at unlock is forgotten`() {
        hold.owed(locked = true)
        now += LIMIT + 1

        assertEquals(OwedOutcome.STALE, hold.unlocked(LIMIT).owed)
        assertFalse(hold.pending)
    }

    @Test
    fun `a clip clears the owed flag, and a pending signal the held clip`() {
        hold.owed(locked = true)
        arrive("arrived", locked = true)
        val afterClip = hold.unlocked(LIMIT)
        assertEquals(OwedOutcome.NONE, afterClip.owed)
        assertEquals("arrived", afterClip.clip?.text)

        arrive("held", locked = true)
        hold.owed(locked = true)
        val afterPending = hold.unlocked(LIMIT)
        assertNull(afterPending.clip)
        assertFalse(afterPending.staleClip)
        assertEquals(OwedOutcome.SUMMON, afterPending.owed)
    }

    @Test
    fun `clearing forgets everything`() {
        arrive("held", locked = true)
        hold.clear()

        assertFalse(hold.pending)
        assertNull(hold.unlocked(LIMIT).clip)
    }

    @Test
    fun `clearing at the Mac's absence forgets an owed fetch too`() {
        hold.owed(locked = true)
        hold.clear()

        assertFalse(hold.pending)
        assertEquals(OwedOutcome.NONE, hold.unlocked(LIMIT).owed)
    }

    @Test
    fun `the clipboard size cap counts UTF-8 bytes`() {
        assertTrue(fitsClipboard("x".repeat(CLIPBOARD_MAX_BYTES)))
        assertFalse(fitsClipboard("x".repeat(CLIPBOARD_MAX_BYTES + 1)))
        assertFalse(fitsClipboard("é".repeat(CLIPBOARD_MAX_BYTES / 2 + 1)))
    }

    private companion object {
        const val MINUTE = 60_000L
        const val LIMIT = 5 * MINUTE
    }
}
