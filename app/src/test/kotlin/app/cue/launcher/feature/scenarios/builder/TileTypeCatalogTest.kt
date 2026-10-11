package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TileTypeCatalogTest {

    @Test
    fun `the catalog offers all sizes in wireframe order`() {
        assertEquals(
            listOf(TileSize.HERO_4X2, TileSize.GRID_2X2, TileSize.GRID_1X1, TileSize.OVERLAY),
            TILE_TYPE_CATALOG,
        )
        assertEquals(TileSize.entries.toSet(), TILE_TYPE_CATALOG.toSet())
    }

    @Test
    fun `overlay is coming soon and the rest can be selected`() {
        assertFalse(tileTypeAvailable(TileSize.OVERLAY))
        listOf(TileSize.HERO_4X2, TileSize.GRID_2X2, TileSize.GRID_1X1).forEach { size ->
            assertTrue(tileTypeAvailable(size))
        }
    }

    @Test
    fun `the thumbnail carries both aspect ratio and size`() {
        assertTrue(tileTypeAspect(TileSize.HERO_4X2) > 1f)
        assertEquals(1f, tileTypeAspect(TileSize.GRID_2X2))
        assertEquals(1f, tileTypeAspect(TileSize.GRID_1X1))

        assertEquals(1f, tileTypeThumbScale(TileSize.HERO_4X2))
        assertTrue(tileTypeThumbScale(TileSize.GRID_2X2) < tileTypeThumbScale(TileSize.HERO_4X2))
        assertTrue(tileTypeThumbScale(TileSize.GRID_1X1) < tileTypeThumbScale(TileSize.GRID_2X2))
    }

    @Test
    fun `every size offers its own content`() {
        assertEquals(
            listOf(ContentKind.TEMPLATE, ContentKind.APP_WIDGET),
            contentKindsFor(TileSize.GRID_1X1),
        )
        assertEquals(
            listOf(ContentKind.TEMPLATE, ContentKind.APP_WIDGET),
            contentKindsFor(TileSize.GRID_2X2),
        )
        assertEquals(
            listOf(ContentKind.APP_WIDGET, ContentKind.LAUNCHER_WIDGET),
            contentKindsFor(TileSize.HERO_4X2),
        )
    }

    @Test
    fun `templates belong only to their own size`() {
        assertEquals(listOf(TemplateLayout.ICON_TEXT), templateLayoutsFor(TileSize.GRID_1X1))
        assertEquals(
            listOf(TemplateLayout.TEXT_ONLY, TemplateLayout.TEXT_TWO_ICONS, TemplateLayout.FOUR_ICONS),
            templateLayoutsFor(TileSize.GRID_2X2),
        )
        assertEquals(emptyList<TemplateLayout>(), templateLayoutsFor(TileSize.HERO_4X2))
    }

    @Test
    fun `the searchbar is not offered as scenario content`() {
        assertEquals(
            listOf(LauncherWidgetType.CALENDAR, LauncherWidgetType.CLOCK, LauncherWidgetType.MEDIA),
            launcherWidgetsFor(TileSize.HERO_4X2),
        )
        assertTrue(TileSize.entries.none { LauncherWidgetType.SEARCHBAR in launcherWidgetsFor(it) })

        LauncherWidgetType.entries.mapNotNull { widget -> widget.size?.let { widget to it } }
            .forEach { (widget, size) -> assertTrue(widget in launcherWidgetsFor(size)) }
    }
}
