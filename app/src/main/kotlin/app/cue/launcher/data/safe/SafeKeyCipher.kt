package app.cue.launcher.data.safe

import android.app.Application
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import dev.zacsweers.metro.Inject
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed interface SafeKeyAccess {

    // Initialized, but every use of the key needs a user authentication first: hand it to the prompt.
    class Ready(val cipher: Cipher) : SafeKeyAccess

    // The key is gone, was invalidated, or the Keystore refused it; only the password helps.
    data object Lost : SafeKeyAccess

    // A key bound to the user's authentication cannot exist without a screen lock.
    data object NoScreenLock : SafeKeyAccess
}

// The CEK is sealed under a per-use key: every seal and every open needs a fresh authentication,
// biometric or the screen lock, which the cipher carries through BiometricPrompt's CryptoObject.
@Inject
class SafeKeyCipher(private val application: Application) {

    val screenLockSet: Boolean
        get() = application.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun forSealing(): SafeKeyAccess = guarded("seal") {
        if (!screenLockSet) return@guarded SafeKeyAccess.NoScreenLock
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: newKey())
        } catch (_: KeyPermanentlyInvalidatedException) {
            destroy()
            cipher.init(Cipher.ENCRYPT_MODE, newKey())
        }
        SafeKeyAccess.Ready(cipher)
    }

    fun forOpening(iv: ByteArray): SafeKeyAccess = guarded("open") {
        val key = existingKey()
        when {
            !screenLockSet -> SafeKeyAccess.NoScreenLock
            key == null -> SafeKeyAccess.Lost
            else -> try {
                SafeKeyAccess.Ready(
                    Cipher.getInstance(TRANSFORMATION).apply {
                        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                    },
                )
            } catch (_: KeyPermanentlyInvalidatedException) {
                destroy()
                SafeKeyAccess.Lost
            }
        }
    }

    fun destroy() {
        guarded("destroy") {
            KeyStore.getInstance(PROVIDER).apply { load(null) }.deleteEntry(ALIAS)
            SafeKeyAccess.Lost
        }
    }

    private inline fun guarded(what: String, block: () -> SafeKeyAccess): SafeKeyAccess =
        try {
            block()
        } catch (cause: GeneralSecurityException) {
            Log.w(TAG, "Could not $what the vault key (${cause::class.java.simpleName})")
            SafeKeyAccess.Lost
        } catch (cause: ProviderException) {
            Log.w(TAG, "Could not $what the vault key (${cause::class.java.simpleName})")
            SafeKeyAccess.Lost
        } catch (cause: IOException) {
            Log.w(TAG, "KeyStore unavailable (${cause::class.java.simpleName})")
            SafeKeyAccess.Lost
        }

    private fun existingKey(): SecretKey? {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        return (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun newKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        val strongBox = application.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        if (strongBox) {
            try {
                generator.init(spec(strongBox = true))
                return generator.generateKey()
            } catch (_: StrongBoxUnavailableException) {
                Log.i(TAG, "StrongBox refused the vault key, using the TEE")
            }
        }
        generator.init(spec(strongBox = false))
        return generator.generateKey()
    }

    // Not invalidated by a new fingerprint: the screen lock opens the key too, so that would protect
    // nothing and only force the password. Removing the screen lock invalidates it all the same.
    private fun spec(strongBox: Boolean) =
        KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, AUTHENTICATION)
            .setInvalidatedByBiometricEnrollment(false)
            .setIsStrongBoxBacked(strongBox)
            .build()

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "cue.safe.cek"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val AUTHENTICATION = KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
        const val TAG = "CueSafe"
    }
}
