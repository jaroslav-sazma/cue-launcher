package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.feature.scenarios.scenario
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PriorityFormTest {

    @Test
    fun `reasons for an invalid number are distinguished`() {
        assertEquals(PriorityFormError.EMPTY, priorityError(""))
        assertEquals(PriorityFormError.EMPTY, priorityError("   "))
        assertEquals(PriorityFormError.NOT_A_NUMBER, priorityError("high"))
        assertEquals(PriorityFormError.NOT_A_NUMBER, priorityError("50 %"))
        assertEquals(PriorityFormError.NEGATIVE, priorityError("-5"))
    }

    @Test
    fun `a number above the format ceiling is reported as too large`() {
        assertEquals(PriorityFormError.TOO_LARGE, priorityError("2147483648"))
        assertNull(priorityError("2147483647"))
        assertEquals(2147483647, parsePriority("2147483647"))
    }

    @Test
    fun `a valid number passes, including zero and surrounding spaces`() {
        assertNull(priorityError("0"))
        assertEquals(0, parsePriority("0"))
        assertEquals(1000, parsePriority("1000"))
        assertEquals(60, parsePriority("  60 "))
        assertNull(parsePriority("-5"))
    }

    @Test
    fun `the live list orders by priority descending`() {
        val peers = priorityPeers(
            scenarios = listOf(
                scenario(id = "a", name = "Morning weather", priority = 40, conditions = CHARGING),
                scenario(id = "b", name = "Media controls", priority = 70, conditions = CHARGING),
            ),
            size = TileSize.GRID_2X2,
            draft = draftPeer(priority = 60),
        )

        assertEquals(listOf("Media controls", "Evening at home", "Morning weather"), peers.map { it.name })
        assertEquals(listOf(70, 60, 40), peers.map { it.priority })
    }

    @Test
    fun `persistent content ranks behind scenarios with conditions`() {
        val peers = priorityPeers(
            scenarios = listOf(
                scenario(id = "a", name = "Clock", priority = 200),
                scenario(id = "b", name = "Morning weather", priority = 40, conditions = CHARGING),
            ),
            size = TileSize.GRID_2X2,
            draft = draftPeer(priority = 10),
        )

        assertEquals(listOf("Morning weather", "Evening at home", "Clock"), peers.map { it.name })
        assertEquals(listOf(false, false, true), peers.map { it.fallback })
    }

    @Test
    fun `the list takes only scenarios of the same size type`() {
        val peers = priorityPeers(
            scenarios = listOf(
                scenario(id = "a", name = "Large", size = TileSize.HERO_4X2, priority = 90, conditions = CHARGING),
                scenario(id = "b", name = "Small", size = TileSize.GRID_1X1, priority = 80, conditions = CHARGING),
            ),
            size = TileSize.GRID_1X1,
            draft = draftPeer(priority = 60),
        )

        assertEquals(listOf("Small", "Evening at home"), peers.map { it.name })
    }

    @Test
    fun `a draft scenario is marked in the list`() {
        val peers = priorityPeers(
            scenarios = listOf(scenario(id = "a", name = "Media controls", priority = 70, conditions = CHARGING)),
            size = TileSize.GRID_2X2,
            draft = draftPeer(priority = 60),
        )

        assertEquals(listOf(false, true), peers.map { it.isDraft })
    }

    @Test
    fun `draft persistent content falls behind conditional ones`() {
        val peers = priorityPeers(
            scenarios = listOf(scenario(id = "a", name = "Media controls", priority = 10, conditions = CHARGING)),
            size = TileSize.GRID_2X2,
            draft = draftPeer(priority = 90, fallback = true),
        )

        assertEquals(listOf("Media controls", "Evening at home"), peers.map { it.name })
    }

    @Test
    fun `equal priority orders by name`() {
        val peers = priorityPeers(
            scenarios = listOf(
                scenario(id = "a", name = "News", priority = 60, conditions = CHARGING),
                scenario(id = "b", name = "Car", priority = 60, conditions = CHARGING),
                scenario(id = "c", name = "Evening at home", priority = 60, conditions = CHARGING),
            ),
            size = TileSize.GRID_2X2,
            draft = draftPeer(priority = 60),
        )

        assertEquals(listOf("Car", "Evening at home", "Evening at home", "News"), peers.map { it.name })
        assertEquals(listOf(false, false, true, false), peers.map { it.isDraft })
    }

    @Test
    fun `an edited scenario does not appear twice in the live list`() {
        val saved = scenario(id = "s1", name = "Evening at home", priority = 40, conditions = CHARGING)
        val other = scenario(id = "s2", name = "News", priority = 60, conditions = CHARGING)

        val peers = priorityPeers(
            scenarios = listOf(saved, other),
            size = TileSize.GRID_2X2,
            draft = draftPeer(priority = 80),
            excludeId = "s1",
        )

        assertEquals(listOf("Evening at home", "News"), peers.map { it.name })
        assertEquals(listOf(true, false), peers.map { it.isDraft })
    }

    private fun draftPeer(priority: Int, fallback: Boolean = false) = PriorityPeer(
        name = "Evening at home",
        priority = priority,
        fallback = fallback,
        isDraft = true,
    )

    private companion object {

        val CHARGING = listOf(Condition.Charging())
    }
}
