package app.cue.launcher.feature.dragdrop

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.layout.LauncherItem
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.data.layout.StaticLayout
import app.cue.launcher.data.layout.areaPositions

const val SHORTCUT_MIME_TYPE: String = "application/vnd.app.cue.launcher.shortcut"

sealed interface DragPayload {

    val columnSpan: Int get() = 1
    val rowSpan: Int get() = 1

    val movedItemId: Long? get() = null

    val isShortcut: Boolean get() = true

    data class NewShortcut(val component: ComponentRef) : DragPayload

    data class MoveItem(
        val itemId: Long,
        override val columnSpan: Int,
        override val rowSpan: Int,
        override val isShortcut: Boolean = false,
    ) : DragPayload {
        override val movedItemId: Long get() = itemId
    }
}

fun encodeDragPayload(payload: DragPayload): String = when (payload) {
    is DragPayload.NewShortcut -> with(payload.component) {
        "$APP_PREFIX$packageName$CLASS_SEPARATOR$className$PROFILE_SEPARATOR$userSerial"
    }

    is DragPayload.MoveItem -> with(payload) {
        val shortcut = if (isShortcut) SHORTCUT_FLAG else OTHER_FLAG
        "$MOVE_PREFIX$itemId$FIELD_SEPARATOR$columnSpan$FIELD_SEPARATOR$rowSpan$FIELD_SEPARATOR$shortcut"
    }
}

fun decodeDragPayload(text: CharSequence?): DragPayload? {
    val raw = text?.toString() ?: return null
    return when {
        raw.startsWith(APP_PREFIX) ->
            decodeComponent(raw.removePrefix(APP_PREFIX))?.let(DragPayload::NewShortcut)

        raw.startsWith(MOVE_PREFIX) -> decodeMove(raw.removePrefix(MOVE_PREFIX))
        else -> null
    }
}

@Suppress("ReturnCount")
private fun decodeComponent(raw: String): ComponentRef? {
    val profile = raw.lastIndexOf(PROFILE_SEPARATOR)
    if (profile < 0) return null
    val userSerial = raw.substring(profile + 1).toLongOrNull() ?: return null

    val separator = raw.indexOf(CLASS_SEPARATOR)
    if (separator < 1 || separator >= profile - 1) return null
    return ComponentRef(
        packageName = raw.substring(0, separator),
        className = raw.substring(separator + 1, profile),
        userSerial = userSerial,
    )
}

@Suppress("ReturnCount")
private fun decodeMove(raw: String): DragPayload.MoveItem? {
    val parts = raw.split(FIELD_SEPARATOR)
    if (parts.size != MOVE_FIELDS) return null
    val (rawId, rawColumns, rawRows) = parts
    val itemId = rawId.toLongOrNull() ?: return null
    val columnSpan = rawColumns.toIntOrNull()?.takeIf { it >= 1 } ?: return null
    val rowSpan = rawRows.toIntOrNull()?.takeIf { it >= 1 } ?: return null
    val isShortcut = when (parts.last()) {
        SHORTCUT_FLAG -> true
        OTHER_FLAG -> false
        else -> return null
    }
    return DragPayload.MoveItem(itemId, columnSpan, rowSpan, isShortcut)
}

data class SlotKey(val container: LayoutContainer, val position: Int)

data class SlotBounds(
    val key: SlotKey,
    val occupied: Boolean,
    val folderId: Long?,
    val itemId: Long?,
    val covered: List<Int>,

    val shortcutId: Long? = null,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

sealed interface DropTarget {

    val slot: SlotKey

    data class Free(
        override val slot: SlotKey,
        val columnSpan: Int = 1,
        val rowSpan: Int = 1,
    ) : DropTarget {
        val positions: List<Int> get() = areaPositions(slot.container, slot.position, columnSpan, rowSpan)
    }

    data class Folder(override val slot: SlotKey, val folderId: Long) : DropTarget

    data class NewFolder(override val slot: SlotKey, val shortcutId: Long) : DropTarget
}

@Suppress("ReturnCount")
fun dropTargetAt(
    slots: Collection<SlotBounds>,
    x: Float,
    y: Float,
    payload: DragPayload? = null,
): DropTarget? {
    val hit = slots.firstOrNull { it.contains(x, y) } ?: return null
    val columnSpan = payload?.columnSpan ?: 1
    val rowSpan = payload?.rowSpan ?: 1
    val moved = payload?.movedItemId
    val isOwn = hit.itemId != null && hit.itemId == moved
    if (hit.folderId != null && !isOwn) {
        return DropTarget.Folder(hit.key, hit.folderId).takeIf { columnSpan == 1 && rowSpan == 1 }
    }
    if (hit.occupied && !isOwn) {

        val merged = hit.shortcutId?.takeIf { payload?.isShortcut == true }
        return merged?.let { DropTarget.NewFolder(hit.key, it) }
    }
    return freeTargetAt(slots, hit.key, columnSpan, rowSpan, moved)
}

@Suppress("ReturnCount")
private fun freeTargetAt(
    slots: Collection<SlotBounds>,
    slot: SlotKey,
    columnSpan: Int,
    rowSpan: Int,
    moved: Long?,
): DropTarget.Free? {
    val container = slot.container
    val origin = clampedOrigin(container, slot.position, columnSpan, rowSpan) ?: return null
    val busy = slots
        .filter { it.key.container == container && it.itemId != null && it.itemId != moved }
        .flatMap(SlotBounds::covered)
    val positions = areaPositions(container, origin, columnSpan, rowSpan)
    if (positions.any { it in busy }) return null
    return DropTarget.Free(SlotKey(container, origin), columnSpan, rowSpan)
}

private fun clampedOrigin(
    container: LayoutContainer,
    position: Int,
    columnSpan: Int,
    rowSpan: Int,
): Int? {
    if (columnSpan > container.columns || rowSpan > container.rows) return null
    val column = container.columnOf(position).coerceAtMost(container.columns - columnSpan)
    val row = container.rowOf(position).coerceAtMost(container.rows - rowSpan)
    return container.positionOf(row, column)
}

fun firstFreeSlot(
    layout: StaticLayout,
    containers: List<LayoutContainer> = LayoutContainer.home,
): SlotKey? =
    containers.firstNotNullOfOrNull { container ->
        layout.slots(container)
            .indexOfFirst { it == null }
            .takeIf { it >= 0 }
            ?.let { position -> SlotKey(container, position) }
    }

fun LauncherItem.dragPayload(): DragPayload.MoveItem = DragPayload.MoveItem(
    itemId = id,
    columnSpan = columnSpan,
    rowSpan = rowSpan,
    isShortcut = this is LauncherItem.Shortcut,
)

private const val APP_PREFIX = "app:"
private const val MOVE_PREFIX = "move:"
private const val CLASS_SEPARATOR = '/'
private const val PROFILE_SEPARATOR = '#'
private const val FIELD_SEPARATOR = ":"
private const val MOVE_FIELDS = 4
private const val SHORTCUT_FLAG = "1"
private const val OTHER_FLAG = "0"
