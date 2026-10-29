package app.cue.launcher.system.bridge

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

private const val CODE_NOTIF_POSTED = 1
private const val CODE_NOTIF_REMOVED = 2
private const val CODE_DISMISS = 3
private const val CODE_REPLY = 4
private const val CODE_CLIPBOARD_SMALL = 5
private const val CODE_SYNC_REQUEST = 7
private const val CODE_REPLY_ERROR = 8
private const val CODE_PING = 9
private const val CODE_CLIPBOARD_PENDING = 10
private const val CODE_REPLY_OK = 11
private const val CODE_LOCK_STATE = 12
private const val CODE_LOCK = 13

internal enum class BleFrameType(val code: Int, vararg senders: BleDirection) {
    NOTIF_POSTED(CODE_NOTIF_POSTED, BleDirection.PHONE_TO_MAC),
    NOTIF_REMOVED(CODE_NOTIF_REMOVED, BleDirection.PHONE_TO_MAC),
    DISMISS(CODE_DISMISS, BleDirection.MAC_TO_PHONE),
    REPLY(CODE_REPLY, BleDirection.MAC_TO_PHONE),
    CLIPBOARD_SMALL(CODE_CLIPBOARD_SMALL, BleDirection.MAC_TO_PHONE),
    SYNC_REQUEST(CODE_SYNC_REQUEST, BleDirection.MAC_TO_PHONE),
    REPLY_ERROR(CODE_REPLY_ERROR, BleDirection.PHONE_TO_MAC),
    PING(CODE_PING, BleDirection.PHONE_TO_MAC, BleDirection.MAC_TO_PHONE),
    CLIPBOARD_PENDING(CODE_CLIPBOARD_PENDING, BleDirection.MAC_TO_PHONE),
    REPLY_OK(CODE_REPLY_OK, BleDirection.PHONE_TO_MAC),
    LOCK_STATE(CODE_LOCK_STATE, BleDirection.MAC_TO_PHONE),
    LOCK(CODE_LOCK, BleDirection.PHONE_TO_MAC),
    ;

    val senders: Set<BleDirection> = senders.toSet()

    companion object {
        fun of(code: Int): BleFrameType? = entries.firstOrNull { it.code == code }
    }
}

internal sealed interface BleSignal {

    val type: BleFrameType

    data class NotifPosted(val keyHash: String) : BleSignal {
        override val type get() = BleFrameType.NOTIF_POSTED
    }

    data class NotifRemoved(val keyHash: String) : BleSignal {
        override val type get() = BleFrameType.NOTIF_REMOVED
    }

    data class Dismiss(val keyHash: String, val revision: Long? = null) : BleSignal {
        override val type get() = BleFrameType.DISMISS
    }

    data class Reply(val keyHash: String, val text: String?) : BleSignal {
        override val type get() = BleFrameType.REPLY
    }

    data class ClipboardSmall(val text: String, val sensitive: Boolean, val ageMs: Long? = null) : BleSignal {
        override val type get() = BleFrameType.CLIPBOARD_SMALL
    }

    data object SyncRequest : BleSignal {
        override val type get() = BleFrameType.SYNC_REQUEST
    }

    data class ReplyError(val keyHash: String, val reason: String?) : BleSignal {
        override val type get() = BleFrameType.REPLY_ERROR
    }

    data object Ping : BleSignal {
        override val type get() = BleFrameType.PING
    }

    data object ClipboardPending : BleSignal {
        override val type get() = BleFrameType.CLIPBOARD_PENDING
    }

    data class ReplyOk(val keyHash: String) : BleSignal {
        override val type get() = BleFrameType.REPLY_OK
    }

    data class LockState(val locked: Boolean) : BleSignal {
        override val type get() = BleFrameType.LOCK_STATE
    }

    data object Lock : BleSignal {
        override val type get() = BleFrameType.LOCK
    }
}

@Serializable
private data class KeyPayload(val k: String)

@Serializable
private data class DismissPayload(val k: String, val r: Long? = null)

@Serializable
private data class ReplyPayload(val k: String, val t: String? = null, val tcp: Boolean = false)

@Serializable
private data class ReplyErrorPayload(val k: String, val r: String? = null)

@Serializable
private data class ClipboardPayload(val t: String, val s: Boolean, val a: Long? = null)

@Serializable
private data class LockStatePayload(val l: Boolean)

@Serializable
private class EmptyPayload

