package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.scenario.Condition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ScenarioEditsTest {

    private val original = scenario(
        id = "vecer-doma",
        name = "Večer doma",
        priority = 60,
        conditions = listOf(Condition.Charging()),
    )

    @Test
    fun `a copy gets a free id under the same rule as import collisions`() {
        val copy = duplicateOf(original, takenIds = setOf("vecer-doma"), name = "Večer doma (kopie)")

        assertEquals("vecer-doma-2", copy.id)
        assertNotEquals(original.id, copy.id)
    }

    @Test
    fun `another copy skips already taken numbers`() {
        val taken = setOf("vecer-doma", "vecer-doma-2")

        assertEquals("vecer-doma-3", duplicateOf(original, taken, name = "x").id)
    }

    @Test
    fun `a copy inherits everything except id and name`() {
        val copy = duplicateOf(original, setOf("vecer-doma"), name = "Večer doma (kopie)")

        assertEquals("Večer doma (kopie)", copy.name)
        assertEquals(original.priority, copy.priority)
        assertEquals(original.enabled, copy.enabled)
        assertEquals(original.conditions, copy.conditions)
        assertEquals(original.content, copy.content)
    }

    @Test
    fun `a copy keeps the pin, a shared position takes nothing from anybody`() {
        val pinned = original.copy(pinnedPosition = 0)

        assertEquals(0, duplicateOf(pinned, setOf("vecer-doma"), name = "Kopie").pinnedPosition)
    }
}
