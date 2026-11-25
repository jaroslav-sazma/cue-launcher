package app.cue.launcher.system.safe

import androidx.annotation.StringRes
import app.cue.launcher.R
import app.cue.launcher.engine.safe.VaultFailure

sealed interface SafeState {

    data object NotConfigured : SafeState

    data object Locked : SafeState

    data class Unlocking(val step: UnlockStep) : SafeState

    // Wall-clock milliseconds, for showing; the expiry itself is counted on the elapsed-time clock.
    data class Unlocked(val unlockedAt: Long, val locksAt: Long, val writtenAt: Long) : SafeState

    // rejected: the last password given was wrong.
    data class NeedsPassword(val reason: PasswordReason, val rejected: Boolean = false) : SafeState

    // The read grant on the chosen file is gone, e.g. after Google Drive was reinstalled.
    data object ChooseFileAgain : SafeState

    data class Failed(val error: SafeError) : SafeState
}

enum class UnlockStep(@StringRes val message: Int) {
    READING(R.string.safe_step_reading),
    CHECKING_PASSWORD(R.string.safe_step_checking_password),
    AUTHENTICATING(R.string.safe_step_authenticating),
    DECRYPTING(R.string.safe_step_decrypting),
}

enum class PasswordReason(@StringRes val message: Int) {
    // No key on this phone yet: the first unlock, or after a reinstall or "forget".
    SET_UP(R.string.safe_password_set_up),
    // The Keystore key is gone or invalidated.
    KEY_LOST(R.string.safe_password_key_lost),
    // stale_key: the password, and with it the CEK, changed on the Mac.
    PASSWORD_CHANGED(R.string.safe_password_changed),
    // The file's vault_id is not the one whose key this phone holds.
    OTHER_VAULT(R.string.safe_password_other_vault),
    // No screen lock, so no key can be kept: the password opens the vault every time.
    NO_SCREEN_LOCK(R.string.safe_password_no_screen_lock),
}

enum class SafeError(@StringRes val message: Int) {
    NOT_A_VAULT(R.string.safe_error_not_a_vault),
    UNSUPPORTED_VERSION(R.string.safe_error_unsupported_version),
    DAMAGED(R.string.safe_error_damaged),
    UNREADABLE(R.string.safe_error_unreadable),
    NO_SPACE(R.string.safe_error_no_space),
    // The phone's own limit, SAFE_MAX_ENTRIES, not one of the format's categories.
    TOO_MANY_ENTRIES(R.string.safe_error_too_many_entries),
}

// What to tell the user about a state, or null where the state speaks for itself.
@get:StringRes
val SafeState.message: Int?
    get() = when (this) {
        SafeState.NotConfigured -> R.string.safe_not_configured
        SafeState.ChooseFileAgain -> R.string.safe_choose_again
        is SafeState.Unlocking -> step.message
        is SafeState.NeedsPassword -> if (rejected) R.string.safe_password_rejected else reason.message
        is SafeState.Failed -> error.message
        SafeState.Locked, is SafeState.Unlocked -> null
    }

// WRONG_PASSWORD and STALE_KEY ask for the password instead, so they never reach the user as an error.
internal fun safeErrorOf(failure: VaultFailure): SafeError = when (failure) {
    VaultFailure.NOT_A_VAULT -> SafeError.NOT_A_VAULT
    VaultFailure.UNSUPPORTED_VERSION -> SafeError.UNSUPPORTED_VERSION
    VaultFailure.BAD_HEADER,
    VaultFailure.WRONG_PASSWORD,
    VaultFailure.STALE_KEY,
    VaultFailure.CORRUPT,
    VaultFailure.BAD_ARCHIVE,
    -> SafeError.DAMAGED
}

// An open session wins over the settings, so whatever they say, the vault shows as open until it is
// locked.
internal fun safeStateOf(configured: Boolean, phase: SafeState, session: SafeSession?): SafeState = when {
    session != null -> SafeState.Unlocked(session.unlockedAt, session.locksAt, session.writtenAt)
    !configured -> SafeState.NotConfigured
    else -> phase
}
