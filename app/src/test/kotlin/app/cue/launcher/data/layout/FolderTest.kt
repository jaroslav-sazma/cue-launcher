package app.cue.launcher.data.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class FolderTest {

    @ParameterizedTest(name = "{0} items = {1} previews and +{2}")
    @CsvSource(
        "0, 0, 0",
        "1, 1, 0",
        "3, 3, 0",
        "4, 4, 0",
        "5, 3, 2",
        "7, 3, 4",
    )
    fun `previews and plus count on the chip`(itemCount: Int, previews: Int, plus: Int) {
        assertEquals(previews, folderPreviewCount(itemCount))
        assertEquals(plus, folderPlusCount(itemCount))
    }

    @Test
    fun `the sum of previews and plus is always the whole content`() {
        (0..20).forEach { count ->
            assertEquals(count, folderPreviewCount(count) + folderPlusCount(count))
        }
    }

    @Test
    fun `moving an item left and right`() {
        val items = listOf("a", "b", "c")

        assertEquals(listOf("b", "a", "c"), folderItemsMoved(items, from = 1, to = 0))
        assertEquals(listOf("a", "c", "b"), folderItemsMoved(items, from = 1, to = 2))
    }

    @Test
    fun `a move outside the range leaves the list unchanged`() {
        val items = listOf("a", "b", "c")

        assertSame(items, folderItemsMoved(items, from = 0, to = -1))
        assertSame(items, folderItemsMoved(items, from = 2, to = 3))
        assertSame(items, folderItemsMoved(items, from = 5, to = 0))
        assertSame(items, folderItemsMoved(items, from = 1, to = 1))
    }

    @Test
    fun `a move across the whole list keeps the rest in order`() {
        assertEquals(
            listOf("c", "a", "b"),
            folderItemsMoved(listOf("a", "b", "c"), from = 2, to = 0),
        )
    }

    @Test
    fun `a shortcut is added at the end`() {
        val chrome = ComponentRef("com.android.chrome", "com.google.android.apps.chrome.Main", 0)
        val clock = ComponentRef("com.android.deskclock", "com.android.deskclock.DeskClock", 0)

        assertEquals(listOf(chrome, clock), folderItemsWith(listOf(chrome), clock))
    }

    @Test
    fun `the same shortcut is not added twice`() {
        val chrome = ComponentRef("com.android.chrome", "com.google.android.apps.chrome.Main", 0)
        val items = listOf(chrome)

        assertSame(items, folderItemsWith(items, chrome))
    }

    @Test
    fun `the same app from a different profile is a different item`() {
        val personal = ComponentRef("com.android.chrome", "com.google.android.apps.chrome.Main", 0)
        val work = personal.copy(userSerial = 10)

        assertEquals(listOf(personal, work), folderItemsWith(listOf(personal), work))
    }

    @Test
    fun `a folder at a position is found among all items`() {
        val folder = LauncherItem.Folder(
            id = 3,
            container = LayoutContainer.DOCK,
            position = 2,
            name = "Nástroje",
            items = emptyList(),
        )
        val layout = staticLayoutOf(
            listOf(
                folder,
                LauncherItem.Shortcut(
                    id = 1,
                    container = LayoutContainer.GRID,
                    position = 0,
                    component = ComponentRef("a", "a.Main", 0),
                ),
            ),
        )

        assertEquals(folder, layout.itemAt(LayoutContainer.DOCK, 2))
        assertEquals(2, layout.allItems().size)
        assertEquals(folder, layout.allItems().filterIsInstance<LauncherItem.Folder>().single())
    }
}
