package app.cue.launcher.system.bridge

import app.cue.launcher.data.notifications.NotificationListenerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

internal enum class MirrorSync { SEND_EMPTY, WAIT, READ_SHADE }

// Without notification access nothing can be mirrored, which is the one case an empty set tells the
// truth. A listener still binding would make the shade look empty and clear the Mac's mirror, so the
// set waits for it.
internal fun mirrorSyncOf(state: NotificationListenerState): MirrorSync = when {
    state.connected -> MirrorSync.READ_SHADE
    !state.enabled -> MirrorSync.SEND_EMPTY
    else -> MirrorSync.WAIT
}

// The changes of the listener that call for a set of their own. A listener that binds again after a
// drop missed what came in between, so every new binding sends the whole set once more.
internal fun Flow<NotificationListenerState>.mirrorSyncTriggers(): Flow<MirrorSync> =
    map(::mirrorSyncOf).distinctUntilChanged().filter { it != MirrorSync.WAIT }

internal class FittedSet(val set: BridgeMessage.ActiveSet, val dropped: Int)

// The set travels in one frame. Each element is clipped far below the limit, so only their number
// can outgrow it, and the oldest by `when` are left out first.
internal fun fitActiveSet(
    details: List<BridgeMessage.NotifDetail>,
    limit: Int = BRIDGE_MAX_BODY_BYTES,
): FittedSet {
    val whole = BridgeMessage.ActiveSet(details)
    if (bridgeBody(whole).size <= limit) return FittedSet(whole, dropped = 0)
    var budget = limit - bridgeBody(BridgeMessage.ActiveSet(emptyList())).size
    val kept = BooleanArray(details.size)
    var separator = 0
    for (index in details.indices.sortedByDescending { details[it].postedAt }) {
        val size = elementSize(details[index]) + separator
        if (size > budget) break
        budget -= size
        kept[index] = true
        separator = 1
    }
    val fitted = details.filterIndexedTo(ArrayList()) { index, _ -> kept[index] }
    while (fitted.isNotEmpty() && bridgeBody(BridgeMessage.ActiveSet(fitted)).size > limit) {
        fitted.remove(fitted.minBy { it.postedAt })
    }
    return FittedSet(BridgeMessage.ActiveSet(fitted), dropped = details.size - fitted.size)
}

private fun elementSize(detail: BridgeMessage.NotifDetail): Int =
    bridgeJson.encodeToString(BridgeMessage.NotifDetail.serializer(), detail).encodeToByteArray().size
