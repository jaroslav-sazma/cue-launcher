package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.feature.scenarios.scenario
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PinSlotTest {

    @Test
    fun `holders are read per position and skip the edited scenario`() {
        val scenarios = listOf(
            scenario(id = "gate", name = "Gate", size = TileSize.GRID_1X1).copy(pinnedPosition = 0),
            scenario(id = "self", name = "Self", size = TileSize.GRID_1X1).copy(pinnedPosition = 1),
            scenario(id = "other-size", name = "Hero", size = TileSize.GRID_2X2).copy(pinnedPosition = 0),
        )

        val holders = pinHolders(scenarios, TileSize.GRID_1X1, excludeId = "self")

        assertEquals(mapOf(0 to listOf("Gate")), holders)
    }

    @Test
    fun `a shared position lists the scenario the bento would show first`() {
        val scenarios = listOf(
            scenario(id = "quiet", name = "Quiet", size = TileSize.GRID_1X1, priority = 10)
                .copy(pinnedPosition = 0),
            scenario(id = "loud", name = "Loud", size = TileSize.GRID_1X1, priority = 90)
                .copy(pinnedPosition = 0),
        )

        assertEquals(
            mapOf(0 to listOf("Loud", "Quiet")),
            pinHolders(scenarios, TileSize.GRID_1X1, excludeId = null),
        )
    }

    @Test
    fun `a static scenario never outranks a triggered one for the name of a holder`() {
        val scenarios = listOf(
            scenario(id = "static", name = "Static", size = TileSize.GRID_1X1, priority = 900)
                .copy(pinnedPosition = 0),
            scenario(
                id = "triggered",
                name = "Triggered",
                size = TileSize.GRID_1X1,
                priority = 10,
                conditions = listOf(Condition.Charging()),
            ).copy(pinnedPosition = 0),
        )

        assertEquals(
            mapOf(0 to listOf("Triggered", "Static")),
            pinHolders(scenarios, TileSize.GRID_1X1, excludeId = null),
        )
    }

    @Test
    fun `slots mirror the row with the overflow one marked`() {
        val slots = pinSlots(TileSize.GRID_1X1, selected = 1, holders = mapOf(2 to listOf("Gate", "Sound")))

        assertEquals(listOf(0, 1, 2, 3), slots.map { it.position })
        assertEquals(listOf(true, true, true, false), slots.map { it.pinnable })
        assertEquals(listOf(false, true, false, false), slots.map { it.selected })
        assertEquals(listOf(false, false, true, false), slots.map { it.shared })
        assertEquals(listOf("Gate", "Sound"), slots[2].holders)
    }

    @Test
    fun `switching to a fixed position offers a free slot first`() {
        assertEquals(0, defaultPin(TileSize.GRID_1X1, holders = emptyMap()))
        assertEquals(2, defaultPin(TileSize.GRID_1X1, holders = mapOf(0 to listOf("a"), 1 to listOf("b"))))
    }

    @Test
    fun `with every position taken the choice falls on the first one`() {
        val holders = mapOf(0 to listOf("a"), 1 to listOf("b"), 2 to listOf("c"))

        assertEquals(0, defaultPin(TileSize.GRID_1X1, holders))
        assertNull(defaultPin(TileSize.HERO_4X2, holders = emptyMap()))
    }
}
