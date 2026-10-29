package app.cue.launcher.system.bridge

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator(BRIDGE_TYPE_FIELD)
internal sealed interface BridgeMessage {

    @Serializable
    @SerialName("hello")
    data class Hello(val v: Int, val clipboardFromMac: Boolean? = null) : BridgeMessage

    @Serializable
    @SerialName("bye")
    data class Bye(val reason: String? = null) : BridgeMessage

    @Serializable
    @SerialName("pair-request")
    data class PairRequest(val commit: String) : BridgeMessage

    @Serializable
    @SerialName("pair-response")
    data class PairResponse(val pk: String) : BridgeMessage

    @Serializable
    @SerialName("pair-reveal")
    data class PairReveal(val pk: String) : BridgeMessage

    @Serializable
    @SerialName("pair-confirm")
    data class PairConfirm(val proof: String) : BridgeMessage

    @Serializable
    @SerialName("pair-abort")
    data class PairAbort(val reason: String) : BridgeMessage

    @Serializable
    @SerialName("signal")
    data class Signal(val frame: String) : BridgeMessage

    @Serializable
    @SerialName("sync-request")
    data object SyncRequest : BridgeMessage

    @Serializable
    @SerialName("active-set")
    data class ActiveSet(val notifications: List<NotifDetail>) : BridgeMessage

    @Serializable
    @SerialName("notif-detail")
    data class NotifDetail(
        val key: String,
        val pkg: String,
        val appLabel: String,
        val title: String,
        val text: String,
        @SerialName("when") val postedAt: Long,
        val threadId: String? = null,
        val canReply: Boolean,
        val isClearable: Boolean,
        val iconHash: String? = null,
        @SerialName("r") val revision: Long? = null,
    ) : BridgeMessage

    @Serializable
    @SerialName("icon-get")
    data class IconGet(val hash: String) : BridgeMessage

    @Serializable
    @SerialName("icon-data")
    data class IconData(val hash: String, @SerialName(BRIDGE_BINARY_FIELD) val binaryBytes: Int) : BridgeMessage

    @Serializable
    @SerialName("clipboard-set")
    data class ClipboardSet(
        val text: String,
        val sensitive: Boolean,
        val announce: Boolean? = null,
        val ageMs: Long? = null,
    ) : BridgeMessage

    @Serializable
    @SerialName("reply-text")
    data class ReplyText(val key: String, val text: String) : BridgeMessage
}

internal const val BRIDGE_TYPE_FIELD = "type"
internal const val BRIDGE_BINARY_FIELD = "binaryBytes"
