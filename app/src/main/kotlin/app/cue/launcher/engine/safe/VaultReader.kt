package app.cue.launcher.engine.safe

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface VaultSource : Closeable {

    // Fills buffer[0, length) with the file's bytes from position on; EOFException when the file ends first.
    fun read(position: Long, buffer: ByteArray, length: Int)
}

// A vault file of known length, read at any position. Every segment is verified each time it is
// decrypted, so bytes that changed under the reader fail as corrupt instead of reaching the caller.
// Nothing here is synchronized: one thread at a time. The reader owns the source and closes it, and
// the caller may wipe the CEK once the constructor returns, as the cipher keeps its own copy.
class VaultReader(private val layout: VaultLayout, private val source: VaultSource, cek: ByteArray) : Closeable {

    private val cipher = SegmentCipher(cek, layout.header)
    private val sealed = ByteArray(SEALED_SEGMENT_SIZE)
    private val cache = SegmentCache(CACHED_SEGMENTS)

    val plaintextSize: Long get() = layout.plaintextSize

    @Volatile
    var closed = false
        private set

    // Every segment in order, each verified before the indexer sees it. A damaged segment ends the pass
    // at once as corrupt; an archive that breaks the profile is reported only once all segments verified.
    // More than entryLimit entries end it at once with VaultLimitReached.
    fun index(entryLimit: Int = Int.MAX_VALUE, onSegment: () -> Unit = {}): VaultArchive {
        val indexer = ArchiveIndexer(layout.plaintextSize, entryLimit)
        val plain = ByteArray(VAULT_SEGMENT_SIZE)
        try {
            for (index in 0 until layout.segments) {
                onSegment()
                indexer.feed(plain, open(index, plain))
            }
        } finally {
            plain.fill(0)
        }
        return indexer.finish()
    }

    fun read(position: Long, buffer: ByteArray, offset: Int, length: Int) {
        check(!closed) { "the vault is closed" }
        require(position >= 0 && length >= 0 && position <= plaintextSize - length) {
            "$length bytes at $position of $plaintextSize"
        }
        var done = 0
        while (done < length) {
            val at = position + done
            val index = at / VAULT_SEGMENT_SIZE
            val within = (at % VAULT_SEGMENT_SIZE).toInt()
            val count = minOf(length - done, layout.plaintextLength(index) - within)
            segment(index).copyInto(buffer, offset + done, within, within + count)
            done += count
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        cache.clear()
        source.close()
    }

    private fun segment(index: Long): ByteArray {
        cache[index]?.let { return it }
        val plain = cache.spare()
        var opened = false
        try {
            open(index, plain)
            opened = true
        } finally {
            if (!opened) plain.fill(0)
        }
        cache[index] = plain
        return plain
    }

    private fun open(index: Long, plain: ByteArray): Int {
        val length = layout.plaintextLength(index) + TAG_SIZE
        source.read(layout.sealedAt(index), sealed, length)
        return cipher.open(sealed, length, index, last = index == layout.segments - 1, plain)
            ?: vaultFailure(VaultFailure.CORRUPT, "segment $index does not authenticate")
    }

    private companion object {
        const val CACHED_SEGMENTS = 16
    }
}

internal class SegmentCache(private val capacity: Int) {

    private val segments = LinkedHashMap<Long, ByteArray>(capacity, 1f, true)

    operator fun get(index: Long): ByteArray? = segments[index]

    operator fun set(index: Long, plain: ByteArray) {
        segments[index] = plain
    }

    fun spare(): ByteArray {
        if (segments.size < capacity) return ByteArray(VAULT_SEGMENT_SIZE)
        val eldest = segments.values.iterator()
        return eldest.next().also {
            eldest.remove()
            it.fill(0)
        }
    }

    fun clear() {
        segments.values.forEach { it.fill(0) }
        segments.clear()
    }
}

private const val NONCE_SIZE = 12
private const val NONCE_PREFIX_SIZE = 7
private const val NONCE_FLAG_AT = 11
private const val LAST: Byte = 1
private const val NOT_LAST: Byte = 0

private class SegmentCipher(cek: ByteArray, private val header: VaultHeader) {

    private val cipher = Cipher.getInstance(AES_GCM)
    private val key = SecretKeySpec(cek, "AES")
    private val nonce = ByteBuffer.allocate(NONCE_SIZE).order(ByteOrder.LITTLE_ENDIAN).put(header.noncePrefix)

    fun open(sealed: ByteArray, length: Int, index: Long, last: Boolean, plain: ByteArray): Int? {
        nonce.putInt(NONCE_PREFIX_SIZE, index.toInt()).put(NONCE_FLAG_AT, if (last) LAST else NOT_LAST)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce.array()))
        cipher.updateAAD(header.bytes)
        return try {
            cipher.doFinal(sealed, 0, length, plain, 0)
        } catch (_: AEADBadTagException) {
            null
        }
    }
}
