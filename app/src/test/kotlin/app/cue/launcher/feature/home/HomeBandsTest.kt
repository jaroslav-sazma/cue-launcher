package app.cue.launcher.feature.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cue.launcher.designsystem.Tokens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HomeBandsTest {

    private val reference = 828.dp

    private val tall = 904.dp

    private val fixed = 780.dp

    private fun total(bands: HomeBands, rows: Int = 3): Dp =
        bentoBlockHeight(bands.heroHeight, bands.mediumHeight) +
            bands.bentoGap +
            Tokens.Grid.RowHeight.static * rows +
            bands.rowGap * (rows - 1) +
            bands.dockGap +
            Tokens.Size.icon + Tokens.Padding.zone * 2

    @Test
    @DisplayName("Static home content is 780 dp (bento 458 + 3 × 82 + dock 76)")
    fun `fixed content matches the tokens`() {
        assertEquals(458.dp, bentoBlockHeight(Tokens.Grid.RowHeight.hero4x2, Tokens.Grid.RowHeight.tile2x2))
        assertEquals(fixed, total(homeBands(fixed)))
    }

    @Test
    @DisplayName("The bento zone is inset by the same 9 dp on all four sides")
    fun `the bento zone has one inset`() {
        val sides = Tokens.Padding.zone * 2
        val stacked = Tokens.Grid.RowHeight.hero4x2 +
            Tokens.Grid.gutter +
            Tokens.Grid.RowHeight.tile2x2 +
            Tokens.Grid.gutter +
            Tokens.Grid.RowHeight.static

        assertEquals(
            sides + stacked,
            bentoBlockHeight(Tokens.Grid.RowHeight.hero4x2, Tokens.Grid.RowHeight.tile2x2),
        )
    }

    @Test
    @DisplayName("The surplus splits 2 : 1 : 3 and fills the display exactly")
    fun `the reference home screen splits the surplus by weight`() {
        val bands = homeBands(reference)
        val share = bands.rowGap.value

        assertEquals(share * 2, bands.bentoGap.value, TOLERANCE)
        assertEquals(share * 3, bands.dockGap.value, TOLERANCE)
        assertEquals(reference.value, total(bands).value, TOLERANCE)
    }

    @Test
    @DisplayName("A taller display spreads the surplus instead of piling it into one gap")
    fun `a taller display splits the surplus`() {
        val bands = homeBands(tall)

        assertTrue(bands.dockGap < (tall - fixed) / 2)
        assertTrue(bands.rowGap > 0.dp)
        assertEquals(tall.value, total(bands).value, TOLERANCE)
    }

    @Test
    @DisplayName("Bento tiles do not grow with display height")
    fun `bento tiles stay at their tokens`() {
        val bands = homeBands(tall)
        assertEquals(Tokens.Grid.RowHeight.hero4x2, bands.heroHeight)
        assertEquals(Tokens.Grid.RowHeight.tile2x2, bands.mediumHeight)
    }

    @Test
    @DisplayName("Short display: gaps go to zero and bento tiles absorb the remainder")
    fun `a short display shrinks the bento`() {
        val available = 708.dp
        val bands = homeBands(available)
        assertEquals(0.dp, bands.bentoGap)
        assertEquals(0.dp, bands.rowGap)
        assertEquals(0.dp, bands.dockGap)
        assertTrue(bands.heroHeight < Tokens.Grid.RowHeight.hero4x2)

        assertEquals(
            bands.heroHeight / Tokens.Grid.RowHeight.hero4x2,
            bands.mediumHeight / Tokens.Grid.RowHeight.tile2x2,
            0.001f,
        )
        assertEquals(available.value, total(bands).value, 0.01f)
    }

    @Test
    @DisplayName("Shrinking bento tiles has a floor — it never goes below 75 %")
    fun `shrinking has a floor`() {
        val bands = homeBands(400.dp)
        assertEquals(Tokens.Grid.RowHeight.hero4x2 * Tokens.Home.MIN_BENTO_SCALE, bands.heroHeight)
        assertEquals(Tokens.Grid.RowHeight.tile2x2 * Tokens.Home.MIN_BENTO_SCALE, bands.mediumHeight)
    }

    @Test
    @DisplayName("Zero height does not upset the home screen")
    fun `zero height does not crash`() {
        val bands = homeBands(0.dp)
        assertEquals(0.dp, bands.bentoGap)
        assertTrue(bands.heroHeight > 0.dp)
    }

    private companion object {
        const val TOLERANCE = 0.01f

        // The design grid is written down in whole dp, the derived one is not.
        const val AXIS_TOLERANCE = 1f
        val REFERENCE_WIDTH = 412.dp
    }

    @Test
    @DisplayName("The dock tray is as wide as a 4×2 hero tile inside the bento")
    fun `the dock is as wide as a hero tile`() {
        assertEquals(Tokens.Grid.ZoneBleed.start + Tokens.Padding.zone, Tokens.Grid.ZoneBleed.dock)
    }

    @Test
    @DisplayName("The derived bento column lands on the design grid of the 412 dp reference display")
    fun `the bento column matches the design grid`() {
        val column = bentoColumnWidth(REFERENCE_WIDTH)
        val zoneStart = Tokens.Grid.ZoneBleed.start + Tokens.Padding.zone
        val axes = List(Tokens.Grid.COLUMNS) { zoneStart + column / 2 + (column + Tokens.Grid.gutter) * it }

        assertEquals(Tokens.Grid.columnWidth.value, column.value, AXIS_TOLERANCE)
        listOf(59.dp, 157.dp, 255.dp, 353.dp).forEachIndexed { index, axis ->
            assertEquals(axis.value, axes[index].value, AXIS_TOLERANCE, "column ${index + 1}")
        }
    }

    // A 2x2 tile spans two columns, so half of it less the tile's padding is the column an action
    // chip inside it stands on — the geometry TemplateActionRow relies on.
    @Test
    @DisplayName("Two bento columns and a gutter are one 2×2 tile")
    fun `a medium tile spans two columns`() {
        val column = bentoColumnWidth(REFERENCE_WIDTH)
        val zone = REFERENCE_WIDTH - (Tokens.Grid.ZoneBleed.start + Tokens.Padding.zone) * 2

        assertEquals(((zone - Tokens.Grid.gutter) / 2).value, (column * 2 + Tokens.Grid.gutter).value, AXIS_TOLERANCE)
    }
}
