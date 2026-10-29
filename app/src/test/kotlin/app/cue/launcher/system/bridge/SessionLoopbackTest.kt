package app.cue.launcher.system.bridge

import app.cue.launcher.data.bridge.BridgeReplayWindow
import app.cue.launcher.system.notifications.ReplyDelivery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

// One session end to end in-process: the phone's real conversation, framing, frame codec, ledger
// and mirror commands over in-memory carriers, against a scripted Mac. Only the shade and the
// notification's reply action are fakes. What the Mac decides is the Bridge repository's to test;
// the script here plays what its pending dismissals send.
class SessionLoopbackTest {

    private class Pipe {
        private val lock = ReentrantLock()
        private val ready = lock.newCondition()
        private val bytes = ArrayDeque<Byte>()
        private var closed = false

        val input: InputStream = object : InputStream() {
            override fun read(): Int {
                val one = ByteArray(1)
                return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and BYTE_MASK
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int = lock.withLock {
                while (len > 0 && bytes.isEmpty() && !closed) ready.await()
                var count = 0
                while (count < len && bytes.isNotEmpty()) {
                    b[off + count] = bytes.removeFirst()
                    count++
                }
                if (len > 0 && count == 0) -1 else count
            }
        }

        val output: OutputStream = object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

            override fun write(b: ByteArray, off: Int, len: Int) {
                lock.withLock {
                    if (closed) throw IOException("the pipe is closed")
                    for (index in off until off + len) bytes.addLast(b[index])
                    ready.signalAll()
                }
            }
        }

        fun close() = lock.withLock {
            closed = true
            ready.signalAll()
        }
    }

    private class Shown(val revision: Long, val text: String)

    private val key = BridgeFixtures.load("frames.json").hex("kBle")
    private val toPhone = Pipe()
    private val toMac = Pipe()
    private val carrier = object : BridgeCarrier {
        override fun setReadTimeout(millis: Int) = Unit

        override fun close() {
            toPhone.close()
            toMac.close()
        }
    }

    private val shade = ConcurrentHashMap<String, Shown>()

    // In the shade but kept off the Mac by the filter, as an excluded app or a secret notification is.
    private val keptBack = ConcurrentHashMap.newKeySet<String>()
    private val delivered = CopyOnWriteArrayList<Pair<String, String>>()
    private val clips = CopyOnWriteArrayList<Pair<String, Long?>>()
    private val lockStates = CopyOnWriteArrayList<Pair<Long, Boolean>>()
    private val ledger = MirrorLedger { 0L }
    private var phoneSeq = 0L
    private var phoneWindow = BridgeReplayWindow()

    private val activity = ReadActivity()
    private val phone = BridgeConnection(ActivityInputStream(toPhone.input, activity), toMac.output, carrier, activity)
    private lateinit var talk: SessionConversation
    private val mac = ScriptedMac()

    private val commands = MirrorCommands(
        keyOf = ledger::keyOf,
        revisionOf = { key -> shade[key]?.revision },
        tombstone = ledger::tombstone,
        cancel = { key -> shade.remove(key) != null },
        deliverReply = { key, text ->
            delivered += key to text
            if (shade.containsKey(key)) ReplyDelivery.SENT else ReplyDelivery.GONE
        },
        sendSignal = { signal -> sendSignal(signal) },
        log = {},
    )

    private fun sendSignal(signal: BleSignal): Boolean {
        val frame = BleFrameCodec.seal(
            key, BleDirection.PHONE_TO_MAC, signal.type.code, phoneSeq++, encodeBleSignal(signal),
        )
        talk.send(BridgeMessage.Signal(encodeBase64(frame)))
        return true
    }

    // What the session's mirror does with the listener's posts and removals.
    private fun post(key: String, shown: Shown) {
        shade[key] = shown
        when (val news = ledger.news(key, passes = true)) {
            is PostNews.Content -> talk.send(detail(key, shown))
            is PostNews.Ring -> sendSignal(news.signal)
            null -> Unit
        }
    }

    private fun remove(key: String) {
        shade.remove(key)
        ledger.removed(key)?.let(::sendSignal)
    }

