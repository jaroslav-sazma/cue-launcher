package app.cue.launcher.data.bridge

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

const val BRIDGE_NO_SEQ = -1L

const val BRIDGE_MAX_SEQ = 0xFFFF_FFFFL

const val BRIDGE_REPLAY_WINDOW = 64

const val BRIDGE_SEQ_BLOCK = 64L

// Spec, replay window: bit i of seen stands for highest - i.
@Serializable
data class BridgeReplayWindow(val highest: Long = BRIDGE_NO_SEQ, val seen: Long = 0) {

    fun permits(seq: Long): Boolean {
        val behind = highest - seq
        return when {
            highest == BRIDGE_NO_SEQ || behind < 0 -> true
            behind >= BRIDGE_REPLAY_WINDOW -> false
            else -> (seen and (1L shl behind.toInt())) == 0L
        }
    }

    fun record(seq: Long): BridgeReplayWindow {
        val ahead = seq - highest
        return when {
            highest == BRIDGE_NO_SEQ || ahead >= BRIDGE_REPLAY_WINDOW -> BridgeReplayWindow(seq, 1L)
            ahead > 0 -> BridgeReplayWindow(seq, (seen shl ahead.toInt()) or 1L)
            else -> copy(seen = seen or (1L shl (-ahead).toInt()))
        }
    }
}

// The key tag ties the state to one K_ble: a commit that dies between this file and the settings
// leaves state that matches neither key, so the pairing is unusable instead of starting at zero
// next to a kept key.
@Serializable
data class BridgeSequenceState(
    val macFingerprint: String,
    val keyTag: String,
    val outgoingBound: Long = 0,
    val incoming: BridgeReplayWindow = BridgeReplayWindow(),
)

@Serializable
data class BridgeSequenceFile(val state: BridgeSequenceState? = null)

object BridgeSequenceSerializer : Serializer<BridgeSequenceFile> {

    private val json = Json { encodeDefaults = true }

    override val defaultValue: BridgeSequenceFile = BridgeSequenceFile()

    override suspend fun readFrom(input: InputStream): BridgeSequenceFile =
        try {
            json.decodeFromString<BridgeSequenceFile>(input.readBytes().decodeToString())
        } catch (cause: SerializationException) {
            throw CorruptionException("Could not read the bridge sequence state", cause)
        } catch (cause: IllegalArgumentException) {
            throw CorruptionException("Could not read the bridge sequence state", cause)
        }

    override suspend fun writeTo(t: BridgeSequenceFile, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}

fun bridgeKeyTag(bleKey: ByteArray): String =
    MessageDigest.getInstance("SHA-256").run {
        update(KEY_TAG_LABEL)
        update(bleKey)
        digest()
    }.copyOf(KEY_TAG_BYTES).toHexString()

private val KEY_TAG_LABEL = "cue-bridge/sequence-state".encodeToByteArray()

private const val KEY_TAG_BYTES = 16

// Outgoing numbers are reserved a block at a time: the bound is on disk before any number below it
// leaves, so a crash costs a gap and never a nonce used twice under one K_ble. Its own file, so a
// frame never rewrites the settings that the whole launcher collects.
@SingleIn(AppScope::class)
@Inject
class BridgeSequences(private val dataStore: DataStore<BridgeSequenceFile>) {

    private class Block(val macFingerprint: String, val keyTag: String, var next: Long, val bound: Long)

    private val lock = Mutex()

    private var block: Block? = null

    suspend fun matches(macFingerprint: String, keyTag: String): Boolean =
        stateFor(macFingerprint, keyTag) != null

    suspend fun start(macFingerprint: String, keyTag: String) = lock.withLock {
        block = null
        dataStore.updateData { BridgeSequenceFile(BridgeSequenceState(macFingerprint, keyTag)) }
    }

    suspend fun wipe() = lock.withLock {
        block = null
        dataStore.updateData { BridgeSequenceFile() }
    }

    suspend fun reserveOutgoing(macFingerprint: String, keyTag: String): Long? = lock.withLock {
        val current = block?.takeIf { it.macFingerprint == macFingerprint && it.keyTag == keyTag && it.next < it.bound }
        if (current != null) return@withLock current.next++
        var reserved: Block? = null
        dataStore.updateData { file ->
            val state = file.state?.takeIf { it.owns(macFingerprint, keyTag) && it.outgoingBound <= BRIDGE_MAX_SEQ }
            if (state == null) {
                file
            } else {
                val bound = minOf(state.outgoingBound + BRIDGE_SEQ_BLOCK, BRIDGE_MAX_SEQ + 1)
                reserved = Block(macFingerprint, keyTag, state.outgoingBound, bound)
                file.copy(state = state.copy(outgoingBound = bound))
            }
        }
        block = reserved
        reserved?.let { it.next++ }
    }

    suspend fun incomingWindow(macFingerprint: String, keyTag: String): BridgeReplayWindow? =
        stateFor(macFingerprint, keyTag)?.incoming

    // False when the window no longer lets seq in, or the pairing is gone: the frame was accepted
    // elsewhere first and must not be acted on twice.
    suspend fun recordIncoming(macFingerprint: String, keyTag: String, seq: Long): Boolean {
        var recorded = false
        dataStore.updateData { file ->
            val state = file.state?.takeIf { it.owns(macFingerprint, keyTag) && it.incoming.permits(seq) }
            recorded = state != null
            state?.let { file.copy(state = it.copy(incoming = it.incoming.record(seq))) } ?: file
        }
        return recorded
    }

    private suspend fun stateFor(macFingerprint: String, keyTag: String): BridgeSequenceState? =
        dataStore.data.first().state?.takeIf { it.owns(macFingerprint, keyTag) }

    private fun BridgeSequenceState.owns(macFingerprint: String, keyTag: String) =
        this.macFingerprint == macFingerprint && this.keyTag == keyTag
}
