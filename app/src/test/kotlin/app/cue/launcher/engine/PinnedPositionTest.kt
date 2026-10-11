package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TileSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// Several scenarios may be pinned to one position; these cover which of them is drawn there.
class PinnedPositionTest {

    private val now = 10_000L

    private fun running(id: String, since: Long = 1_000L) = Activation(scenarioId = id, since = since)

    private fun Scenario.pinnedTo(position: Int) = copy(pinnedPosition = position)

    private fun smallSlots(scenarios: List<Scenario>, activations: List<Activation>) =
        bentoSlots(sizeClassQueues(scenarios, activations, now).small).map { it.entry?.scenario?.id }

    @Test
    fun `a scenario with conditions takes a shared position from static content`() {
        val ids = smallSlots(
            scenarios = listOf(
                fallback("static", priority = 900).pinnedTo(2),
                scenario("triggered", priority = 0).pinnedTo(2),
            ),
            activations = listOf(running("triggered")),
        )

        assertEquals(listOf("static", null, "triggered", null), ids)
    }

    @Test
    fun `on a shared position the higher priority wins`() {
        val ids = smallSlots(
            scenarios = listOf(
                scenario("quiet", priority = 10).pinnedTo(2),
                scenario("loud", priority = 90).pinnedTo(2),
            ),
            activations = listOf(running("quiet"), running("loud")),
        )

        assertEquals(listOf("quiet", null, "loud", null), ids)
    }

    @Test
    fun `with equal priority the newer activation takes the shared position`() {
        val ids = smallSlots(
            scenarios = listOf(
                scenario("older", priority = 50).pinnedTo(1),
                scenario("newer", priority = 50).pinnedTo(1),
            ),
            activations = listOf(running("older", since = 1_000L), running("newer", since = 5_000L)),
        )

        assertEquals(listOf("older", "newer", null, null), ids)
    }

    @Test
    fun `a toggle pair keeps the same slot whichever half is running`() {
        val scenarios = listOf(
            scenario("silence", priority = 0).pinnedTo(2),
            scenario("sound", priority = 0).pinnedTo(2),
        )

        assertEquals(
            listOf(null, null, "silence", null),
            smallSlots(scenarios, activations = listOf(running("silence"))),
        )
        assertEquals(
            listOf(null, null, "sound", null),
            smallSlots(scenarios, activations = listOf(running("sound"))),
        )
    }

    @Test
    fun `the scenario that loses a shared position flows on like any other`() {
        val ids = smallSlots(
            scenarios = listOf(
                scenario("winner", priority = 90).pinnedTo(2),
                scenario("loser", priority = 10).pinnedTo(2),
                scenario("floating-a", priority = 50),
                scenario("floating-b", priority = 40),
            ),
            activations = listOf(
                running("winner"),
                running("loser"),
                running("floating-a"),
                running("floating-b"),
            ),
        )

        assertEquals(listOf("floating-a", "floating-b", "winner", "loser"), ids)
    }

    @Test
    fun `a loser with nowhere to go ends up in the overflow stack`() {
        val scenarios = listOf(
            scenario("winner", priority = 90).pinnedTo(2),
            scenario("loser", priority = 10).pinnedTo(2),
            scenario("floating-a", priority = 50),
            scenario("floating-b", priority = 40),
            scenario("floating-c", priority = 30),
        )
        val slots = bentoSlots(
            sizeClassQueues(scenarios, scenarios.map { running(it.id) }, now).small,
        )

        assertEquals(
            listOf("floating-a", "floating-b", "winner", "floating-c"),
            slots.map { it.entry?.scenario?.id },
        )
        assertEquals(listOf("loser"), slots.last().overflow.map { it.scenario.id })
    }

    @Test
    fun `sharing a position does not reserve it while nobody is running`() {
        val ids = smallSlots(
            scenarios = listOf(
                scenario("silence").pinnedTo(1),
                scenario("sound").pinnedTo(1),
                fallback("static"),
            ),
            activations = emptyList(),
        )

        assertEquals(listOf("static", null, null, null), ids)
    }
}
