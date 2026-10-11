package app.cue.launcher.feature.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.home.LocalHomeBands

@Composable
fun rememberBentoSlotPx(size: TileSize): IntSize {
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current

    val bands = LocalHomeBands.current
    return remember(size, windowWidthPx, density, bands) {
        with(density) {
            val zoneInset = (Tokens.Grid.ZoneBleed.start + Tokens.Padding.zone).toPx() * 2
            val zoneWidthPx = (windowWidthPx - zoneInset).coerceAtLeast(0f)
            val frame = Tokens.Padding.widgetHolder.toPx() * 2
            when (size) {
                TileSize.HERO_4X2 -> slot(zoneWidthPx, bands.heroHeight.toPx(), frame)
                TileSize.GRID_2X2 -> slot(
                    widthPx = (zoneWidthPx - Tokens.Grid.gutter.toPx()) / 2f,
                    heightPx = bands.mediumHeight.toPx(),
                    frame = frame,
                )

                TileSize.GRID_1X1 -> Tokens.Size.icon.toPx().let { slot(it, it, frame) }
                TileSize.OVERLAY -> IntSize.Zero
            }
        }
    }
}

fun bentoTileRadius(size: TileSize): Dp = when (size) {
    TileSize.HERO_4X2 -> Tokens.Radius.tile4x2
    TileSize.GRID_2X2 -> Tokens.Radius.tile2x2
    TileSize.GRID_1X1, TileSize.OVERLAY -> Tokens.Radius.icon
}

private fun slot(widthPx: Float, heightPx: Float, frame: Float): IntSize = IntSize(
    width = (widthPx - frame).coerceAtLeast(0f).toInt(),
    height = (heightPx - frame).coerceAtLeast(0f).toInt(),
)
