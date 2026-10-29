package app.cue.launcher.data.bridge

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

interface BridgeKeySealer {

    fun seal(plain: ByteArray): BridgeSealedKey?

    fun open(sealed: BridgeSealedKey): ByteArray?

    fun destroy()
}

object BridgeKeyCipher : BridgeKeySealer {

    override fun seal(plain: ByteArray): BridgeSealedKey? = guarded("seal") {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        BridgeSealedKey(iv = encode(cipher.iv), data = encode(cipher.doFinal(plain)))
    }

    override fun open(sealed: BridgeSealedKey): ByteArray? = guarded("open") {
        existingKey()?.let { key ->
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, decode(sealed.iv)))
            cipher.doFinal(decode(sealed.data))
        }
    }

    // Unpairing and turning the bridge off delete the key, so nothing sealed before opens again; the
    // next seal creates a new one.
    override fun destroy() {
        guarded("destroy") { KeyStore.getInstance(PROVIDER).apply { load(null) }.deleteEntry(ALIAS) }
    }

    private inline fun <T> guarded(what: String, block: () -> T): T? = try {
        block()
    } catch (cause: GeneralSecurityException) {
        Log.w(TAG, "Could not $what K_ble (${cause::class.java.simpleName})")
        null
    } catch (cause: IllegalArgumentException) {
        Log.w(TAG, "Could not $what K_ble (${cause::class.java.simpleName})")
        null
    } catch (cause: IOException) {
        Log.w(TAG, "KeyStore unavailable (${cause::class.java.simpleName})")
        null
    }

    private fun existingKey(): SecretKey? {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        return (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun key(): SecretKey {
        existingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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
    private const val ALIAS = "cue.bridge.kble"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val TAG = "CueBridge"
}
