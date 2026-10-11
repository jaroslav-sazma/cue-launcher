package app.cue.launcher.system

import android.app.Application
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import app.cue.launcher.R
import app.cue.launcher.data.scenario.TapAction
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.suspendCancellableCoroutine
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

fun confirmationRequired(actions: List<TapAction>): Boolean =
    actions.any { it is TapAction.HaAction && it.confirm }

// The system confirmation prompt: biometrics first, the screen lock as its fallback. The dialog is
// rendered by SystemUI, so the application context is enough — no activity has to carry it.
@SingleIn(AppScope::class)
@Inject
class BiometricGate(private val application: Application) {

    suspend fun confirm(): ConfirmationResult {
        val manager = application.getSystemService(BiometricManager::class.java)
        return when (manager?.canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> prompt()
            else -> ConfirmationResult.UNAVAILABLE
        }
    }

    private suspend fun prompt(): ConfirmationResult = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        val callback = object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive) continuation.resume(ConfirmationResult.GRANTED)
            }

            // Covers the cancel button, a tap outside and a lockout alike. A failed attempt is not
            // an error: the prompt stays up and the user tries again.
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                if (continuation.isActive) continuation.resume(ConfirmationResult.DISMISSED)
            }
        }
        BiometricPrompt.Builder(application)
            .setTitle(application.getString(R.string.action_confirm_title))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
            .authenticate(signal, application.mainExecutor, callback)
    }

    private companion object {

        const val AUTHENTICATORS = Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
    }
}