    private val inbox = object : SessionInbox {
        override suspend fun sync() {
            val set = BridgeMessage.ActiveSet(
                shade.filterKeys { it !in keptBack }.map { (key, shown) -> detail(key, shown) },
            )
            talk.send(set)
            ledger.reset(set.notifications.map { it.key })
        }

        override suspend fun icon(hash: String) = Unit

        override suspend fun replyText(key: String, text: String) = commands.replyText(key, text)

        override suspend fun signal(signal: BleSignal) {
            when (signal) {
                BleSignal.SyncRequest -> sync()
                is BleSignal.Dismiss -> commands.dismiss(signal.keyHash, signal.revision)
                is BleSignal.Reply -> commands.reply(signal)
                is BleSignal.ClipboardSmall -> clips += signal.text to signal.ageMs
                else -> Unit
            }
        }

        override suspend fun clip(text: String, sensitive: Boolean, ageMs: Long?) {
            clips += text to ageMs
        }

        override suspend fun lockState(seq: Long, locked: Boolean) {
            lockStates += seq to locked
        }
    }

    @AfterEach
    fun closeCarriers() = carrier.close()

    @Test
    fun `a dismiss from the Mac cancels the notification its set listed`() = conversation {
        shade[CHAT] = Shown(R1, "Are you coming?")
        inbox.sync()
        val listed = mac.expect<BridgeMessage.ActiveSet>().notifications.single()
        assertEquals(CHAT to R1, listed.key to listed.revision)

        mac.signal(BleSignal.Dismiss(CHAT_HASH, listed.revision))

        awaitUntil { !shade.containsKey(CHAT) }
        assertNull(ledger.removed(CHAT), "the removal the dismiss caused is not sent back")
    }

    @Test
    fun `once the first set is out a post goes with its content and a removal as a signal`() = conversation {
        post(CHAT, Shown(R1, "Are you coming?"))
        inbox.sync()
        // The post before the set sent nothing, so the set is the first thing the Mac reads.
        val opening = mac.expect<BridgeMessage.ActiveSet>().notifications.single()
        assertEquals(CHAT to R1, opening.key to opening.revision)

        post(CHAT, Shown(R2, "Running late"))
        val update = mac.expect<BridgeMessage.NotifDetail>()
        assertEquals(Triple(CHAT, R2, "Running late"), Triple(update.key, update.revision, update.text))
        post(MAIL, Shown(R1, "Invoice attached"))
        assertEquals(MAIL, mac.expect<BridgeMessage.NotifDetail>().key)

        mac.signal(BleSignal.Dismiss(MAIL_HASH, R1))
        awaitUntil { !shade.containsKey(MAIL) }
        remove(CHAT)
        assertEquals(BleSignal.NotifRemoved(CHAT_HASH), mac.expectSignal())

        mac.send(BridgeMessage.SyncRequest)
        assertTrue(mac.expect<BridgeMessage.ActiveSet>().notifications.isEmpty(), "the full set still answers")
    }

    @Test
    fun `a dismiss lost on the way is made up for by the next set`() = conversation {
        shade[CHAT] = Shown(R1, "Are you coming?")
        inbox.sync()
        mac.expect<BridgeMessage.ActiveSet>()
        val lost = BleSignal.Dismiss(CHAT_HASH, R1)

        mac.send(BridgeMessage.SyncRequest)
        val again = mac.expect<BridgeMessage.ActiveSet>().notifications.single()
        assertEquals(R1, again.revision, "the phone never got the dismiss, so it still lists the key")
        mac.signal(lost)

        awaitUntil { !shade.containsKey(CHAT) }
        mac.send(BridgeMessage.SyncRequest)
        assertTrue(mac.expect<BridgeMessage.ActiveSet>().notifications.isEmpty(), "settled")
        mac.signal(BleSignal.Dismiss(CHAT_HASH, R1))
        mac.send(BridgeMessage.SyncRequest)
        assertTrue(mac.expect<BridgeMessage.ActiveSet>().notifications.isEmpty(), "a repeat is harmless")
    }

    @Test
    fun `a dismiss for an older revision is ignored and the one at the new revision applies`() = conversation {
        shade[CHAT] = Shown(R1, "Are you coming?")
        inbox.sync()
        mac.expect<BridgeMessage.ActiveSet>()
        shade[CHAT] = Shown(R2, "Are you coming?")

        mac.signal(BleSignal.Dismiss(CHAT_HASH, R1))
        assertEquals(BleSignal.NotifPosted(CHAT_HASH), mac.expectSignal(), "the phone rings for the key instead")
        mac.send(BridgeMessage.SyncRequest)
        val listed = mac.expect<BridgeMessage.ActiveSet>().notifications.single()
        assertEquals(R2, listed.revision, "the newer post was not cancelled")

        mac.signal(BleSignal.Dismiss(CHAT_HASH, listed.revision))

        awaitUntil { !shade.containsKey(CHAT) }
    }

