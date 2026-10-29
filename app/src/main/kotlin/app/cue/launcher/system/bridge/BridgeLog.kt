package app.cue.launcher.system.bridge

import android.util.Log

// Protocol events go out at debug level with the direction, the type and at most a key hash or an
// eight-digit fingerprint prefix. Content, commitments and keys are never logged.
internal object BridgeLog {

    private const val TAG = "CueBridge"
    private const val PREFIX_DIGITS = 8

    fun event(text: String) {
        Log.d(TAG, text)
    }

    fun info(text: String) {
        Log.i(TAG, text)
    }

    // A socket failure's message can quote the Mac's address, so the warning names only the
    // exception class; the full failure goes out at debug level, which R8 strips from release.
    fun warn(text: String, failure: Throwable) {
        Log.w(TAG, "$text (${failure::class.java.simpleName})")
        Log.d(TAG, text, failure)
    }

    fun sent(connection: String, message: BridgeMessage) = event(sentLine(connection, message))

    fun sentLine(connection: String, message: BridgeMessage): String = "send ${describe(message)} conn=$connection"

    fun received(connection: String, message: BridgeMessage) = event("recv ${describe(message)} conn=$connection")

    fun prefix(fingerprint: String): String = fingerprint.take(PREFIX_DIGITS)

    fun prefix(fingerprint: ByteArray): String = prefix(fingerprint.toHexString())

    fun describe(message: BridgeMessage): String = when (message) {
        is BridgeMessage.ActiveSet -> "active-set count=${message.notifications.size}"
        is BridgeMessage.NotifDetail -> "notif-detail k=${notificationKeyHash(message.key)}"
        is BridgeMessage.ReplyText -> "reply-text k=${notificationKeyHash(message.key)}"
        is BridgeMessage.PairAbort -> "pair-abort reason=${message.reason}"
        is BridgeMessage.Bye -> "bye reason=${message.reason ?: "-"}"
        is BridgeMessage.Hello -> "hello v=${message.v} clipboardFromMac=${message.clipboardFromMac ?: "-"}"
        is BridgeMessage.IconGet -> "icon-get ${prefix(message.hash)}"
        is BridgeMessage.IconData -> "icon-data ${prefix(message.hash)} bytes=${message.binaryBytes}"
        is BridgeMessage.ClipboardSet ->
            "clipboard-set sensitive=${message.sensitive} announce=${message.announce == true} " +
                "ageMs=${message.ageMs ?: "-"}"
        else -> wireType(message)
    }
}

private val WIRE_TYPES = mapOf(
    BridgeMessage.Hello::class to "hello",
    BridgeMessage.Bye::class to "bye",
    BridgeMessage.PairRequest::class to "pair-request",
    BridgeMessage.PairResponse::class to "pair-response",
    BridgeMessage.PairReveal::class to "pair-reveal",
    BridgeMessage.PairConfirm::class to "pair-confirm",
    BridgeMessage.PairAbort::class to "pair-abort",
    BridgeMessage.Signal::class to "signal",
    BridgeMessage.SyncRequest::class to "sync-request",
    BridgeMessage.ActiveSet::class to "active-set",
    BridgeMessage.NotifDetail::class to "notif-detail",
    BridgeMessage.IconGet::class to "icon-get",
    BridgeMessage.IconData::class to "icon-data",
    BridgeMessage.ClipboardSet::class to "clipboard-set",
    BridgeMessage.ReplyText::class to "reply-text",
)

internal fun wireType(message: BridgeMessage): String = WIRE_TYPES[message::class] ?: "?"

internal val BleFrameType.wireName: String get() = name.lowercase().replace('_', '-')

internal fun signalKeyHash(signal: BleSignal): String? = when (signal) {
    is BleSignal.NotifPosted -> signal.keyHash
    is BleSignal.NotifRemoved -> signal.keyHash
    is BleSignal.Dismiss -> signal.keyHash
    is BleSignal.Reply -> signal.keyHash
    is BleSignal.ReplyError -> signal.keyHash
    is BleSignal.ReplyOk -> signal.keyHash
    else -> null
}
