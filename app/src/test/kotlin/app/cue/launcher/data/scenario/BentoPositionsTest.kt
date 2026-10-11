package app.cue.launcher.data.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BentoPositionsTest {

    @Test
    fun `the overflow slot of every class stays out of reach`() {
        assertEquals(0..2, pinnablePositions(TileSize.GRID_1X1))
        assertEquals(0..0, pinnablePositions(TileSize.GRID_2X2))
        assertTrue(pinnablePositions(TileSize.HERO_4X2).isEmpty())
        assertTrue(pinnablePositions(TileSize.OVERLAY).isEmpty())
    }

    @Test
    fun `only the two smaller classes can be pinned at all`() {
        assertTrue(canPin(TileSize.GRID_1X1))
        assertTrue(canPin(TileSize.GRID_2X2))
        assertFalse(canPin(TileSize.HERO_4X2))
        assertFalse(canPin(TileSize.OVERLAY))
    }

    @Test
    fun `a pin fits only inside its own size class`() {
        assertTrue(pinFits(TileSize.GRID_1X1, 2))
        assertFalse(pinFits(TileSize.GRID_1X1, 3))
        assertFalse(pinFits(TileSize.GRID_1X1, -1))
        assertFalse(pinFits(TileSize.GRID_2X2, 1))
        assertFalse(pinFits(TileSize.HERO_4X2, 0))
        assertTrue(pinFits(TileSize.HERO_4X2, null))
    }
}
