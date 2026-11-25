package app.cue.launcher.engine.safe

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

// The categories and their codes are the ones docs/vault-format.md fixes for every implementation.
enum class VaultFailure(val code: String) {
    NOT_A_VAULT("not_a_vault"),
    UNSUPPORTED_VERSION("unsupported_version"),
    BAD_HEADER("bad_header"),
    WRONG_PASSWORD("wrong_password"),
    STALE_KEY("stale_key"),
    CORRUPT("corrupt"),
    BAD_ARCHIVE("bad_archive"),
}

class VaultException(val failure: VaultFailure, message: String) : Exception(message)

internal fun vaultFailure(failure: VaultFailure, message: String): Nothing = throw VaultException(failure, message)

internal const val VAULT_HEADER_SIZE = 136
internal const val VAULT_SEGMENT_SIZE = 65_536

internal const val VAULT_KEY_SIZE = 32
internal const val TAG_SIZE = 16
internal const val TAG_BITS = TAG_SIZE * 8
internal const val SEALED_SEGMENT_SIZE = VAULT_SEGMENT_SIZE + TAG_SIZE
internal const val MIN_SEALED_SEGMENT_SIZE = TAG_SIZE + 1
internal const val MAX_SEGMENTS = 1L shl 32 // the segment index is a u32 in the nonce
internal const val WRAP_AAD_SIZE = 61
internal const val AES_GCM = "AES/GCM/NoPadding"

internal val VAULT_MAGIC = "CUEVAULT".encodeToByteArray()
internal const val VAULT_VERSION: Byte = 1

private const val MAX_WRITTEN_AT = Long.MAX_VALUE / 1_000
private const val MIN_ITERATIONS = 100_000
private const val MAX_ITERATIONS = 10_000_000
private const val ITERATIONS_AT = 9
private const val SALT_AT = 13
private const val VAULT_ID_AT = 29
private const val KEY_ID_AT = 45
private const val WRITTEN_AT_AT = 61
private const val WRAP_NONCE_AT = 69
private const val WRAPPED_CEK_AT = 81
private const val NONCE_PREFIX_AT = 129
private const val ID_SIZE = 16
private const val WRAP_NONCE_SIZE = 12

class VaultHeader private constructor(internal val bytes: ByteArray) {

    private val fields = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    // A u32 above Int.MAX_VALUE reads negative, which the range check refuses just the same.
    val iterations: Int = fields.getInt(ITERATIONS_AT)
    val salt: ByteArray get() = range(SALT_AT, ID_SIZE)
    val vaultId: ByteArray get() = range(VAULT_ID_AT, ID_SIZE)
    val keyId: ByteArray get() = range(KEY_ID_AT, ID_SIZE)
    // A u64 of seconds, capped so that it still fits a Long once turned into milliseconds; anything
    // that large is past any real time anyway.
    val writtenAt: Long get() = fields.getLong(WRITTEN_AT_AT).toULong().coerceAtMost(MAX_WRITTEN_AT.toULong()).toLong()

    internal val wrapNonce: ByteArray get() = range(WRAP_NONCE_AT, WRAP_NONCE_SIZE)
    internal val sealedCek: ByteArray get() = range(WRAPPED_CEK_AT, VAULT_KEY_SIZE + TAG_SIZE)
    internal val noncePrefix: ByteArray get() = bytes.copyOfRange(NONCE_PREFIX_AT, VAULT_HEADER_SIZE)

    private fun range(offset: Int, size: Int) = bytes.copyOfRange(offset, offset + size)

    internal companion object {

        // Steps 1 to 4 of "Opening a vault" from the file's first bytes, read up to the header's size;
        // fewer were read only when the file ends there.
        fun parse(head: ByteArray, read: Int): VaultHeader {
            if (read < VAULT_MAGIC.size || !VAULT_MAGIC.indices.all { head[it] == VAULT_MAGIC[it] }) {
                vaultFailure(VaultFailure.NOT_A_VAULT, "no CUEVAULT signature")
            }
            if (read > VAULT_MAGIC.size && head[VAULT_MAGIC.size] != VAULT_VERSION) {
                vaultFailure(VaultFailure.UNSUPPORTED_VERSION, "format version ${head[VAULT_MAGIC.size]}")
            }
            if (read < VAULT_HEADER_SIZE) vaultFailure(VaultFailure.BAD_HEADER, "a file of $read bytes")
            val header = VaultHeader(head.copyOf(VAULT_HEADER_SIZE))
            if (header.iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
                vaultFailure(VaultFailure.BAD_HEADER, "${header.iterations.toUInt()} key derivation iterations")
            }
            return header
        }

        fun read(input: InputStream): VaultHeader {
            val head = ByteArray(VAULT_HEADER_SIZE)
            return parse(head, input.readNBytes(head, 0, head.size))
        }
    }
}

// Step 5 of "Opening a vault": n and P from the file's length alone, or bad_header when no layout of
// sealed segments fits it.
class VaultLayout private constructor(val header: VaultHeader, val segments: Long, val plaintextSize: Long) {

    internal fun sealedAt(index: Long): Long = VAULT_HEADER_SIZE + index * SEALED_SEGMENT_SIZE

    internal fun plaintextLength(index: Long): Int =
        if (index < segments - 1) VAULT_SEGMENT_SIZE else (plaintextSize - (segments - 1) * VAULT_SEGMENT_SIZE).toInt()

    companion object {

        fun of(header: VaultHeader, fileSize: Long): VaultLayout {
            val payload = fileSize - VAULT_HEADER_SIZE
            val count = payload / SEALED_SEGMENT_SIZE + if (payload % SEALED_SEGMENT_SIZE == 0L) 0 else 1
            val last = payload - (count - 1) * SEALED_SEGMENT_SIZE
            if (count <= 0L || last < MIN_SEALED_SEGMENT_SIZE) {
                vaultFailure(VaultFailure.BAD_HEADER, "a payload of $payload bytes is no sequence of segments")
            }
            if (count > MAX_SEGMENTS) vaultFailure(VaultFailure.BAD_HEADER, "a payload of $count segments")
            return VaultLayout(header, count, payload - TAG_SIZE * count)
        }
    }
}
