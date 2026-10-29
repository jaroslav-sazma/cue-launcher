package app.cue.launcher.system.bridge

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object Hkdf {

    private const val ALGORITHM = "HmacSHA256"
    private const val HASH_BYTES = 32
    private const val MAX_BLOCKS = 255

    fun sha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray =
        expand(extract(salt, ikm), info, length)

    // RFC 5869 §2.2: an absent salt is HashLen zero bytes; SecretKeySpec also refuses an empty key.
    fun extract(salt: ByteArray, ikm: ByteArray): ByteArray =
        hmac(if (salt.isEmpty()) ByteArray(HASH_BYTES) else salt).doFinal(ikm)

    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..MAX_BLOCKS * HASH_BYTES) { "HKDF output length $length is out of range" }
        val mac = hmac(prk)
        val okm = ByteArray(length)
        var block = ByteArray(0)
        var written = 0
        var counter = 1
        while (written < length) {
            mac.update(block)
            mac.update(info)
            mac.update(counter.toByte())
            block = mac.doFinal()
            val take = minOf(block.size, length - written)
            block.copyInto(okm, written, 0, take)
            written += take
            counter++
        }
        return okm
    }

    private fun hmac(key: ByteArray): Mac = Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(key, ALGORITHM)) }
}
