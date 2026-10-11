package app.cue.launcher.data.layout

import app.cue.launcher.data.scenario.LauncherWidgetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LauncherItemEntityTest {

    @Test
    fun `shortcut roundtrip`() {
        val item = LauncherItem.Shortcut(
            id = 7,
            container = LayoutContainer.DOCK,
            position = 2,
            component = ComponentRef(
                packageName = "com.android.dialer",
                className = "com.android.dialer.DialtactsActivity",
                userSerial = 0,
            ),
        )

        assertEquals(item, item.toEntity().toLauncherItem())
    }

    @Test
    fun `shortcut roundtrip from a work profile`() {
        val item = LauncherItem.Shortcut(
            id = 8,
            container = LayoutContainer.GRID,
            position = 3,
            component = ComponentRef("com.android.dialer", "com.android.dialer.DialtactsActivity", 10),
        )

        val restored = item.toEntity().toLauncherItem()

        assertEquals(item, restored)
        assertEquals(10L, item.toEntity().userSerial)
    }

    @Test
    fun `enums are stored with text keys`() {
        val entity = LauncherItem.Shortcut(
            id = 1,
            container = LayoutContainer.GRID,
            position = 0,
            component = ComponentRef("app.cue.test", "app.cue.test.MainActivity", 0),
        ).toEntity()

        assertEquals("grid", entity.container)
        assertEquals("shortcut", entity.type)
        assertEquals("app.cue.test", entity.packageName)
        assertEquals("app.cue.test.MainActivity", entity.activityClassName)
        assertEquals(0L, entity.userSerial)
    }

    @Test
    fun `an unknown type and container are skipped instead of crashing the home screen`() {
        val base = LauncherItemEntity(
            id = 1,
            container = "grid",
            position = 0,
            type = "shortcut",
            packageName = "app.cue.test",
            activityClassName = "app.cue.test.MainActivity",
            userSerial = 0,
        )

        assertNull(base.copy(type = "app_widget").toLauncherItem())
        assertNull(base.copy(container = "panelDock").toLauncherItem())
    }

    @Test
    fun `searchbar roundtrip`() {
        val searchbar = LauncherItem.LauncherWidget(
            id = 12,
            container = LayoutContainer.GRID,
            position = 10,
            widget = LauncherWidgetType.SEARCHBAR,
        )

        val entity = searchbar.toEntity()

        assertEquals("launcher_widget", entity.type)
        assertEquals("searchbar", entity.widgetType)

        assertEquals("", entity.packageName)
        assertEquals(0L, entity.userSerial)
        assertEquals(searchbar, entity.toLauncherItem())
        assertEquals(2, searchbar.columnSpan)
    }

    @Test
    fun `an unknown widget kind is discarded`() {
        val base = LauncherItem.LauncherWidget(
            id = 1,
            container = LayoutContainer.GRID,
            position = 0,
            widget = LauncherWidgetType.SEARCHBAR,
        ).toEntity()

        assertNull(base.copy(widgetType = "kamera").toLauncherItem())
        assertNull(base.copy(widgetType = null).toLauncherItem())
    }

    @Test
    fun `calendar and clock are both 4x2 in the grid`() {
        val base = LauncherItem.LauncherWidget(
            id = 1,
            container = LayoutContainer.GRID,
            position = 0,
            widget = LauncherWidgetType.SEARCHBAR,
        ).toEntity()

        listOf("calendar", "clock").forEach { jsonName ->
            val item = base.copy(widgetType = jsonName).toLauncherItem()

            assertEquals(4, item?.columnSpan, jsonName)
            assertEquals(2, item?.rowSpan, jsonName)
        }
        assertEquals(2, base.toLauncherItem()?.columnSpan)
        assertEquals(1, base.toLauncherItem()?.rowSpan)
    }

    @Test
    fun `folder roundtrip including its contents`() {
        val items = listOf(
            ComponentRef("com.android.calculator2", "com.android.calculator2.Calculator", 0),
            ComponentRef("com.android.deskclock", "com.android.deskclock.DeskClock", 10),
        )
        val folder = LauncherItem.Folder(
            id = 4,
            container = LayoutContainer.GRID,
            position = 5,
            name = "Nástroje",
            items = items,
        )

        val entity = folder.toEntity()

        assertEquals("folder", entity.type)
        assertEquals("Nástroje", entity.label)

        assertEquals("", entity.packageName)
        assertEquals("", entity.activityClassName)
        assertEquals(0L, entity.userSerial)
        assertEquals(folder, entity.toLauncherItem(members = items))
    }

    @Test
    fun `an empty folder is a valid state`() {
        val folder = LauncherItem.Folder(
            id = 9,
            container = LayoutContainer.DOCK,
            position = 1,
            name = "Složka",
            items = emptyList(),
        )

        assertEquals(folder, folder.toEntity().toLauncherItem())
    }

    @Test
    fun `the order of shortcuts in a folder is preserved`() {
        val entities = folderMemberEntities(
            folderId = 3,
            items = listOf(
                ComponentRef("a", "a.Main", 0),
                ComponentRef("b", "b.Main", 0),
                ComponentRef("c", "c.Main", 0),
            ),
        )

        assertEquals(listOf(0, 1, 2), entities.map(FolderMemberEntity::position))
        assertEquals(
            listOf("a", "b", "c"),
            entities.map { it.toComponentRef().packageName },
        )
    }
}
