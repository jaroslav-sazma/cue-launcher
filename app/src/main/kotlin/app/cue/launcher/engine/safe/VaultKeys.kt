package app.cue.launcher.engine.safe

import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.text.Normalizer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object VaultKeys {

    private const val HMAC = "HmacSHA256"
    private val FIRST_BLOCK = byteArrayOf(0, 0, 0, 1)

    internal fun canonicalPassword(password: CharSequence): ByteArray {
        val normalized = Normalizer.normalize(password, Normalizer.Form.NFC)
        // A lone surrogate has no UTF-8 form, so no writer can have set such a password.
        val encoded = try {
            Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(normalized))
        } catch (_: CharacterCodingException) {
            vaultFailure(VaultFailure.WRONG_PASSWORD, "the password is not valid Unicode")
        }
        return ByteArray(encoded.remaining()).also {
            encoded.get(it)
            if (encoded.hasArray()) encoded.array().fill(0)
        }
    }

    // PBEKeySpec takes chars and leaves their encoding to the provider, while the spec fixes the bytes;
    // HMAC-SHA256 also takes an empty password, which SecretKeySpec refuses but HMAC pads to the same
    // key as a single zero byte. One block is all a 32-byte KEK needs.
    internal fun kek(password: CharSequence, header: VaultHeader): ByteArray {
        val bytes = canonicalPassword(password)
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(bytes.takeIf { it.isNotEmpty() } ?: ByteArray(1), HMAC))
        bytes.fill(0)
        mac.update(header.salt)
        val u = mac.doFinal(FIRST_BLOCK)
        val kek = u.copyOf()
        repeat(header.iterations - 1) {
            mac.update(u)
            mac.doFinal(u, 0)
            for (i in kek.indices) kek[i] = (kek[i].toInt() xor u[i].toInt()).toByte()
        }
        u.fill(0)
        return kek
    }

    internal fun unwrap(header: VaultHeader, kek: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_BITS, header.wrapNonce))
        cipher.updateAAD(header.bytes, 0, WRAP_AAD_SIZE)
        return try {
            cipher.doFinal(header.sealedCek)
        } catch (_: AEADBadTagException) {
            vaultFailure(VaultFailure.WRONG_PASSWORD, "wrong password, or the header is damaged")
        }
    }

    fun cek(header: VaultHeader, password: CharSequence): ByteArray {
        val kek = kek(password, header)
        return try {
            unwrap(header, kek)
        } finally {
            kek.fill(0)
        }
    }

    // For a stored key, before it is opened: the phone's is sealed under the Keystore, so a key that
    // no longer fits the file must fail before the prompt. key_id is not secret, so a plain comparison
    // is fine.
    fun checkStoredKey(header: VaultHeader, keyId: ByteArray) {
        if (!keyId.contentEquals(header.keyId)) {
            vaultFailure(VaultFailure.STALE_KEY, "the stored key belongs to another key_id")
        }
    }
}
