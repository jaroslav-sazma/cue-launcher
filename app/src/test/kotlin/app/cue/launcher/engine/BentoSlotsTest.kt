package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.bentoSlotCount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BentoSlotsTest {

    private fun entry(id: String, size: TileSize, pinnedPosition: Int? = null) = QueueEntry(
        scenario = scenario(id, size = size).copy(pinnedPosition = pinnedPosition),
        activation = Activation(scenarioId = id, since = 1_000L),
    )

    private fun queue(size: TileSize, vararg ids: String) =
        SizeClassQueue(size = size, entries = ids.map { id -> entry(id, size) })

    private fun queueOf(size: TileSize, vararg entries: QueueEntry) =
        SizeClassQueue(size = size, entries = entries.toList())

    private fun ids(slots: List<BentoSlot>) = slots.map { it.entry?.scenario?.id }

    @Test
    fun `slot count matches the bento zone layout`() {
        assertEquals(1, bentoSlotCount(TileSize.HERO_4X2))
        assertEquals(2, bentoSlotCount(TileSize.GRID_2X2))
        assertEquals(4, bentoSlotCount(TileSize.GRID_1X1))
    }

    @Test
    fun `an overlay has no slot in the bento zone`() {
        assertEquals(0, bentoSlotCount(TileSize.OVERLAY))
        assertTrue(bentoSlots(SizeClassQueue(TileSize.OVERLAY)).isEmpty())
    }

    @Test
    fun `an empty queue leaves all slots empty`() {
        val slots = bentoSlots(queue(TileSize.GRID_1X1))

        assertEquals(4, slots.size)
        assertTrue(slots.all { it.isEmpty })
        assertTrue(slots.none { it.hasOverflow })
    }

    @Test
    fun `contents occupy slots from the left in queue order`() {
        val slots = bentoSlots(queue(TileSize.GRID_1X1, "first", "second"))

        assertEquals(listOf("first", "second", null, null), ids(slots))
        assertEquals(listOf(0, 1, 2, 3), slots.map { it.index })
        assertTrue(slots.all { it.size == TileSize.GRID_1X1 })
    }

    @Test
    fun `an exactly filled class does not suffer overflow`() {
        val slots = bentoSlots(queue(TileSize.GRID_1X1, "a", "b", "c", "d"))

        assertEquals(listOf("a", "b", "c", "d"), ids(slots))
        assertTrue(slots.none { it.hasOverflow })
        assertEquals(0, slots.last().overflowCount)
    }

    @Test
    fun `the last slot carries the rest of the queue`() {
        val slots = bentoSlots(queue(TileSize.GRID_1X1, "a", "b", "c", "d", "e", "f"))

        assertEquals(listOf("a", "b", "c", "d"), ids(slots))
        assertEquals(listOf("e", "f"), slots.last().overflow.map { it.scenario.id })
        assertEquals(2, slots.last().overflowCount)

        assertTrue(slots.dropLast(1).none { it.hasOverflow })
    }

    @Test
    fun `stack pages are the visible content and overflow beneath it`() {
        val slots = bentoSlots(queue(TileSize.GRID_2X2, "left", "right", "extra", "more"))

        assertEquals(listOf("left"), slots.first().stackPages.map { it.scenario.id })
        assertEquals(
            listOf("right", "extra", "more"),
            slots.last().stackPages.map { it.scenario.id },
        )
    }

    @Test
    fun `an empty slot has no depth either`() {
        assertTrue(bentoSlots(queue(TileSize.GRID_2X2)).all { it.stackPages.isEmpty() })
    }

    @Test
    fun `the hero slot is also the overflow slot`() {
        val slots = bentoSlots(queue(TileSize.HERO_4X2, "hlavni", "pod", "uplne-dole"))

        assertEquals(1, slots.size)
        assertEquals("hlavni", slots.single().entry?.scenario?.id)
        assertEquals(listOf("pod", "uplne-dole"), slots.single().overflow.map { it.scenario.id })
    }

    @Test
    fun `the first of two 2x2 slots carries no overflow`() {
        val slots = bentoSlots(queue(TileSize.GRID_2X2, "vlevo", "vpravo", "navic"))

        assertEquals(listOf("vlevo", "vpravo"), ids(slots))
        assertFalse(slots.first().hasOverflow)
        assertEquals(listOf("navic"), slots.last().overflow.map { it.scenario.id })
    }

    @Test
    fun `the layout maps each queue to its own row`() {
        val queues = sizeClassQueues(
            scenarios = listOf(
                fallback("hero", size = TileSize.HERO_4X2),
                fallback("stredni", size = TileSize.GRID_2X2),
                fallback("maly", size = TileSize.GRID_1X1),
            ),
            activations = emptyList(),
            now = 0L,
        )

        val layout = bentoLayoutOf(queues)

        assertEquals(listOf("hero"), ids(layout.hero))
        assertEquals(listOf("stredni", null), ids(layout.medium))
        assertEquals(listOf("maly", null, null, null), ids(layout.small))
    }

    @Test
    fun `a pinned scenario holds its slot and the queue flows around it`() {
        val slots = bentoSlots(
            queueOf(
                TileSize.GRID_1X1,
                entry("a", TileSize.GRID_1X1),
                entry("pin", TileSize.GRID_1X1, pinnedPosition = 2),
                entry("b", TileSize.GRID_1X1),
                entry("c", TileSize.GRID_1X1),
            ),
        )

        assertEquals(listOf("a", "b", "pin", "c"), ids(slots))
    }

    @Test
    fun `a pin holds its slot even when nothing flows around it`() {
        val slots = bentoSlots(
            queueOf(TileSize.GRID_1X1, entry("pin", TileSize.GRID_1X1, pinnedPosition = 2)),
        )

        assertEquals(listOf(null, null, "pin", null), ids(slots))
    }

    @Test
    fun `a pinned scenario never lands in the overflow stack`() {
        val slots = bentoSlots(
            queueOf(
                TileSize.GRID_1X1,
                entry("a", TileSize.GRID_1X1),
                entry("b", TileSize.GRID_1X1),
                entry("c", TileSize.GRID_1X1),
                entry("d", TileSize.GRID_1X1),
                entry("e", TileSize.GRID_1X1),

                entry("pin", TileSize.GRID_1X1, pinnedPosition = 0),
            ),
        )

        assertEquals(listOf("pin", "a", "b", "c"), ids(slots))
        assertEquals(listOf("d", "e"), slots.last().overflow.map { it.scenario.id })
    }

    @Test
    fun `a position several scenarios share goes to the first in queue order`() {
        val slots = bentoSlots(
            queueOf(
                TileSize.GRID_1X1,
                entry("first", TileSize.GRID_1X1, pinnedPosition = 1),
                entry("second", TileSize.GRID_1X1, pinnedPosition = 1),
            ),
        )

        assertEquals(listOf("second", "first", null, null), ids(slots))
    }

    @Test
    fun `a pin on the overflow slot is no pin at all`() {
        val last = bentoSlotCount(TileSize.GRID_1X1) - 1
        val slots = bentoSlots(
            queueOf(
                TileSize.GRID_1X1,
                entry("a", TileSize.GRID_1X1),
                entry("pin", TileSize.GRID_1X1, pinnedPosition = last),
            ),
        )

        assertEquals(listOf("a", "pin", null, null), ids(slots))
    }

    @Test
    fun `the hero cannot be pinned, its only slot is the overflow one`() {
        val slots = bentoSlots(
            queueOf(
                TileSize.HERO_4X2,
                entry("a", TileSize.HERO_4X2),
                entry("pin", TileSize.HERO_4X2, pinnedPosition = 0),
            ),
        )

        assertEquals(listOf("a"), ids(slots))
        assertEquals(listOf("pin"), slots.single().overflow.map { it.scenario.id })
    }

    @Test
    fun `a 2x2 pin leaves the second slot to the overflow stack`() {
        val slots = bentoSlots(
            queueOf(
                TileSize.GRID_2X2,
                entry("a", TileSize.GRID_2X2),
                entry("b", TileSize.GRID_2X2),
                entry("pin", TileSize.GRID_2X2, pinnedPosition = 0),
            ),
        )

        assertEquals(listOf("pin", "a"), ids(slots))
        assertEquals(listOf("b"), slots.last().overflow.map { it.scenario.id })
    }

    @Test
    fun `an empty layout has all slots and no content`() {
        val layout = BentoLayout.EMPTY

        assertEquals(1, layout.hero.size)
        assertEquals(2, layout.medium.size)
        assertEquals(4, layout.small.size)
        assertTrue((layout.hero + layout.medium + layout.small).all { it.isEmpty })
    }
}
