package app.cue.launcher.feature.home

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.designsystem.Tokens

@Immutable
data class HomeBands(

    val bentoGap: Dp,

    val rowGap: Dp,

    val dockGap: Dp,

    val heroHeight: Dp,

    val mediumHeight: Dp,
)

private val GRID_ROWS = LayoutContainer.GRID.rows

private val DOCK_HEIGHT = Tokens.Size.icon + Tokens.Padding.zone * 2

internal fun bentoBlockHeight(heroHeight: Dp, mediumHeight: Dp): Dp =
    Tokens.Padding.zone * 2 +
        heroHeight +
        Tokens.Grid.gutter +
        mediumHeight +
        Tokens.Grid.gutter +
        Tokens.Grid.RowHeight.static

// The column the 1x1 chips of the bento stand on. A 2x2 tile spans two of them, so an action chip
// inside such a tile only lines up with the chip below it when it is centred on this same column.
fun bentoColumnWidth(windowWidth: Dp): Dp {
    val zone = windowWidth - (Tokens.Grid.ZoneBleed.start + Tokens.Padding.zone) * 2
    val gutters = Tokens.Grid.gutter * (Tokens.Grid.COLUMNS - 1)
    return ((zone - gutters) / Tokens.Grid.COLUMNS).coerceAtLeast(0.dp)
}

fun homeBands(available: Dp): HomeBands {
    val hero = Tokens.Grid.RowHeight.hero4x2
    val medium = Tokens.Grid.RowHeight.tile2x2
    val fixed = bentoBlockHeight(hero, medium) +
        Tokens.Grid.RowHeight.static * GRID_ROWS +
        DOCK_HEIGHT
    val slack = available - fixed
    if (slack < 0.dp) return shrunkBands(hero, medium, deficit = -slack)

    val units = Tokens.Home.WEIGHT_BENTO_GAP +
        Tokens.Home.WEIGHT_ROW_GAP * (GRID_ROWS - 1) +
        Tokens.Home.WEIGHT_DOCK_GAP
    val unit = slack / units
    return HomeBands(
        bentoGap = unit * Tokens.Home.WEIGHT_BENTO_GAP,
        rowGap = unit * Tokens.Home.WEIGHT_ROW_GAP,
        dockGap = unit * Tokens.Home.WEIGHT_DOCK_GAP,
        heroHeight = hero,
        mediumHeight = medium,
    )
}

private fun shrunkBands(hero: Dp, medium: Dp, deficit: Dp): HomeBands {
    val tiles = hero + medium
    val scale = ((tiles - deficit) / tiles).coerceAtLeast(Tokens.Home.MIN_BENTO_SCALE)
    return HomeBands(
        bentoGap = 0.dp,
        rowGap = 0.dp,
        dockGap = 0.dp,
        heroHeight = hero * scale,
        mediumHeight = medium * scale,
    )
}

val LocalHomeBands: ProvidableCompositionLocal<HomeBands> = compositionLocalOf {
    homeBands(REFERENCE_HEIGHT - REFERENCE_INSETS)
}

private val REFERENCE_HEIGHT = 892.dp
private val REFERENCE_INSETS = 64.dp

@Composable
fun rememberHomeBands(): HomeBands {
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val heightPx = LocalWindowInfo.current.containerSize.height
    val top = insets.getTop(density)
    val bottom = insets.getBottom(density)
    return remember(density, heightPx, top, bottom) {
        with(density) { homeBands((heightPx - top - bottom).coerceAtLeast(0).toDp()) }
    }
}
