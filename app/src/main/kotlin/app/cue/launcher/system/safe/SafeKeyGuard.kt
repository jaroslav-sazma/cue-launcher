package app.cue.launcher.system.safe

import app.cue.launcher.R
import app.cue.launcher.data.safe.SafeKeyAccess
import app.cue.launcher.data.safe.SafeKeyCipher
import app.cue.launcher.system.BiometricGate
import app.cue.launcher.system.CipherAuthorization
import dev.zacsweers.metro.Inject
import java.security.GeneralSecurityException
import java.security.ProviderException

sealed interface KeySealing {
    class Sealed(val iv: ByteArray, val sealedCek: ByteArray) : KeySealing
    data object Dismissed : KeySealing
    // No screen lock, or the Keystore refused: the vault opens this once without a key kept.
    data object Impossible : KeySealing
}

sealed interface KeyOpening {
    class Opened(val cek: ByteArray) : KeyOpening
    data object Dismissed : KeyOpening
    data object Lost : KeyOpening
    data object NoScreenLock : KeyOpening
}

interface SafeKeyGuard {

    val canKeep: Boolean

    suspend fun seal(cek: ByteArray): KeySealing

    suspend fun open(iv: ByteArray, sealedCek: ByteArray): KeyOpening

    fun forget()
}

@Inject
class KeystoreSafeKeyGuard(private val cipher: SafeKeyCipher, private val gate: BiometricGate) : SafeKeyGuard {

    override val canKeep: Boolean get() = cipher.screenLockSet

    override suspend fun seal(cek: ByteArray): KeySealing {
        val access = cipher.forSealing() as? SafeKeyAccess.Ready ?: return KeySealing.Impossible
        return when (val authorization = gate.authorize(access.cipher, R.string.safe_prompt_remember)) {
            is CipherAuthorization.Granted -> try {
                KeySealing.Sealed(authorization.cipher.iv, authorization.cipher.doFinal(cek))
            } catch (_: GeneralSecurityException) {
                KeySealing.Impossible
            } catch (_: ProviderException) {
                KeySealing.Impossible
            }
            CipherAuthorization.Dismissed -> KeySealing.Dismissed
            CipherAuthorization.Unavailable -> KeySealing.Impossible
        }
    }

    override suspend fun open(iv: ByteArray, sealedCek: ByteArray): KeyOpening =
        when (val access = cipher.forOpening(iv)) {
            SafeKeyAccess.Lost -> KeyOpening.Lost
            SafeKeyAccess.NoScreenLock -> KeyOpening.NoScreenLock
            is SafeKeyAccess.Ready -> opened(gate.authorize(access.cipher, R.string.safe_prompt_unlock), sealedCek)
        }

    private fun opened(authorization: CipherAuthorization, sealedCek: ByteArray): KeyOpening = when (authorization) {
        is CipherAuthorization.Granted -> try {
            KeyOpening.Opened(authorization.cipher.doFinal(sealedCek))
        } catch (_: GeneralSecurityException) {
            KeyOpening.Lost
        } catch (_: ProviderException) {
            KeyOpening.Lost
        }
        CipherAuthorization.Dismissed -> KeyOpening.Dismissed
        // Biometrics and screen lock both gone since the key was kept.
        CipherAuthorization.Unavailable -> KeyOpening.NoScreenLock
    }

    override fun forget() = cipher.destroy()
}
