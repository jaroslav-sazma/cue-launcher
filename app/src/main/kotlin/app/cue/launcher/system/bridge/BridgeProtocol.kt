package app.cue.launcher.system.bridge

import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64

internal const val BRIDGE_PROTOCOL_VERSION = 1

internal const val BRIDGE_SERVICE_TYPE = "_cuebridge._tcp"
internal const val BRIDGE_TXT_VERSION_KEY = "v"
internal const val BRIDGE_TXT_PAIR_KEY = "pair"
internal const val BRIDGE_TXT_PAIR_ON = "1"

internal const val BRIDGE_MAX_BODY_BYTES = 1_048_576
internal const val BRIDGE_MAX_BINARY_BYTES = 1_048_576
internal const val BRIDGE_LENGTH_BYTES = 4

internal const val PAIRING_CONFIRM_TIMEOUT_MS = 120_000L
internal const val PAIRING_STEP_TIMEOUT_MS = 15_000L

internal const val PAIRING_COMMIT_LABEL = "app.cue.bridge/commit/v1"
internal const val PAIRING_TRANSCRIPT_LABEL = "app.cue.bridge/pair/v1"
internal const val PAIRING_KBLE_INFO = "app.cue.bridge/kble/v1"
internal const val PAIRING_SAS_INFO = "app.cue.bridge/sas/v1"
internal const val PAIRING_CONFIRM_INFO = "app.cue.bridge/confirm/v1"
internal const val L2CAP_KEYS_INFO = "app.cue.bridge/l2cap/v1"
internal const val SAS_DIGITS = 6
internal const val SAS_MODULUS = 1_000_000L

internal const val TOMBSTONE_MIN_MS = 5_000L

internal const val FINGERPRINT_BYTES = 32
internal const val X25519_KEY_BYTES = 32
internal const val BLE_KEY_BYTES = 32
internal const val CONFIRM_KEY_BYTES = 32
internal const val PROOF_BYTES = 32
internal const val COMMIT_BYTES = 32
internal const val NOTIFICATION_KEY_HASH_BYTES = 8

internal const val BYE_VERSION_MISMATCH = "version-mismatch"
internal const val BYE_REPLACED = "replaced"

// Informational only: the Mac logs it like any reason the spec does not define.
internal const val BYE_IDLE = "idle"
internal const val ABORT_SAS_MISMATCH = "sas-mismatch"
internal const val ABORT_CANCELLED = "cancelled"
internal const val ABORT_TIMEOUT = "timeout"
internal const val ABORT_PROTOCOL_ERROR = "protocol-error"
internal const val ABORT_BUSY = "busy"
internal const val REPLY_ERROR_GONE = "gone"
internal const val REPLY_ERROR_NO_REPLY_ACTION = "no-reply-action"
internal const val REPLY_ERROR_SEND_FAILED = "send-failed"

internal open class BridgeProtocolException(message: String, cause: Throwable? = null) : IOException(message, cause)

internal val bridgeJson = Json { ignoreUnknownKeys = true }

internal fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

internal fun decodeBase64(text: String): ByteArray = Base64.getDecoder().decode(text)

internal fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

internal fun certificateFingerprint(der: ByteArray): String = sha256(der).toHexString()

internal fun notificationKeyHash(key: String): String =
    sha256(key.encodeToByteArray()).copyOf(NOTIFICATION_KEY_HASH_BYTES).toHexString()

internal fun isLowerHex(value: String, bytes: Int): Boolean =
    value.length == bytes * 2 && value.all { it in '0'..'9' || it in 'a'..'f' }
