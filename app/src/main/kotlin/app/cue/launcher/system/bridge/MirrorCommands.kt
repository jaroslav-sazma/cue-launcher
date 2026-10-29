package app.cue.launcher.system.bridge

import app.cue.launcher.system.notifications.ReplyDelivery

// What the Mac asks of a mirrored notification. Runs on the one coroutine of the ledger it
// tombstones in, so a cancel's removal is queued behind the tombstone set here. Every key, a
// reply-text's full one included, is resolved through that ledger, so the Mac reaches only what was
// mirrored, and a key never mirrored fails exactly like one that is gone: the answer tells nothing
// about the rest of the shade. The revision is always read from the shade: the ledger may not have
// seen a newer post yet.
@Suppress("LongParameterList")
internal class MirrorCommands(
    private val keyOf: (String) -> String?,
    private val revisionOf: (String) -> Long?,
    private val tombstone: (String) -> Unit,
    private val cancel: (String) -> Boolean,
    private val deliverReply: suspend (key: String, text: String) -> ReplyDelivery,
    private val sendSignal: suspend (BleSignal) -> Boolean,
    private val log: (String) -> Unit = BridgeLog::event,
) {

    // A dismiss for another revision was meant for a post this one replaced. The doorbell makes the
    // Mac ask for the set, which shows it the current revision; it sends the dismiss again at that one
    // if the user closed what the notification shows now.
    suspend fun dismiss(keyHash: String, revision: Long?) {
        val key = keyOf(keyHash)
        val current = key?.let(revisionOf)
        when {
            key == null -> log("dismiss k=$keyHash not mirrored, ignored")
            revision != null && current != null && current != revision -> {
                log("dismiss k=$keyHash r=$revision is for an older post than r=$current, ignored")
                if (!sendSignal(BleSignal.NotifPosted(keyHash))) log("notif-posted k=$keyHash not sent")
            }
            cancel(key) -> {
                tombstone(keyHash)
                log("dismiss k=$keyHash r=${revision ?: "-"} cancelled")
            }
            else -> log("dismiss k=$keyHash not applied: no notification listener")
        }
    }

    // The tcp flag only announces the reply-text that carries the reply, which is acted on alone.
    suspend fun reply(signal: BleSignal.Reply) {
        val text = signal.text
        val key = keyOf(signal.keyHash)
        when {
            text == null -> log("reply k=${signal.keyHash} follows as reply-text")
            key == null -> failed(signal.keyHash, REPLY_ERROR_GONE)
            else -> deliver(key, signal.keyHash, text)
        }
    }

    suspend fun replyText(key: String, text: String) {
        val keyHash = notificationKeyHash(key)
        if (keyOf(keyHash) == key) deliver(key, keyHash, text) else failed(keyHash, REPLY_ERROR_GONE)
    }

    private suspend fun deliver(key: String, keyHash: String, text: String) {
        when (deliverReply(key, text)) {
            ReplyDelivery.SENT -> delivered(keyHash)
            ReplyDelivery.GONE -> failed(keyHash, REPLY_ERROR_GONE)
            ReplyDelivery.NO_REPLY_ACTION -> failed(keyHash, REPLY_ERROR_NO_REPLY_ACTION)
            ReplyDelivery.FAILED -> failed(keyHash, REPLY_ERROR_SEND_FAILED)
        }
    }

    private suspend fun delivered(keyHash: String) {
        log("reply k=$keyHash delivered")
        if (!sendSignal(BleSignal.ReplyOk(keyHash))) log("reply-ok k=$keyHash not sent")
    }

    private suspend fun failed(keyHash: String, reason: String) {
        log("reply k=$keyHash failed: $reason")
        if (!sendSignal(BleSignal.ReplyError(keyHash, reason))) log("reply-error k=$keyHash not sent")
    }
}