    @Test
    fun `lock-states reach the phone with their seqs, and an overtaken one changes nothing`() = conversation {
        val cache = MacLockCache()
        assertNull(cache.locked, "unknown until the first lock-state")

        mac.signal(BleSignal.LockState(locked = true), at = LOCK_SEQ)
        mac.signal(BleSignal.LockState(locked = false), at = LOCK_SEQ + 2)
        mac.signal(BleSignal.LockState(locked = true), at = LOCK_SEQ + 1)
        awaitUntil { lockStates.size == 3 }
        lockStates.forEach { (seq, locked) -> cache.received(KEY_TAG, seq, locked) }

        assertEquals(listOf(LOCK_SEQ to true, LOCK_SEQ + 2 to false, LOCK_SEQ + 1 to true), lockStates)
        assertEquals(false, cache.locked, "the overtaken lock-state was sealed before the newest one")
    }

    @Test
    fun `a lock-state whose state is not a boolean is ignored but uses up its seq`() = conversation {
        val plaintext = """{"l":"true"}""".encodeToByteArray()
        val quoted = BleFrameCodec.seal(
            key, BleDirection.MAC_TO_PHONE, BleFrameType.LOCK_STATE.code, LOCK_SEQ, plaintext,
        )
        mac.send(BridgeMessage.Signal(encodeBase64(quoted)))
        mac.signal(BleSignal.LockState(locked = false))

        awaitUntil { lockStates.isNotEmpty() }
        assertEquals(listOf(0L to false), lockStates)
        assertTrue(!phoneWindow.permits(LOCK_SEQ), "the ignored frame was still accepted")
    }

    @Test
    fun `an inline reply and a reply-text are each answered with reply-ok`() = conversation {
        shade[CHAT] = Shown(R1, "Are you coming?")
        inbox.sync()
        mac.expect<BridgeMessage.ActiveSet>()

        mac.signal(BleSignal.Reply(CHAT_HASH, "On my way"))
        assertEquals(BleSignal.ReplyOk(CHAT_HASH), mac.expectSignal())
        mac.signal(BleSignal.Reply(CHAT_HASH, null))
        mac.send(BridgeMessage.ReplyText(CHAT, "A reply too long for one BLE frame."))
        assertEquals(BleSignal.ReplyOk(CHAT_HASH), mac.expectSignal())
        mac.send(BridgeMessage.ReplyText(MAIL, "Gone already"))
        assertEquals(BleSignal.ReplyError(MAIL_HASH, REPLY_ERROR_GONE), mac.expectSignal())

        assertEquals(listOf(CHAT to "On my way", CHAT to "A reply too long for one BLE frame."), delivered)
    }

    @Test
    fun `a reply to a key the phone never mirrored fails exactly like one to a key that is gone`() = conversation {
        shade[CHAT] = Shown(R1, "Are you coming?")
        shade[MAIL] = Shown(R1, "Your code is 482913")
        keptBack += MAIL
        inbox.sync()
        assertEquals(listOf(CHAT), mac.expect<BridgeMessage.ActiveSet>().notifications.map { it.key })

        mac.send(BridgeMessage.ReplyText(MAIL, "Probing"))
        val unmirrored = mac.expectSignal()
        mac.signal(BleSignal.Reply(MAIL_HASH, "Probing"))
        val unmirroredInline = mac.expectSignal()
        remove(CHAT)
        assertEquals(BleSignal.NotifRemoved(CHAT_HASH), mac.expectSignal())
        mac.send(BridgeMessage.ReplyText(CHAT, "Too late"))
        val gone = mac.expectSignal()

        assertEquals(BleSignal.ReplyError(MAIL_HASH, REPLY_ERROR_GONE), unmirrored)
        assertEquals(unmirrored, unmirroredInline)
        assertEquals(BleSignal.ReplyError(CHAT_HASH, REPLY_ERROR_GONE), gone)
        assertTrue(delivered.isEmpty(), "nothing reached a notification")
        assertTrue(shade.containsKey(MAIL))
    }

    @Test
    fun `a waited clip over Bluetooth keeps its age`() = conversation {
        mac.signal(BleSignal.ClipboardSmall("Notes from the call", sensitive = false, ageMs = 95_000))

        awaitUntil { clips.isNotEmpty() }
        assertEquals(listOf("Notes from the call" to 95_000L), clips)
    }

