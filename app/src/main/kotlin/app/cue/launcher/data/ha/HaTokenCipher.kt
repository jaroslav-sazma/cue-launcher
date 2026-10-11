package app.cue.launcher.data.ha

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object HaTokenCipher {

    fun seal(plain: String): HaSealedToken? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        HaSealedToken(iv = encode(cipher.iv), data = encode(data))
    } catch (cause: GeneralSecurityException) {
        Log.w(TAG, "Failed to encrypt token (${cause::class.java.simpleName})")
        null
    } catch (cause: IOException) {

        Log.w(TAG, "KeyStore unavailable (${cause::class.java.simpleName})")
        null
    }

    fun open(sealed: HaSealedToken): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, decode(sealed.iv)))
        cipher.doFinal(decode(sealed.data)).toString(Charsets.UTF_8)
    } catch (cause: GeneralSecurityException) {
        Log.w(TAG, "Failed to decrypt token — user will re-enter it (${cause::class.java.simpleName})")
        null
    } catch (cause: IllegalArgumentException) {

        Log.w(TAG, "Stored token is unreadable (${cause::class.java.simpleName})")
        null
    } catch (cause: IOException) {
        Log.w(TAG, "KeyStore unavailable (${cause::class.java.simpleName})")
        null
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "cue.ha.token"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val TAG = "HaToken"
}
