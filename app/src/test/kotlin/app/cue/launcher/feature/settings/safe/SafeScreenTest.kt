package app.cue.launcher.feature.settings.safe

import app.cue.launcher.R
import app.cue.launcher.system.safe.PasswordReason
import app.cue.launcher.system.safe.SafeError
import app.cue.launcher.system.safe.SafeState
import app.cue.launcher.system.safe.UnlockStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SafeScreenTest {

    @Test
    fun `the state line names what the vault is waiting for`() {
        assertEquals(R.string.safe_state_locked, safeStateLineRes(SafeState.Locked))
        assertEquals(R.string.safe_state_unlocked, safeStateLineRes(SafeState.Unlocked(0L, 1L, 2L)))
        assertEquals(R.string.safe_state_password, safeStateLineRes(SafeState.NeedsPassword(PasswordReason.SET_UP)))
        assertEquals(R.string.safe_state_unavailable, safeStateLineRes(SafeState.ChooseFileAgain))
        assertEquals(R.string.safe_state_unavailable, safeStateLineRes(SafeState.Failed(SafeError.DAMAGED)))
    }

    @Test
    fun `an unlock in progress shows its step`() {
        UnlockStep.entries.forEach { step ->
            assertEquals(step.message, safeStateLineRes(SafeState.Unlocking(step)), step.name)
        }
    }
}