    @Test
    fun `a visit stays open while a frame trickles in and closes once the bytes stop`() {
        val text = "x".repeat(TRICKLED_BYTES)
        conversation(idleCloseMs = IDLE_MS) {
            val frame = BridgeFraming.encode(BridgeMessage.ClipboardSet(text, sensitive = false))
            frame.asList().chunked(frame.size / TRICKLE_STEPS + 1).forEach { chunk ->
                mac.write(chunk.toByteArray())
                Thread.sleep(IDLE_MS / 3)
            }

            awaitUntil { clips.isNotEmpty() }
            assertEquals(text, clips.single().first, "the clip arrived whole though it took longer than the idle time")
            assertEquals(BridgeMessage.Bye(BYE_IDLE), mac.expect<BridgeMessage.Bye>())
        }
        assertTrue(talk.idled)
    }

    private fun conversation(idleCloseMs: Long? = null, script: suspend () -> Unit) = runBlocking {
        talk = SessionConversation(
            connection = phone,
            open = { frame ->
                BleFrameCodec.open(key, BleDirection.MAC_TO_PHONE, frame, phoneWindow).also { opened ->
                    if (opened is BleOpened.Accepted) phoneWindow = phoneWindow.record(opened.frame.seq)
                }
            },
            inbox = inbox,
            idleCloseMs = idleCloseMs,
            log = {},
            info = {},
        )
        talk.send(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION))
        mac.expect<BridgeMessage.Hello>()
        mac.send(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION))
        assertEquals(SessionOpening.Established, sessionOpening(phone.read()))
        val session = async(Dispatchers.IO) { talk.run() }
        try {
            script()
        } finally {
            if (idleCloseMs == null) carrier.close()
            withTimeout(WAIT_MS) { session.await() }
        }
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "the condition did not hold within $WAIT_MS ms" }
            Thread.sleep(POLL_MS)
        }
    }

    private fun detail(key: String, shown: Shown) = BridgeMessage.NotifDetail(
        key = key, pkg = "com.example.chat", appLabel = "Example Chat", title = "Alice", text = shown.text,
        postedAt = shown.revision, canReply = true, isClearable = true, revision = shown.revision,
    )

    private inner class ScriptedMac {

        private val connection = BridgeConnection(toMac.input, toPhone.output, carrier)
        val received = LinkedBlockingQueue<BridgeMessage>()
        private var seq = 0L
        private var window = BridgeReplayWindow()

        init {
            thread(isDaemon = true, name = "scripted-mac") {
                try {
                    while (true) {
                        val inbound = connection.read() ?: break
                        if (inbound is BridgeInbound.Message) received.put(inbound.message)
                    }
                } catch (_: IOException) {
                    // The carrier closed.
                }
            }
        }

        fun send(message: BridgeMessage) = connection.send(message)

        fun write(bytes: ByteArray) {
            toPhone.output.write(bytes)
        }

        fun signal(signal: BleSignal, at: Long = seq++) {
            val frame = BleFrameCodec.seal(
                key, BleDirection.MAC_TO_PHONE, signal.type.code, at, encodeBleSignal(signal),
            )
            send(BridgeMessage.Signal(encodeBase64(frame)))
        }

        inline fun <reified T : BridgeMessage> expect(): T {
            val message = received.poll(WAIT_MS, TimeUnit.MILLISECONDS)
            assertTrue(message is T) { "expected ${T::class.simpleName}, got $message" }
            return message as T
        }

        fun expectSignal(): BleSignal {
            val opened = BleFrameCodec.open(
                key, BleDirection.PHONE_TO_MAC, decodeBase64(expect<BridgeMessage.Signal>().frame), window,
            )
            assertTrue(opened is BleOpened.Accepted) { "$opened" }
            val frame = (opened as BleOpened.Accepted).frame
            window = window.record(frame.seq)
            return checkNotNull(decodeBleSignal(frame.type, frame.plaintext))
        }
    }

    private companion object {
        const val CHAT = "0|com.example.chat|42|null|10123"
        const val CHAT_HASH = "54892d1f759b11f6"
        const val MAIL = "0|com.example.mail|7|null|10123"
        val MAIL_HASH = notificationKeyHash(MAIL)
        const val R1 = 1_791_820_800_000L
        const val R2 = 1_791_820_860_000L
        const val WAIT_MS = 5_000L
        const val POLL_MS = 10L
        const val IDLE_MS = 300L
        const val TRICKLE_STEPS = 8
        const val TRICKLED_BYTES = 4_000
        const val BYTE_MASK = 0xff
        const val LOCK_SEQ = 40L
        const val KEY_TAG = "key"
    }
}
