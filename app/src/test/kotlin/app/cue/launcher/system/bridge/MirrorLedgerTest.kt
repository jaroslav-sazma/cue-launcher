package app.cue.launcher.system.bridge

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MirrorLedgerTest {

    private var now = 0L

    private val ledger = MirrorLedger { now }

    @Test
    fun `nothing rings before the first active-set`() {
        assertNull(ledger.posted(A, passes = true))
        assertNull(ledger.removed(A))
        assertNull(ledger.keyOf(HASH_A))
        assertFalse(ledger.synced)
    }

    @Test
    fun `every post that passes rings, an update of a mirrored key too`() {
        ledger.reset(listOf(A))

        assertEquals(BleSignal.NotifPosted(HASH_A), ledger.posted(A, passes = true))
        assertEquals(BleSignal.NotifPosted(HASH_B), ledger.posted(B, passes = true))
    }

    @Test
    fun `for a session a post that passes is content to send, one that no longer passes a removal`() {
        assertNull(ledger.news(A, passes = true), "nothing before the first set")
        ledger.reset(listOf(A))

        val update = ledger.news(A, passes = true)
        val added = ledger.news(B, passes = true)
        val takenBack = ledger.news(A, passes = false)

        assertEquals(HASH_A, (update as PostNews.Content).keyHash)
        assertEquals(HASH_B, (added as PostNews.Content).keyHash)
        assertEquals(BleSignal.NotifRemoved(HASH_A), (takenBack as PostNews.Ring).signal)
        assertNull(ledger.news(A, passes = false))
        assertEquals(B, ledger.keyOf(HASH_B), "a key sent with its content is mirrored like a listed one")
    }

    @Test
    fun `a removal rings only for a key the Mac mirrors`() {
        ledger.reset(listOf(A))

        assertEquals(BleSignal.NotifRemoved(HASH_A), ledger.removed(A))
        assertNull(ledger.removed(A))
        assertNull(ledger.removed(B))
    }

    @Test
    fun `a doorbell adds to what the Mac mirrors`() {
        ledger.reset(emptyList())
        ledger.posted(B, passes = true)

        assertEquals(BleSignal.NotifRemoved(HASH_B), ledger.removed(B))
    }

    @Test
    fun `an update that no longer passes takes the key back`() {
        ledger.reset(listOf(A))

        assertEquals(BleSignal.NotifRemoved(HASH_A), ledger.posted(A, passes = false))
        assertNull(ledger.posted(A, passes = false))
        assertNull(ledger.posted(B, passes = false))
    }

    @Test
    fun `every active-set replaces what the Mac mirrors`() {
        ledger.reset(listOf(A))
        ledger.reset(listOf(B))

        assertNull(ledger.removed(A))
        assertEquals(BleSignal.NotifRemoved(HASH_B), ledger.removed(B))
    }

    @Test
    fun `a key hash maps back to the key the Mac mirrors, and only while it does`() {
        ledger.reset(listOf(A))
        ledger.posted(B, passes = true)

        assertEquals(A, ledger.keyOf(HASH_A))
        assertEquals(B, ledger.keyOf(HASH_B))

        ledger.removed(B)

        assertNull(ledger.keyOf(HASH_B))
    }

    @Test
    fun `a tombstoned removal leaves the mirror without ringing`() {
        ledger.reset(listOf(A))
        ledger.tombstone(HASH_A)

        assertNull(ledger.removed(A))
        assertNull(ledger.keyOf(HASH_A))
    }

    @Test
    fun `a tombstone is spent by the removal it was set for`() {
        ledger.reset(listOf(A))
        ledger.tombstone(HASH_A)
        ledger.removed(A)
        ledger.posted(A, passes = true)

        assertEquals(BleSignal.NotifRemoved(HASH_A), ledger.removed(A))
    }

    @Test
    fun `a tombstone lives for the minimum the spec sets and no longer`() {
        ledger.reset(listOf(A, B))
        ledger.tombstone(HASH_A)
        ledger.tombstone(HASH_B)

        now = TOMBSTONE_MIN_MS - 1
        assertNull(ledger.removed(A))

        now = TOMBSTONE_MIN_MS
        assertEquals(BleSignal.NotifRemoved(HASH_B), ledger.removed(B))
    }

    @Test
    fun `a tombstone suppresses nothing for another key`() {
        ledger.reset(listOf(A, B))
        ledger.tombstone(HASH_A)

        assertEquals(BleSignal.NotifRemoved(HASH_B), ledger.removed(B))
    }

    @Test
    fun `only an advertised icon is answered`() {
        val png = byteArrayOf(1, 2, 3)
        ledger.advertise(ICON, png)

        assertArrayEquals(png, ledger.icon(ICON))
        assertNull(ledger.icon(ICON.reversed()))
    }

    private companion object {
        const val A = "0|com.example.chat|42|null|10123"
        const val B = "0|com.whatsapp|1|abc@s.whatsapp.net|10234"
        const val HASH_A = "54892d1f759b11f6"
        const val HASH_B = "778e7ebdc00b1b16"
        const val ICON = "4c4b6a3be1314ab86138bef4314dde022e600960d8689a2c8f8631802d20dab6"
    }
}
