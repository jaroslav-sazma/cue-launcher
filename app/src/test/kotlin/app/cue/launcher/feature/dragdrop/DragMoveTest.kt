package app.cue.launcher.feature.dragdrop

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.layout.LauncherItem
import app.cue.launcher.data.layout.LayoutContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DragMoveTest {

    @Test
    fun `a move survives transfer along with its span`() {
        val payload = DragPayload.MoveItem(itemId = 42, columnSpan = 2, rowSpan = 3)

        assertEquals(payload, decodeDragPayload(encodeDragPayload(payload)))
    }

    @Test
    fun `the shortcut flag survives transfer`() {
        val payload = DragPayload.MoveItem(itemId = 42, columnSpan = 1, rowSpan = 1, isShortcut = true)

        assertEquals(payload, decodeDragPayload(encodeDragPayload(payload)))
    }

    @Test
    fun `an item carries its own span and kind`() {
        val shortcut = LauncherItem.Shortcut(
            id = 3,
            container = LayoutContainer.GRID,
            position = 0,
            component = ComponentRef("app.cue.launcher", "Main", 0L),
        )
        val folder = LauncherItem.Folder(
            id = 4,
            container = LayoutContainer.GRID,
            position = 1,
            name = "Složka",
            items = emptyList(),
        )

        assertEquals(DragPayload.MoveItem(3, columnSpan = 1, rowSpan = 1, isShortcut = true), shortcut.dragPayload())
        assertEquals(DragPayload.MoveItem(4, columnSpan = 1, rowSpan = 1, isShortcut = false), folder.dragPayload())
    }

    @Test
    fun `a dragged shortcut merges with a tile into a folder`() {
        val slots = withItem(position = 2, shortcut(position = 2, shortcutId = 7L))

        assertEquals(
            DropTarget.NewFolder(SlotKey(LayoutContainer.GRID, 2), shortcutId = 7L),
            dropTargetAt(
                slots = slots,
                x = 250f,
                y = 50f,
                payload = DragPayload.MoveItem(itemId = 9, columnSpan = 1, rowSpan = 1, isShortcut = true),
            ),
        )
    }

    @Test
    fun `a dragged widget does not merge with a shortcut`() {
        val slots = withItem(position = 2, shortcut(position = 2, shortcutId = 7L))

        assertNull(
            dropTargetAt(
                slots = slots,
                x = 250f,
                y = 50f,
                payload = DragPayload.MoveItem(itemId = 9, columnSpan = 1, rowSpan = 1, isShortcut = false),
            ),
        )
    }

    private fun withItem(position: Int, item: SlotBounds): List<SlotBounds> =
        emptyGrid().filterNot { it.key.position == position } + item

    @Test
    fun `a wider payload near the edge snaps inward`() {
        val target = dropTargetAt(
            slots = emptyGrid(),
            x = 350f,
            y = 50f,
            payload = DragPayload.MoveItem(itemId = 1, columnSpan = 2, rowSpan = 1),
        )

        assertEquals(DropTarget.Free(SlotKey(LayoutContainer.GRID, 2), columnSpan = 2, rowSpan = 1), target)
    }

    @Test
    fun `a taller payload snaps upward`() {
        val target = dropTargetAt(
            slots = emptyGrid(),
            x = 50f,
            y = 250f,
            payload = DragPayload.MoveItem(itemId = 1, columnSpan = 1, rowSpan = 2),
        )

        assertEquals(DropTarget.Free(SlotKey(LayoutContainer.GRID, 4), columnSpan = 1, rowSpan = 2), target)
    }

    @Test
    fun `an occupied cell inside the rectangle cancels the target`() {
        val slots = emptyGrid(occupied = mapOf(5 to 9L))

        assertNull(
            dropTargetAt(
                slots = slots,
                x = 50f,
                y = 50f,
                payload = DragPayload.MoveItem(itemId = 1, columnSpan = 2, rowSpan = 2),
            ),
        )
    }

    @Test
    fun `an item's own cells are not an obstacle`() {
        val slots = emptyGrid(occupied = mapOf(0 to 9L, 1 to 9L, 4 to 9L, 5 to 9L))

        assertEquals(
            DropTarget.Free(SlotKey(LayoutContainer.GRID, 1), columnSpan = 2, rowSpan = 2),
            dropTargetAt(
                slots = slots,
                x = 150f,
                y = 50f,
                payload = DragPayload.MoveItem(itemId = 9, columnSpan = 2, rowSpan = 2),
            ),
        )
    }

    @Test
    fun `a widget does not drop into a folder`() {
        val slots = emptyGrid() + folder(position = 3, folderId = 4L)

        assertNull(
            dropTargetAt(
                slots = slots.filter { it.key.position != 3 } + folder(position = 3, folderId = 4L),
                x = 350f,
                y = 50f,
                payload = DragPayload.MoveItem(itemId = 1, columnSpan = 2, rowSpan = 1),
            ),
        )
    }

    @Test
    fun `a container rejects a payload that is too large`() {
        val dock = listOf(bounds(SlotKey(LayoutContainer.DOCK, 0), itemId = null))

        assertNull(
            dropTargetAt(
                slots = dock,
                x = 50f,
                y = 50f,
                payload = DragPayload.MoveItem(itemId = 1, columnSpan = 1, rowSpan = 2),
            ),
        )
    }

    @Test
    fun `an occupied dock does not block the grid`() {
        val slots = emptyGrid() + fullDock()

        assertEquals(
            DropTarget.Free(SlotKey(LayoutContainer.GRID, 3), columnSpan = 1, rowSpan = 1),
            dropTargetAt(
                slots = slots,
                x = 350f,
                y = 50f,
                payload = DragPayload.MoveItem(itemId = 9, columnSpan = 1, rowSpan = 1, isShortcut = true),
            ),
        )
    }

    private fun fullDock(): List<SlotBounds> = (0 until LayoutContainer.DOCK.slotCount).map { position ->
        val left = position * CELL
        SlotBounds(
            key = SlotKey(LayoutContainer.DOCK, position),
            occupied = true,
            folderId = null,
            shortcutId = DOCK_ITEM_ID + position,
            itemId = DOCK_ITEM_ID + position,
            covered = listOf(position),
            left = left,
            top = DOCK_TOP,
            right = left + CELL,
            bottom = DOCK_TOP + CELL,
        )
    }

    private fun emptyGrid(occupied: Map<Int, Long> = emptyMap()): List<SlotBounds> =
        (0 until LayoutContainer.GRID.slotCount).map { position ->
            bounds(key = SlotKey(LayoutContainer.GRID, position), itemId = occupied[position])
        }

    private fun shortcut(position: Int, shortcutId: Long) = bounds(
        key = SlotKey(LayoutContainer.GRID, position),
        itemId = shortcutId,
        shortcutId = shortcutId,
    )

    private fun folder(position: Int, folderId: Long) = bounds(
        key = SlotKey(LayoutContainer.GRID, position),
        itemId = folderId,
        folderId = folderId,
    )

    private fun bounds(
        key: SlotKey,
        itemId: Long?,
        folderId: Long? = null,
        shortcutId: Long? = null,
    ): SlotBounds {
        val left = key.container.columnOf(key.position) * CELL
        val top = key.container.rowOf(key.position) * CELL
        return SlotBounds(
            key = key,
            occupied = itemId != null,
            folderId = folderId,
            shortcutId = shortcutId,
            itemId = itemId,
            covered = listOfNotNull(key.position.takeIf { itemId != null }),
            left = left,
            top = top,
            right = left + CELL,
            bottom = top + CELL,
        )
    }

    private companion object {
        const val CELL = 100f

        const val DOCK_TOP = 1000f
        const val DOCK_ITEM_ID = 100L
    }
}