internal fun encodeBleSignal(signal: BleSignal): ByteArray = when (signal) {
    is BleSignal.NotifPosted -> encode(KeyPayload.serializer(), KeyPayload(signal.keyHash))
    is BleSignal.NotifRemoved -> encode(KeyPayload.serializer(), KeyPayload(signal.keyHash))
    is BleSignal.Dismiss -> encode(DismissPayload.serializer(), DismissPayload(signal.keyHash, signal.revision))
    is BleSignal.Reply -> encode(
        ReplyPayload.serializer(),
        ReplyPayload(signal.keyHash, signal.text, tcp = signal.text == null),
    )
    is BleSignal.ClipboardSmall ->
        encode(ClipboardPayload.serializer(), ClipboardPayload(signal.text, signal.sensitive, signal.ageMs))
    BleSignal.SyncRequest -> encode(EmptyPayload.serializer(), EmptyPayload())
    is BleSignal.ReplyError -> encode(ReplyErrorPayload.serializer(), ReplyErrorPayload(signal.keyHash, signal.reason))
    BleSignal.Ping, BleSignal.ClipboardPending, BleSignal.Lock -> encode(EmptyPayload.serializer(), EmptyPayload())
    is BleSignal.ReplyOk -> encode(KeyPayload.serializer(), KeyPayload(signal.keyHash))
    is BleSignal.LockState -> encode(LockStatePayload.serializer(), LockStatePayload(signal.locked))
}

internal fun fitsBleFrame(signal: BleSignal): Boolean =
    encodeBleSignal(signal).size <= BleFrameCodec.MAX_PLAINTEXT_BYTES

// A reply too long for one frame goes out as the "tcp" flag and follows as reply-text.
internal fun replySignal(keyHash: String, text: String): BleSignal.Reply =
    BleSignal.Reply(keyHash, text).takeIf(::fitsBleFrame) ?: BleSignal.Reply(keyHash, null)

internal fun decodeBleSignal(type: Int, plaintext: ByteArray): BleSignal? {
    val known = BleFrameType.of(type) ?: return null
    return try {
        decodeSignal(known, plaintext.decodeToString(throwOnInvalidSequence = true))
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: CharacterCodingException) {
        null
    }
}

private fun decodeSignal(type: BleFrameType, text: String): BleSignal? = when (type) {
    BleFrameType.NOTIF_POSTED -> keyHash(text)?.let(BleSignal::NotifPosted)
    BleFrameType.NOTIF_REMOVED -> keyHash(text)?.let(BleSignal::NotifRemoved)
    BleFrameType.DISMISS -> decodeDismiss(text)
    BleFrameType.REPLY -> decodeReply(text)
    BleFrameType.CLIPBOARD_SMALL ->
        decode(ClipboardPayload.serializer(), text).let { BleSignal.ClipboardSmall(it.t, it.s, it.a) }
    BleFrameType.REPLY_ERROR -> decode(ReplyErrorPayload.serializer(), text)
        .takeIf { isLowerHex(it.k, NOTIFICATION_KEY_HASH_BYTES) }
        ?.let { BleSignal.ReplyError(it.k, it.r) }
    BleFrameType.SYNC_REQUEST, BleFrameType.PING, BleFrameType.CLIPBOARD_PENDING, BleFrameType.LOCK ->
        decode(EmptyPayload.serializer(), text).let { EMPTY_SIGNALS.getValue(type) }
    BleFrameType.REPLY_OK -> keyHash(text)?.let(BleSignal::ReplyOk)
    BleFrameType.LOCK_STATE -> decode(LockStatePayload.serializer(), text).let { BleSignal.LockState(it.l) }
}

private val EMPTY_SIGNALS: Map<BleFrameType, BleSignal> =
    listOf(BleSignal.SyncRequest, BleSignal.Ping, BleSignal.ClipboardPending, BleSignal.Lock).associateBy { it.type }

private fun decodeDismiss(text: String): BleSignal.Dismiss? = decode(DismissPayload.serializer(), text)
    .takeIf { isLowerHex(it.k, NOTIFICATION_KEY_HASH_BYTES) }
    ?.let { BleSignal.Dismiss(it.k, it.r) }

private fun decodeReply(text: String): BleSignal.Reply? {
    val payload = decode(ReplyPayload.serializer(), text)
    val valid = isLowerHex(payload.k, NOTIFICATION_KEY_HASH_BYTES) && (payload.t == null) == payload.tcp
    return if (valid) BleSignal.Reply(payload.k, payload.t) else null
}

private fun keyHash(text: String): String? =
    decode(KeyPayload.serializer(), text).k.takeIf { isLowerHex(it, NOTIFICATION_KEY_HASH_BYTES) }

// The JSON type of every member is checked first: the decoder alone would take "1" for a number,
// which the Mac's decoder refuses.
private fun <T> decode(serializer: KSerializer<T>, text: String): T {
    val json = bridgeJson.parseToJsonElement(text) as? JsonObject
    require(json != null && jsonMembersMatch(serializer.descriptor, json)) { "the plaintext does not match its type" }
    return bridgeJson.decodeFromJsonElement(serializer, json)
}

private fun <T> encode(serializer: KSerializer<T>, value: T): ByteArray =
    bridgeJson.encodeToString(serializer, value).encodeToByteArray()
