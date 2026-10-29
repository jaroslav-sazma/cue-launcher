package app.cue.launcher.data.bridge

import kotlinx.serialization.Serializable

// Off is the empty record: turning the bridge off or unpairing goes back to these defaults, so
// nothing about a former pairing stays behind.
@Serializable
data class BridgeSettings(
    val enabled: Boolean = false,
    val pairing: BridgePairingRecord? = null,

    // Debug builds only: the endpoint DevPresence connects to instead of a Mac found over Bluetooth.
    val devEndpoint: BridgeDevEndpoint? = null,

    // Only text from the Mac asks these; Send to Mac does not.
    val clipboardFromMac: Boolean = true,
    val clipboardMaxAgeMinutes: Int = CLIPBOARD_MAX_AGE_DEFAULT_MINUTES,
    val mirrorExcluded: Set<String> = emptySet(),
)

const val CLIPBOARD_MAX_AGE_DEFAULT_MINUTES = 5

val CLIPBOARD_MAX_AGE_MINUTES = 1..60

@Serializable
data class BridgePairingRecord(
    val macName: String,
    val host: String,
    val port: Int,
    val macFingerprint: String,
    val bleKey: BridgeSealedKey,

    // The companion-device association the system watches the Mac's advertisement for; null for a
    // debug pairing over the dev endpoint, which has none.
    val association: BridgeAssociation? = null,
)

@Serializable
data class BridgeAssociation(val id: Int, val address: String? = null, val addressType: Int? = null)

@Serializable
data class BridgeSealedKey(val iv: String, val data: String)

@Serializable
data class BridgeDevEndpoint(val host: String = BRIDGE_DEV_HOST, val port: Int)

// The emulator's alias for the computer it runs on.
const val BRIDGE_DEV_HOST = "10.0.2.2"
