package app.cue.launcher.system.bridge

import app.cue.launcher.system.notifications.ReplyDelivery
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class MirrorCommandsTest {

    private val ledger = MirrorLedger { 0L }
    private val cancelled = mutableListOf<String>()
    private val delivered = mutableListOf<Pair<String, String>>()
    private val signals = mutableListOf<BleSignal>()
    private val logged = mutableListOf<String>()
    private var listenerBound = true
    private var delivery = ReplyDelivery.SENT
    private var shown: Long? = REVISION

    private val commands = MirrorCommands(
        keyOf = ledger::keyOf,
        revisionOf = { shown },
        tombstone = ledger::tombstone,
        cancel = { key ->
            cancelled += key
            listenerBound
        },
        deliverReply = { key, text ->
            delivered += key to text
            delivery
        },
        sendSignal = { signal ->
            signals += signal
            true
        },
        log = { logged += it },
    )

    @Test
    fun `a dismiss cancels the notification its key hash maps to`() = runTest {
        ledger.reset(listOf(KEY))

        commands.dismiss(HASH, REVISION)

        assertEquals(listOf(KEY), cancelled)
        assertTrue(signals.isEmpty())
    }

    @Test
    fun `the removal a dismiss causes is not sent back`() = runTest {
        ledger.reset(listOf(KEY))

        commands.dismiss(HASH, REVISION)

        assertNull(ledger.removed(KEY))
        assertNull(ledger.keyOf(HASH))
    }

    @Test
    fun `a dismiss of a key hash the phone does not mirror is ignored`() = runTest {
        ledger.reset(listOf(KEY))

        commands.dismiss(OTHER_HASH, REVISION)

        assertTrue(cancelled.isEmpty())
        assertTrue(signals.isEmpty())
        assertTrue(logged.single().contains("not mirrored"))
    }

    @Test
    fun `a dismiss that could not cancel leaves no tombstone`() = runTest {
        ledger.reset(listOf(KEY))
        listenerBound = false

        commands.dismiss(HASH, REVISION)

        assertEquals(BleSignal.NotifRemoved(HASH), ledger.removed(KEY))
    }

    @Test
    fun `a dismiss for an older post of the key leaves the newer one alone and rings for it`() = runTest {
        ledger.reset(listOf(KEY))
        shown = REVISION + 1

        commands.dismiss(HASH, REVISION)

        assertTrue(cancelled.isEmpty())
        assertEquals(KEY, ledger.keyOf(HASH))
        assertTrue(logged.single().contains("older post"))
        assertEquals(listOf(BleSignal.NotifPosted(HASH)), signals)
    }

    @Test
    fun `a dismiss without a revision, or for a post the shade no longer reads, still cancels`() = runTest {
        ledger.reset(listOf(KEY))
        shown = REVISION + 1

        commands.dismiss(HASH, revision = null)
        ledger.reset(listOf(KEY))
        shown = null
        commands.dismiss(HASH, REVISION)

        assertEquals(listOf(KEY, KEY), cancelled)
    }

    @Test
    fun `an inline reply is delivered at once and answers reply-ok`() = runTest {
        ledger.reset(listOf(KEY))

        commands.reply(BleSignal.Reply(HASH, "On my way"))

        assertEquals(listOf(KEY to "On my way"), delivered)
        assertEquals(listOf(BleSignal.ReplyOk(HASH)), signals)
    }

    @Test
    fun `a reply with the tcp flag is only logged`() = runTest {
        ledger.reset(listOf(KEY))

        commands.reply(BleSignal.Reply(HASH, null))

        assertTrue(delivered.isEmpty())
        assertTrue(signals.isEmpty())
        assertTrue(logged.single().contains("reply-text"))
    }

    @Test
    fun `a reply-text is delivered on its own key and answered under its hash`() = runTest {
        ledger.reset(listOf(KEY))

        commands.replyText(KEY, "A reply too long for one BLE frame.")

        assertEquals(listOf(KEY to "A reply too long for one BLE frame."), delivered)
        assertEquals(listOf(BleSignal.ReplyOk(HASH)), signals)
    }

    @Test
    fun `a reply-text to a key the ledger does not hold reaches nothing and answers like a key that is gone`() =
        runTest {
            ledger.reset(listOf(KEY))
            commands.replyText(OTHER_KEY, "Probing")
            delivery = ReplyDelivery.GONE
            commands.replyText(KEY, "Too late")

            assertEquals(listOf(KEY to "Too late"), delivered)
            assertEquals(
                listOf(
                    BleSignal.ReplyError(notificationKeyHash(OTHER_KEY), REPLY_ERROR_GONE),
                    BleSignal.ReplyError(HASH, REPLY_ERROR_GONE),
                ),
                signals,
            )
        }

    @Test
    fun `before the first set nothing is mirrored, so no command reaches the shade`() = runTest {
        commands.replyText(KEY, "Hi")
        commands.reply(BleSignal.Reply(HASH, "Hi"))
        commands.dismiss(HASH, REVISION)

        assertTrue(delivered.isEmpty())
        assertTrue(cancelled.isEmpty())
        assertEquals(List(2) { BleSignal.ReplyError(HASH, REPLY_ERROR_GONE) }, signals)
    }

    // The companion window's ledger is seeded with what passes the filter and follows the posts, so a
    // notification the filter keeps back, or one that stops passing, is out of the Mac's reach.
    @Test
    fun `in the window a notification the filter keeps back cannot be answered or closed`() = runTest {
        ledger.reset(listOf(KEY))
        ledger.posted(OTHER_KEY, passes = false)
        ledger.posted(KEY, passes = false)

        commands.reply(BleSignal.Reply(notificationKeyHash(OTHER_KEY), "Hi"))
        commands.reply(BleSignal.Reply(HASH, "Hi"))
        commands.dismiss(HASH, REVISION)

        assertTrue(delivered.isEmpty())
        assertTrue(cancelled.isEmpty())
        assertEquals(
            listOf(
                BleSignal.ReplyError(notificationKeyHash(OTHER_KEY), REPLY_ERROR_GONE),
                BleSignal.ReplyError(HASH, REPLY_ERROR_GONE),
            ),
            signals,
        )
    }

    @Test
    fun `an inline reply to a key hash the phone does not mirror answers gone`() = runTest {
        ledger.reset(listOf(KEY))

        commands.reply(BleSignal.Reply(OTHER_HASH, "Hi"))

        assertTrue(delivered.isEmpty())
        assertEquals(listOf(BleSignal.ReplyError(OTHER_HASH, REPLY_ERROR_GONE)), signals)
    }

    @ParameterizedTest
    @EnumSource(ReplyDelivery::class, names = ["SENT"], mode = EnumSource.Mode.EXCLUDE)
    fun `an inline reply that fails answers reply-error with its reason`(failure: ReplyDelivery) = runTest {
        ledger.reset(listOf(KEY))
        delivery = failure

        commands.reply(BleSignal.Reply(HASH, "Hi"))

        assertEquals(listOf(BleSignal.ReplyError(HASH, REASONS.getValue(failure))), signals)
    }

    @ParameterizedTest
    @EnumSource(ReplyDelivery::class, names = ["SENT"], mode = EnumSource.Mode.EXCLUDE)
    fun `a reply-text that fails answers reply-error under the hash of its key`(failure: ReplyDelivery) = runTest {
        ledger.reset(listOf(KEY))
        delivery = failure

        commands.replyText(KEY, "Hi")

        assertEquals(listOf(BleSignal.ReplyError(HASH, REASONS.getValue(failure))), signals)
    }

    private companion object {
        const val KEY = "0|com.example.chat|42|null|10123"
        const val HASH = "54892d1f759b11f6"
        const val OTHER_HASH = "778e7ebdc00b1b16"
        const val OTHER_KEY = "0|com.example.bank|3|null|10123"
        const val REVISION = 1_791_820_800_000L

        val REASONS = mapOf(
            ReplyDelivery.GONE to REPLY_ERROR_GONE,
            ReplyDelivery.NO_REPLY_ACTION to REPLY_ERROR_NO_REPLY_ACTION,
            ReplyDelivery.FAILED to REPLY_ERROR_SEND_FAILED,
        )
    }
}
