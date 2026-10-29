package app.cue.launcher.feature.settings.bridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.system.bridge.BRIDGE_PROTOCOL_VERSION
import app.cue.launcher.system.bridge.BridgeLinkState
import app.cue.launcher.system.bridge.PairAbortReason
import app.cue.launcher.system.bridge.PairingAbort

@Composable
internal fun bridgeLinkText(link: BridgeLinkState, endpointMissing: Boolean): String = stringResource(
    when (link) {
        BridgeLinkState.IDLE -> if (endpointMissing) R.string.bridge_link_no_endpoint else R.string.bridge_link_idle
        BridgeLinkState.CONNECTING -> R.string.bridge_link_connecting
        BridgeLinkState.CONNECTED -> R.string.bridge_link_connected
        BridgeLinkState.UNREACHABLE -> R.string.bridge_link_unreachable
        BridgeLinkState.REJECTED -> R.string.bridge_link_rejected
        BridgeLinkState.VERSION_MISMATCH -> R.string.bridge_link_version
        BridgeLinkState.REPLACED -> R.string.bridge_link_replaced
        BridgeLinkState.NO_LOCAL_NETWORK -> R.string.bridge_link_no_local_network
        BridgeLinkState.KEY_UNAVAILABLE -> R.string.bridge_link_key_unavailable
    },
)

// The older side is the one to update, so the message names it.
@Composable
private fun versionText(version: Int): String = stringResource(
    if (version > BRIDGE_PROTOCOL_VERSION) R.string.bridge_version_update_phone else R.string.bridge_version_update_mac,
)

@Composable
internal fun pairingAbortText(reason: PairingAbort): String = when (reason) {
    is PairingAbort.Sent -> stringResource(sentAbortText(reason.reason))
    is PairingAbort.Received -> stringResource(receivedAbortText(reason.reason))
    PairingAbort.NoAnswer -> stringResource(R.string.bridge_abort_no_answer)
    is PairingAbort.VersionMismatch -> versionText(reason.peerVersion)
    PairingAbort.Malformed -> stringResource(R.string.bridge_abort_protocol)
    PairingAbort.ConnectionLost -> stringResource(R.string.bridge_abort_lost)
    PairingAbort.Unreachable -> stringResource(R.string.bridge_abort_unreachable)
    PairingAbort.KeystoreFailed -> stringResource(R.string.bridge_abort_keystore)
}

// The phone never sends busy or sas-mismatch; they read as cancelled only to keep the mapping total.
private fun sentAbortText(reason: PairAbortReason): Int = when (reason) {
    PairAbortReason.CANCELLED, PairAbortReason.BUSY, PairAbortReason.SAS_MISMATCH -> R.string.bridge_abort_cancelled
    PairAbortReason.TIMEOUT -> R.string.bridge_abort_timeout
    PairAbortReason.PROTOCOL_ERROR -> R.string.bridge_abort_protocol
}

private fun receivedAbortText(reason: PairAbortReason): Int = when (reason) {
    PairAbortReason.SAS_MISMATCH -> R.string.bridge_abort_mac_sas_mismatch
    PairAbortReason.CANCELLED -> R.string.bridge_abort_mac_cancelled
    PairAbortReason.TIMEOUT -> R.string.bridge_abort_mac_timeout
    PairAbortReason.PROTOCOL_ERROR -> R.string.bridge_abort_mac_protocol
    PairAbortReason.BUSY -> R.string.bridge_abort_mac_busy
}
