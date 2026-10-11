package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileSize

val TILE_TYPE_CATALOG: List<TileSize> = listOf(
    TileSize.HERO_4X2,
    TileSize.GRID_2X2,
    TileSize.GRID_1X1,
    TileSize.OVERLAY,
)

fun tileTypeAvailable(size: TileSize): Boolean = size != TileSize.OVERLAY

fun tileTypeAspect(size: TileSize): Float = when (size) {
    TileSize.HERO_4X2 -> THUMB_HERO_WIDTH / THUMB_HERO_HEIGHT
    TileSize.GRID_2X2, TileSize.GRID_1X1 -> 1f
    TileSize.OVERLAY -> THUMB_MEDIUM_WIDTH / THUMB_SMALL_WIDTH
}

fun tileTypeThumbScale(size: TileSize): Float = when (size) {
    TileSize.HERO_4X2 -> 1f
    TileSize.GRID_2X2, TileSize.OVERLAY -> THUMB_MEDIUM_WIDTH / THUMB_HERO_WIDTH
    TileSize.GRID_1X1 -> THUMB_SMALL_WIDTH / THUMB_HERO_WIDTH
}

private const val THUMB_HERO_WIDTH = 38f
private const val THUMB_HERO_HEIGHT = 22f
private const val THUMB_MEDIUM_WIDTH = 26f
private const val THUMB_SMALL_WIDTH = 18f

fun contentKindsFor(size: TileSize): List<ContentKind> = when (size) {
    TileSize.GRID_1X1, TileSize.GRID_2X2 -> listOf(ContentKind.TEMPLATE, ContentKind.APP_WIDGET)
    TileSize.HERO_4X2 -> listOf(ContentKind.APP_WIDGET, ContentKind.LAUNCHER_WIDGET)
    TileSize.OVERLAY -> emptyList()
}

fun templateLayoutsFor(size: TileSize): List<TemplateLayout> =
    TemplateLayout.entries.filter { it.size == size }

fun launcherWidgetsFor(size: TileSize): List<LauncherWidgetType> =
    LauncherWidgetType.entries.filter { it.size == size }
