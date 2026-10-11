package app.cue.launcher.feature.scenarios

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PriorityDragTest {

    private val group = listOf(
        scenario(id = "a", name = "A", priority = 80),
        scenario(id = "b", name = "B", priority = 70),
        scenario(id = "c", name = "C", priority = 40),
        scenario(id = "d", name = "D", priority = 10),
    )

    @Test
    fun `the number lands in the middle of the gap between the new neighbors`() {
        val move = priorityMove(group, from = 2, to = 1)

        assertEquals(75, move?.priority)
        assertEquals(listOf(PriorityUpdate("c", 75)), move?.updates)
    }

    @Test
    fun `at the edge of the group it steps by the spacing`() {
        assertEquals(90, priorityMove(group, from = 2, to = 0)?.priority)
        assertEquals(0, priorityMove(group, from = 0, to = 3)?.priority)
    }

    @Test
    fun `dragging to its own place writes nothing`() {
        assertNull(priorityMove(group, from = 2, to = 2))
        assertNull(priorityMove(group, from = 9, to = 0))
    }

    @Test
    fun `an exhausted gap renumbers the group with a spacing of ten`() {
        val tight = listOf(
            scenario(id = "a", name = "A", priority = 3),
            scenario(id = "b", name = "B", priority = 2),
            scenario(id = "c", name = "C", priority = 1),
        )

        val move = priorityMove(tight, from = 2, to = 1)

        assertEquals(10, move?.priority)
        assertEquals(
            listOf(PriorityUpdate("a", 20), PriorityUpdate("c", 10), PriorityUpdate("b", 0)),
            move?.updates,
        )
    }

    @Test
    fun `matching numbers lead to renumbering`() {
        val flat = List(3) { index -> scenario(id = "s$index", name = "S$index", priority = 0) }

        val move = priorityMove(flat, from = 0, to = 2)

        assertEquals(0, move?.priority)
        assertEquals(listOf(PriorityUpdate("s1", 20), PriorityUpdate("s2", 10)), move?.updates)
    }

    @Test
    fun `moving to the end below zero renumbers`() {
        val low = listOf(
            scenario(id = "a", name = "A", priority = 5),
            scenario(id = "b", name = "B", priority = 0),
        )

        val move = priorityMove(low, from = 0, to = 1)

        assertTrue((move?.updates?.size ?: 0) > 1)
        assertEquals(0, move?.priority)
    }

    @Test
    fun `the gap between neighbors is only computed when a number fits into it`() {
        assertEquals(75, priorityBetween(above = 80, below = 70))
        assertEquals(71, priorityBetween(above = 72, below = 70))
        assertNull(priorityBetween(above = 71, below = 70))
        assertNull(priorityBetween(above = 70, below = 70))
        assertEquals(PRIORITY_SPACING, priorityBetween(above = null, below = 0))
        assertNull(priorityBetween(above = 0, below = null))
        assertNull(priorityBetween(above = null, below = null))
    }
}
