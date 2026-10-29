package app.cue.launcher.system.bridge

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal class PairingSecrets(val bleKey: ByteArray, val sas: String, val confirmKey: ByteArray)

internal enum class PairingRole(val proofData: String) {
    PHONE("phone"),
    MAC("mac"),
}

private const val MAX_FIELD_BYTES = 0xFFFF

internal fun lengthPrefixed(vararg fields: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    fields.forEach { field ->
        require(field.size <= MAX_FIELD_BYTES) { "a ${field.size}-byte field does not fit a u16 length" }
        out.write(field.size ushr Byte.SIZE_BITS)
        out.write(field.size)
        out.write(field)
    }
    return out.toByteArray()
}

internal object PairingDerivation {

    private const val SAS_OKM_BYTES = 4
    private const val HMAC = "HmacSHA256"

    fun commitment(phonePublicKey: ByteArray): ByteArray {
        require(phonePublicKey.size == X25519_KEY_BYTES) { "an X25519 public key has $X25519_KEY_BYTES bytes" }
        return sha256(lengthPrefixed(PAIRING_COMMIT_LABEL.encodeToByteArray(), phonePublicKey))
    }

    fun transcript(
        macFingerprint: ByteArray,
        phoneFingerprint: ByteArray,
        macPublicKey: ByteArray,
        phonePublicKey: ByteArray,
    ): ByteArray {
        require(macFingerprint.size == FINGERPRINT_BYTES && phoneFingerprint.size == FINGERPRINT_BYTES) {
            "a fingerprint has $FINGERPRINT_BYTES bytes"
        }
        require(macPublicKey.size == X25519_KEY_BYTES && phonePublicKey.size == X25519_KEY_BYTES) {
            "an X25519 public key has $X25519_KEY_BYTES bytes"
        }
        val label = PAIRING_TRANSCRIPT_LABEL.encodeToByteArray()
        return lengthPrefixed(label, macFingerprint, phoneFingerprint, macPublicKey, phonePublicKey)
    }

    fun transcriptHash(
        macFingerprint: ByteArray,
        phoneFingerprint: ByteArray,
        macPublicKey: ByteArray,
        phonePublicKey: ByteArray,
    ): ByteArray = sha256(transcript(macFingerprint, phoneFingerprint, macPublicKey, phonePublicKey))

    fun derive(sharedSecret: ByteArray, transcriptHash: ByteArray): PairingSecrets = PairingSecrets(
        bleKey(sharedSecret, transcriptHash),
        sas(sharedSecret, transcriptHash),
        confirmKey(sharedSecret, transcriptHash),
    )

    fun bleKey(sharedSecret: ByteArray, transcriptHash: ByteArray): ByteArray =
        Hkdf.sha256(sharedSecret, transcriptHash, PAIRING_KBLE_INFO.encodeToByteArray(), BLE_KEY_BYTES)

    fun confirmKey(sharedSecret: ByteArray, transcriptHash: ByteArray): ByteArray =
        Hkdf.sha256(sharedSecret, transcriptHash, PAIRING_CONFIRM_INFO.encodeToByteArray(), CONFIRM_KEY_BYTES)

    fun sas(sharedSecret: ByteArray, transcriptHash: ByteArray): String {
        val okm = Hkdf.sha256(sharedSecret, transcriptHash, PAIRING_SAS_INFO.encodeToByteArray(), SAS_OKM_BYTES)
        val value = Integer.toUnsignedLong(ByteBuffer.wrap(okm).int) % SAS_MODULUS
        return value.toString().padStart(SAS_DIGITS, '0')
    }

    fun proof(confirmKey: ByteArray, sender: PairingRole): ByteArray =
        Mac.getInstance(HMAC).apply { init(SecretKeySpec(confirmKey, HMAC)) }
            .doFinal(sender.proofData.encodeToByteArray())
}
