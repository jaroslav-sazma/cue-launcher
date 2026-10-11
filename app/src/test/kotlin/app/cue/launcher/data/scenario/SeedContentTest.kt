package app.cue.launcher.data.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeedContentTest {

    private val seed = clockSeedScenario("Hodiny")

    @Test
    fun `the seed is a 4x2 clock`() {
        assertEquals(TileSize.HERO_4X2, seed.size)
        assertEquals(TileContent.LauncherWidget(LauncherWidgetType.CLOCK), seed.content)
        assertEquals("Hodiny", seed.name)
    }

    @Test
    fun `the seed is an enabled fallback`() {
        assertTrue(seed.isFallback, "the seed has no conditions")
        assertTrue(seed.enabled)
        assertEquals(0, seed.priority)
    }

    @Test
    fun `the seed is a valid scenario`() {
        assertEquals(emptyList<ScenarioDefect>(), validateScenario(seed))
    }

    @Test
    fun `the seed has a stable id`() {
        assertEquals(CLOCK_SEED_SCENARIO_ID, seed.id)
        assertEquals(seed.id, clockSeedScenario("Clock").id)
    }

    @Test
    fun `a seed name follows the language`() {
        val relabeled = relabeledSeedScenario(seed, "Clock", names)

        assertEquals("Clock", relabeled?.name, "Czech seed name in an English app")
    }

    @Test
    fun `a seed name already in the language is left alone`() {
        assertNull(relabeledSeedScenario(seed, "Hodiny", names))
    }

    @Test
    fun `a renamed seed is never relabelled`() {
        val renamed = seed.copy(name = "Kitchen clock")

        assertNull(relabeledSeedScenario(renamed, "Clock", names))
    }

    private companion object {
        val names = setOf("Clock", "Hodiny")
    }
}
