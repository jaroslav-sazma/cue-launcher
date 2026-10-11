package app.cue.launcher.feature.dragdrop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import app.cue.launcher.data.layout.LauncherItem
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.data.layout.areaPositions

@Stable
class ShortcutDragState {

    private val slots = mutableMapOf<SlotKey, SlotAnchor>()

    var target: DropTarget? by mutableStateOf(null)
        private set

    var payload: DragPayload? by mutableStateOf(null)
        private set

    var menuItemId: Long? by mutableStateOf(null)

    var pointer: Offset? by mutableStateOf(null)
        private set

    fun isTarget(container: LayoutContainer, position: Int): Boolean = when (val aimed = target) {
        null -> false
        is DropTarget.Folder, is DropTarget.NewFolder -> aimed.slot == SlotKey(container, position)
        is DropTarget.Free -> aimed.slot.container == container && position in aimed.positions
    }

    internal fun setContent(key: SlotKey, item: LauncherItem?) {
        val anchor = slots.getOrPut(key) { SlotAnchor() }
        anchor.item = item
    }

    internal fun setCoordinates(key: SlotKey, coordinates: LayoutCoordinates) {
        slots.getOrPut(key) { SlotAnchor() }.coordinates = coordinates
    }

    internal fun unregister(key: SlotKey) {
        slots.remove(key)
        if (target?.slot == key) target = null
    }

    internal fun startDrag(payload: DragPayload?) {
        this.payload = payload
    }

    internal fun targetAt(position: Offset): DropTarget? =
        dropTargetAt(slots = bounds(), x = position.x, y = position.y, payload = payload)

    internal fun aimAt(position: Offset) {
        pointer = position
        target = targetAt(position)
    }

    internal fun clearTarget() {
        pointer = null
        target = null
    }

    internal fun endDrag() {
        clearTarget()
        payload = null
    }

    private fun bounds(): List<SlotBounds> = slots.mapNotNull { (key, anchor) ->
        val coordinates = anchor.coordinates?.takeIf { it.isAttached } ?: return@mapNotNull null
        val rect = coordinates.boundsInRoot()
        val item = anchor.item
        SlotBounds(
            key = key,
            occupied = item != null,
            folderId = (item as? LauncherItem.Folder)?.id,
            shortcutId = (item as? LauncherItem.Shortcut)?.id,
            itemId = item?.id,

            covered = item?.let {
                areaPositions(key.container, key.position, it.columnSpan, it.rowSpan)
            }.orEmpty(),
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
        )
    }
}

private class SlotAnchor(
    var item: LauncherItem? = null,
    var coordinates: LayoutCoordinates? = null,
)

@Composable
fun Modifier.dropSlot(
    state: ShortcutDragState,
    container: LayoutContainer,
    position: Int,
    item: LauncherItem?,
): Modifier {
    val key = remember(container, position) { SlotKey(container, position) }

    SideEffect { state.setContent(key, item) }
    DisposableEffect(state, key) {
        onDispose { state.unregister(key) }
    }
    return onGloballyPositioned { coordinates -> state.setCoordinates(key, coordinates) }
}
