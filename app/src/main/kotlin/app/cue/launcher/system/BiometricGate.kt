package app.cue.launcher.system

import android.app.Application
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.annotation.StringRes
import app.cue.launcher.R
import app.cue.launcher.data.scenario.TapAction
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import kotlin.coroutines.resume

enum class ConfirmationResult {

    GRANTED,

    // The prompt was shown and not passed — cancelled, locked out, or failed. The system prompt has
    // already told the user; reporting it again as a toast would only scold them for backing out.
    DISMISSED,

    // No biometrics and no screen lock to fall back on, so the gate cannot be held. The action is
    // refused rather than let through: a device without a lock is where a stray tap costs the most.
    UNAVAILABLE,
}

sealed interface CipherAuthorization {

    // The cipher the prompt authorized, ready for exactly one operation.
    class Granted(val cipher: Cipher) : CipherAuthorization

    data object Dismissed : CipherAuthorization

    data object Unavailable : CipherAuthorization
}

fun confirmationRequired(actions: List<TapAction>): Boolean =
    actions.any { it is TapAction.HaAction && it.confirm }

// The system confirmation prompt: biometrics first, the screen lock as its fallback. The dialog is
// rendered by SystemUI, so the application context is enough — no activity has to carry it.
@SingleIn(AppScope::class)
@Inject
class BiometricGate(private val application: Application) {

    suspend fun confirm(): ConfirmationResult = when {
        !available() -> ConfirmationResult.UNAVAILABLE
        prompt(null, R.string.action_confirm_title) != null -> ConfirmationResult.GRANTED
        else -> ConfirmationResult.DISMISSED
    }

    // For a Keystore key bound to the user's authentication: the key opens only for the cipher the
    // prompt hands back.
    suspend fun authorize(cipher: Cipher, @StringRes title: Int): CipherAuthorization {
        if (!available()) return CipherAuthorization.Unavailable
        val authorized = prompt(BiometricPrompt.CryptoObject(cipher), title)?.cryptoObject?.cipher
        return authorized?.let(CipherAuthorization::Granted) ?: CipherAuthorization.Dismissed
    }

    private fun available(): Boolean =
        application.getSystemService(BiometricManager::class.java)?.canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private suspend fun prompt(
        crypto: BiometricPrompt.CryptoObject?,
        @StringRes title: Int,
    ): BiometricPrompt.AuthenticationResult? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            val callback = object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(result)
                }

                // Covers the cancel button, a tap outside and a lockout alike. A failed attempt is not
                // an error: the prompt stays up and the user tries again.
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            val prompt = BiometricPrompt.Builder(application)
                .setTitle(application.getString(title))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
            if (crypto == null) {
                prompt.authenticate(signal, application.mainExecutor, callback)
            } else {
                prompt.authenticate(crypto, signal, application.mainExecutor, callback)
            }
        }
    }

    private companion object {

        const val AUTHENTICATORS = Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
    }
}
