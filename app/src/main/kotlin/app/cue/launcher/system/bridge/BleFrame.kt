package app.cue.launcher.system.bridge

import app.cue.launcher.data.bridge.BRIDGE_MAX_SEQ
import app.cue.launcher.data.bridge.BridgeReplayWindow
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.NoSuchAlgorithmException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal enum class BleDirection(val nonceId: Int) {
    PHONE_TO_MAC(1),
    MAC_TO_PHONE(2),
}

internal class BleFrame(val type: Int, val seq: Long, val plaintext: ByteArray)

internal enum class BleRejection { MALFORMED, REPLAY, AUTHENTICATION }

internal sealed interface BleOpened {

    class Accepted(val frame: BleFrame) : BleOpened

    data class Rejected(val reason: BleRejection) : BleOpened
}

internal object ChaCha20Poly1305 {

    const val KEY_BYTES = 32
    const val NONCE_BYTES = 12
    const val TAG_BYTES = 16

    private const val KEY_ALGORITHM = "ChaCha20"

    // Conscrypt names the AEAD "ChaCha20/Poly1305/NoPadding", the JDK only "ChaCha20-Poly1305".
    private val transformation: String by lazy {
        listOf("ChaCha20/Poly1305/NoPadding", "ChaCha20-Poly1305").firstOrNull { name ->
            try {
                Cipher.getInstance(name)
                true
            } catch (_: GeneralSecurityException) {
                false
            }
        } ?: throw NoSuchAlgorithmException("no ChaCha20-Poly1305 provider")
    }

    fun seal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray =
        cipher(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(plaintext)

    fun open(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray =
        cipher(Cipher.DECRYPT_MODE, key, nonce, aad).doFinal(ciphertext)

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher {
        require(key.size == KEY_BYTES) { "a ChaCha20-Poly1305 key has $KEY_BYTES bytes" }
        require(nonce.size == NONCE_BYTES) { "a ChaCha20-Poly1305 nonce has $NONCE_BYTES bytes" }
        return Cipher.getInstance(transformation).apply {
            init(mode, SecretKeySpec(key, KEY_ALGORITHM), IvParameterSpec(nonce))
            updateAAD(aad)
        }
    }
}

// The direction sits in the nonce, so a frame can never be replayed back to its own sender, and seq
// is the only per-direction state: a sender that ever reuses one under the same K_ble breaks the AEAD.
internal object BleFrameCodec {

    const val MAX_FRAME_BYTES = 180
    const val HEADER_BYTES = 5
    const val MAX_PLAINTEXT_BYTES = MAX_FRAME_BYTES - HEADER_BYTES - ChaCha20Poly1305.TAG_BYTES
    const val MAX_SEQ = BRIDGE_MAX_SEQ

    private const val MIN_FRAME_BYTES = HEADER_BYTES + ChaCha20Poly1305.TAG_BYTES
    private const val MAX_TYPE = 0xFF

    fun nonce(direction: BleDirection, seq: Long): ByteArray {
        require(seq in 0..MAX_SEQ) { "seq $seq is out of range" }
        return ByteBuffer.allocate(ChaCha20Poly1305.NONCE_BYTES)
            .putInt(direction.nonceId).putInt(0).putInt(seq.toInt()).array()
    }

    fun seal(key: ByteArray, direction: BleDirection, type: Int, seq: Long, plaintext: ByteArray): ByteArray {
        require(type in 0..MAX_TYPE) { "type $type does not fit a byte" }
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "a ${plaintext.size}-byte plaintext does not fit a frame" }
        val typeByte = byteArrayOf(type.toByte())
        val ciphertext = ChaCha20Poly1305.seal(key, nonce(direction, seq), typeByte, plaintext)
        return ByteBuffer.allocate(HEADER_BYTES + ciphertext.size)
            .put(typeByte).putInt(seq.toInt()).put(ciphertext).array()
    }

    // The window is only read; the caller records an accepted seq in it.
    fun open(key: ByteArray, direction: BleDirection, frame: ByteArray, window: BridgeReplayWindow): BleOpened {
        val seq = if (frame.size in MIN_FRAME_BYTES..MAX_FRAME_BYTES) {
            Integer.toUnsignedLong(ByteBuffer.wrap(frame, 1, Int.SIZE_BYTES).int)
        } else {
            null
        }
        val plaintext = seq?.takeIf(window::permits)?.let { decrypt(key, direction, frame, it) }
        return when {
            seq == null -> BleOpened.Rejected(BleRejection.MALFORMED)
            !window.permits(seq) -> BleOpened.Rejected(BleRejection.REPLAY)
            plaintext == null -> BleOpened.Rejected(BleRejection.AUTHENTICATION)
            else -> BleOpened.Accepted(BleFrame(frame[0].toInt() and MAX_TYPE, seq, plaintext))
        }
    }

    private fun decrypt(key: ByteArray, direction: BleDirection, frame: ByteArray, seq: Long): ByteArray? = try {
        val ciphertext = frame.copyOfRange(HEADER_BYTES, frame.size)
        ChaCha20Poly1305.open(key, nonce(direction, seq), frame.copyOf(1), ciphertext)
    } catch (_: GeneralSecurityException) {
        null
    }
}
