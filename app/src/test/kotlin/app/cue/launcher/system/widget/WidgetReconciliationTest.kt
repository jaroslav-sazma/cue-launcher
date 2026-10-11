package app.cue.launcher.system.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetReconciliationTest {

    @Test
    fun `agreement means no work`() {
        val plan = reconcileWidgets(hostIds = setOf(7, 9), knownIds = setOf(7, 9), isBound = { true })

        assertTrue(plan.isEmpty)
        assertEquals(emptyList<Int>(), plan.releaseFromHost)
        assertEquals(emptyList<Int>(), plan.forgetRows)
    }

    @Test
    fun `a host id without a row is released`() {
        val plan = reconcileWidgets(hostIds = setOf(7, 42), knownIds = setOf(7), isBound = { true })

        assertEquals(listOf(42), plan.releaseFromHost)
        assertEquals(emptyList<Int>(), plan.forgetRows)
    }

    @Test
    fun `a row without a host id is discarded`() {
        val plan = reconcileWidgets(hostIds = setOf(7), knownIds = setOf(7, 42), isBound = { true })

        assertEquals(emptyList<Int>(), plan.releaseFromHost)
        assertEquals(listOf(42), plan.forgetRows)
    }

    @Test
    fun `a known id without a provider is both released and discarded`() {
        val plan = reconcileWidgets(
            hostIds = setOf(7, 9),
            knownIds = setOf(7, 9),
            isBound = { it != 9 },
        )

        assertEquals(listOf(9), plan.releaseFromHost)
        assertEquals(listOf(9), plan.forgetRows)
    }

    @Test
    fun `only the intersection is asked of the provider`() {
        val asked = mutableListOf<Int>()

        reconcileWidgets(hostIds = setOf(1, 2), knownIds = setOf(2, 3)) { appWidgetId ->
            asked += appWidgetId
            true
        }

        assertEquals(listOf(2), asked)
    }

    @Test
    fun `an empty host and an empty registry are in agreement`() {
        assertTrue(reconcileWidgets(emptySet(), emptySet(), isBound = { true }).isEmpty)
    }

    @Test
    fun `the result is sorted`() {
        val plan = reconcileWidgets(
            hostIds = setOf(30, 10, 20),
            knownIds = setOf(5, 1),
            isBound = { true },
        )

        assertEquals(listOf(10, 20, 30), plan.releaseFromHost)
        assertEquals(listOf(1, 5), plan.forgetRows)
    }

    @Test
    fun `a widget of an unsaved scenario is an orphan`() {
        assertEquals(
            listOf(2, 7),
            orphanScenarioWidgets(scenarioBoundIds = setOf(7, 2, 5), referencedIds = setOf(5)),
        )
    }

    @Test
    fun `a widget referenced by a scenario is not an orphan`() {
        assertEquals(
            emptyList<Int>(),
            orphanScenarioWidgets(scenarioBoundIds = setOf(5), referencedIds = setOf(5, 9)),
        )
    }
}
