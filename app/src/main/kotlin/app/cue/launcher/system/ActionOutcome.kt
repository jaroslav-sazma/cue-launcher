package app.cue.launcher.system

import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.system.bridge.LockMacOutcome
import app.cue.launcher.system.safe.SafeFilesOpened
import app.cue.launcher.system.safe.SafeState

enum class ActionOutcome {

    DONE,

    TARGET_MISSING,

    FAILED,

    PERMISSION_REQUIRED,

    HA_NOT_CONFIGURED,

    HA_UNREACHABLE,

    HA_REJECTED,

    // Sent, not answered in time. Reported as its own outcome because the action may well have run:
    // Home Assistant finishes a service call before it replies.
    HA_TIMEOUT,

    HA_LOCAL_NETWORK,

    NOTIFICATION_GONE,

    // The user backed out of the confirmation prompt. The prompt itself was the feedback; this
    // outcome deliberately carries no message of its own.
    CONFIRMATION_DISMISSED,

    // The action asks for a confirmation the device cannot hold — no biometrics, no screen lock.
    CONFIRMATION_UNAVAILABLE,

    LOCATION_PERMISSION_REQUIRED,

    LOCATION_OFF,

    PLACE_NOT_FOUND,

    PLACE_NOT_AWAITED,

    NO_PLACE,

    GOOGLE_TV_MISSING,

    WORK_PAUSED,

    MAC_NOT_PAIRED,

    MAC_LOCAL_NETWORK,

    MAC_UNREACHABLE,

    // The vault needs the user on its settings screen: to set it up, for the password, for the file
    // chosen again or for an error. The screen itself says which, so the outcome carries no message.
    VAULT_SETTINGS,
}

// SENT is all the phone can know: the Mac's answering lock-state is what greys the tile out.
fun lockMacOutcome(outcome: LockMacOutcome): ActionOutcome = when (outcome) {
    LockMacOutcome.SENT -> ActionOutcome.DONE
    LockMacOutcome.NOT_PAIRED -> ActionOutcome.MAC_NOT_PAIRED
    LockMacOutcome.NO_LOCAL_NETWORK -> ActionOutcome.MAC_LOCAL_NETWORK
    LockMacOutcome.UNREACHABLE -> ActionOutcome.MAC_UNREACHABLE
}

// Locked after an unlock is a dismissed prompt, which was its own feedback.
fun vaultOutcome(state: SafeState): ActionOutcome = when (state) {
    SafeState.Locked, is SafeState.Unlocking, is SafeState.Unlocked -> ActionOutcome.DONE
    SafeState.NotConfigured, is SafeState.NeedsPassword, SafeState.ChooseFileAgain, is SafeState.Failed ->
        ActionOutcome.VAULT_SETTINGS
}

// A vault locked in the meantime shows as locked on its tile, which is feedback enough.
fun vaultFilesOutcome(opened: SafeFilesOpened): ActionOutcome = when (opened) {
    SafeFilesOpened.OPENED, SafeFilesOpened.LOCKED -> ActionOutcome.DONE
    SafeFilesOpened.NO_FILE_MANAGER -> ActionOutcome.TARGET_MISSING
}

enum class VaultTap { UNLOCK, OPEN_FILES, LOCK, NOTHING }

// On an unlocked vault, Unlock opens the files whatever the switch says, as that is all a second tap
// can mean, and the toggle locks. A tap during an unlock does nothing, or the files would open twice.
fun vaultTapOf(state: SafeState, toggle: Boolean): VaultTap = when (state) {
    is SafeState.Unlocked -> if (toggle) VaultTap.LOCK else VaultTap.OPEN_FILES
    is SafeState.Unlocking -> VaultTap.NOTHING
    SafeState.NotConfigured, SafeState.Locked, is SafeState.NeedsPassword, SafeState.ChooseFileAgain,
    is SafeState.Failed,
    -> VaultTap.UNLOCK
}

// Only the Google TV app opens its remote, so a missing target can name the app that is missing.
fun tvRemoteOutcome(started: ActionOutcome): ActionOutcome =
    if (started == ActionOutcome.TARGET_MISSING) ActionOutcome.GOOGLE_TV_MISSING else started

fun haActionOutcome(probe: HaProbe): ActionOutcome = when (probe) {
    is HaProbe.Ok -> ActionOutcome.DONE
    is HaProbe.Http -> ActionOutcome.HA_REJECTED
    is HaProbe.Network -> ActionOutcome.HA_UNREACHABLE
    HaProbe.Timeout -> ActionOutcome.HA_TIMEOUT
    HaProbe.LocalNetworkDenied -> ActionOutcome.HA_LOCAL_NETWORK
    HaProbe.Blocked, HaProbe.Unsaved, HaProbe.NotConfigured -> ActionOutcome.HA_NOT_CONFIGURED
}
