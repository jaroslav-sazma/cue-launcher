package app.cue.launcher.feature.dragdrop

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.layout.LauncherItem
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.data.layout.StaticLayout
import app.cue.launcher.data.layout.staticLayoutOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ShortcutDragTest {

    @Test
    fun `a component passes through the transfer unchanged`() {
        val component = ComponentRef("com.android.chrome", "com.google.android.apps.chrome.Main", 0L)

        assertEquals(component, component(decodeDragPayload(encodeDragPayload(DragPayload.NewShortcut(component)))))
    }

    @Test
    fun `a work profile is carried along with the component`() {
        val work = ComponentRef("com.android.chrome", "com.google.android.apps.chrome.Main", 12L)

        assertEquals(work, component(decodeDragPayload(encodeDragPayload(DragPayload.NewShortcut(work)))))
        assertEquals(12L, component(decodeDragPayload(encodeDragPayload(DragPayload.NewShortcut(work))))?.userSerial)
    }

    @Test
    fun `an inner class passes through unchanged`() {
        val component = ComponentRef("app.cue.launcher", "app.cue.Main\$Alias", 0L)

        assertEquals(component, component(decodeDragPayload(encodeDragPayload(DragPayload.NewShortcut(component)))))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "com.android.chrome",
            "com.android.chrome/Main",
            "com.android.chrome/Main#",
            "com.android.chrome/Main#osobni",
            "com.android.chrome#0",
            "/Main#0",
            "com.android.chrome/#0",
            "libovolny text odjinud",
            "app:",
            "app:com.android.chrome/Main#osobni",
            "move:",
            "move:1:2",
            "move:1:1:1",
            "move:x:1:1:1",
            "move:1:0:1:1",
            "move:1:1:1:ano",
        ],
    )
    fun `an unreadable payload produces no shortcut`(text: String) {
        assertNull(decodeDragPayload(text))
    }

    @Test
    fun `a missing payload produces no shortcut`() {
        assertNull(decodeDragPayload(null))
    }

    @Test
    fun `a finger hits the free slot beneath it`() {
        val slots = listOf(
            slot(position = 0, occupied = false),
            slot(position = 1, occupied = false),
        )

        assertEquals(
            DropTarget.Free(SlotKey(LayoutContainer.GRID, 1)),
            dropTargetAt(slots, x = 150f, y = 50f),
        )
    }

    @Test
    fun `an occupied slot is not a target`() {
        val slots = listOf(slot(position = 0, occupied = true))

        assertNull(dropTargetAt(slots, x = 50f, y = 50f))
        assertNull(dropTargetAt(slots, x = 50f, y = 50f, payload = DragPayload.NewShortcut(CHROME)))
    }

    @Test
    fun `a shortcut on a shortcut creates a folder`() {
        val slots = listOf(slot(position = 1, occupied = true, shortcut = true))

        assertEquals(
            DropTarget.NewFolder(SlotKey(LayoutContainer.GRID, 1), shortcutId = 1L),
            dropTargetAt(slots, x = 150f, y = 50f, payload = DragPayload.NewShortcut(CHROME)),
        )
    }

    @Test
    fun `a different item does not merge with a shortcut`() {
        val slots = listOf(slot(position = 1, occupied = true, shortcut = true))

        assertNull(
            dropTargetAt(
                slots,
                x = 150f,
                y = 50f,
                payload = DragPayload.MoveItem(itemId = 9, columnSpan = 1, rowSpan = 1, isShortcut = false),
            ),
        )
    }

    @Test
    fun `a folder is a drag target`() {
        val slots = listOf(
            slot(position = 0, occupied = true, folderId = 7L),
        )

        assertEquals(
            DropTarget.Folder(SlotKey(LayoutContainer.GRID, 0), folderId = 7L),
            dropTargetAt(slots, x = 50f, y = 50f),
        )
    }

    @Test
    fun `outside the slots there is nowhere to drop`() {
        val slots = listOf(slot(position = 0, occupied = false))

        assertNull(dropTargetAt(slots, x = 150f, y = 50f))
        assertNull(dropTargetAt(slots, x = 50f, y = 500f))
        assertNull(dropTargetAt(emptyList(), x = 50f, y = 50f))
    }

    @Test
    fun `the menu targets the first free grid position`() {
        assertEquals(SlotKey(LayoutContainer.GRID, 0), firstFreeSlot(StaticLayout.EMPTY))
    }

    @Test
    fun `the menu fills the gap left by an uninstall`() {
        val layout = staticLayoutOf(
            listOf(shortcut(LayoutContainer.GRID, 0), shortcut(LayoutContainer.GRID, 2)),
        )

        assertEquals(SlotKey(LayoutContainer.GRID, 1), firstFreeSlot(layout))
    }

    @Test
    fun `a full grid sends the menu to the dock`() {
        val grid = (0 until LayoutContainer.GRID.slotCount).map { shortcut(LayoutContainer.GRID, it) }
        val layout = staticLayoutOf(grid + shortcut(LayoutContainer.DOCK, 0))

        assertEquals(SlotKey(LayoutContainer.DOCK, 1), firstFreeSlot(layout))
    }

    @Test
    fun `a full home screen has no free position`() {
        val items = LayoutContainer.entries.flatMap { container ->
            (0 until container.slotCount).map { shortcut(container, it) }
        }

        assertNull(firstFreeSlot(staticLayoutOf(items)))
    }

    @Test
    fun `the menu does not reach into the dock panel`() {
        val home = LayoutContainer.home.flatMap { container ->
            (0 until container.slotCount).map { shortcut(container, it) }
        }

        assertNull(firstFreeSlot(staticLayoutOf(home)))
        assertEquals(
            SlotKey(LayoutContainer.PANEL_DOCK, 0),
            firstFreeSlot(staticLayoutOf(home), containers = listOf(LayoutContainer.PANEL_DOCK)),
        )
    }

    private fun component(payload: DragPayload?): ComponentRef? =
        (payload as? DragPayload.NewShortcut)?.component

    private fun slot(
        position: Int,
        occupied: Boolean,
        folderId: Long? = null,
        shortcut: Boolean = false,
    ) = SlotBounds(
        key = SlotKey(LayoutContainer.GRID, position),
        occupied = occupied,
        folderId = folderId,
        shortcutId = position.toLong().takeIf { occupied && shortcut },
        itemId = position.toLong().takeIf { occupied },
        covered = listOfNotNull(position.takeIf { occupied }),
        left = position * CELL,
        top = 0f,
        right = (position + 1) * CELL,
        bottom = CELL,
    )

    private fun shortcut(container: LayoutContainer, position: Int) = LauncherItem.Shortcut(
        id = position.toLong(),
        container = container,
        position = position,
        component = ComponentRef("app.$position", "Main", 0L),
    )

    private companion object {
        const val CELL = 100f
        val CHROME = ComponentRef("com.android.chrome", "com.google.android.apps.chrome.Main", 0L)
    }
}
