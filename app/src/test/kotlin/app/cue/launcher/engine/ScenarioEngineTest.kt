package app.cue.launcher.engine

import app.cash.turbine.test
import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScenarioEngineTest {

    private val scenarios = FakeScenarioSource()

    private val activations = FakeActivationStore()

    private val haStates = FakeHaStateSource()

    private val macLocked = MutableStateFlow<Boolean?>(null)

    private fun TestScope.engine() = ScenarioEngine(
        scenarios = scenarios,
        activations = activations,
        clock = WallClock { testScheduler.currentTime },
        haStates = haStates,
        macLock = MacLockSource { macLocked },
    )

    private fun ids(queue: SizeClassQueue) = queue.entries.map { it.scenario.id }

    @Test
    fun `the queue fills from activations`() = runTest {
        val engine = engine()
        scenarios.emit(scenario("trigger"), fallback("trvaly"))

        engine.queues.test {
            assertEquals(listOf("trvaly"), ids(awaitItem().small))

            engine.activate(scenarios.scenario("trigger"))

            assertEquals(listOf("trigger", "trvaly"), ids(awaitItem().small))
        }
    }

    @Test
    fun `the same set does not emit again`() = runTest {
        val engine = engine()
        scenarios.emit(scenario("a"))

        engine.queues.test {
            awaitItem()

            scenarios.emit(scenario("a"))
            activations.reemit()
            runCurrent()

            expectNoEvents()
        }
    }

    @Test
    fun `a state-based activation does not expire`() = runTest {
        val engine = engine()
        val scenario = scenario("stav", conditions = listOf(Condition.Charging()))
        scenarios.emit(scenario)

        val activation = engine.activate(scenario, trigger = scenario.instantCondition)

        assertNull(activation?.expiresAt)
        assertEquals(activation, activations.activation("stav"))
    }

    @Test
    fun `a repeated trigger moves the expiry and keeps the order`() = runTest {
        val engine = engine()
        val scenario = scenario("pulz", conditions = listOf(Condition.HaEvent(code = "x", durationSec = 60)))
        scenarios.emit(scenario)

        val first = engine.activate(scenario, trigger = scenario.instantCondition)
        advanceTimeBy(30_000L)
        val repeated = engine.activate(scenario, trigger = scenario.instantCondition)

        assertEquals(first?.since, repeated?.since)
        assertEquals(30_000L + 60_000L, repeated?.expiresAt)
    }

    @Test
    fun `deactivation removes a scenario from the queue`() = runTest {
        val engine = engine()
        val scenario = scenario("pulz", conditions = listOf(Condition.HaEvent(code = "x", durationSec = 60)))
        scenarios.emit(scenario)
        engine.activate(scenario, trigger = scenario.instantCondition)

        engine.queues.test {
            assertEquals(listOf("pulz"), ids(awaitItem().small))

            assertTrue(engine.deactivate("pulz"))

            assertTrue(awaitItem().small.isEmpty)
            assertFalse(engine.deactivate("pulz"))
        }
    }

    @Test
    fun `foreground clears the activation the moment it expires`() = runTest {
        val engine = engine()
        val scenario = scenario("pulz", conditions = listOf(Condition.HaEvent(code = "x", durationSec = 60)))
        scenarios.emit(scenario, fallback("trvaly"))
        engine.activate(scenario, trigger = scenario.instantCondition)
        backgroundScope.launch { engine.runForeground() }

        engine.queues.test {
            assertEquals(listOf("pulz", "trvaly"), ids(awaitItem().small))

            advanceTimeBy(59_000L)
            expectNoEvents()

            advanceTimeBy(1_001L)
            assertEquals(listOf("trvaly"), ids(awaitItem().small))
            assertTrue(activations.rows().isEmpty())
        }
    }

    @Test
    fun `foreground cleans up an activation that survived a process restart`() = runTest {
        val engine = engine()
        scenarios.emit(scenario("zombie"))
        activations.put(Activation(scenarioId = "zombie", since = 0L, expiresAt = 1_000L))
        advanceTimeBy(5_000L)

        engine.queues.test {
            assertTrue(awaitItem().small.isEmpty)

            backgroundScope.launch { engine.runForeground() }
            runCurrent()

            assertTrue(activations.rows().isEmpty())
            expectNoEvents()
        }
    }

    @Test
    fun `an earlier-expiring activation reschedules the wait`() = runTest {
        val engine = engine()
        val dlouhy = scenario("dlouhy", conditions = listOf(Condition.HaEvent(code = "a", durationSec = 300)))
        val kratky = scenario("kratky", conditions = listOf(Condition.HaEvent(code = "b", durationSec = 30)))
        scenarios.emit(dlouhy, kratky)
        engine.activate(dlouhy, trigger = dlouhy.instantCondition)
        backgroundScope.launch { engine.runForeground() }
        runCurrent()

        advanceTimeBy(1_000L)
        engine.activate(kratky, trigger = kratky.instantCondition)

        engine.queues.test {
            assertEquals(listOf("kratky", "dlouhy"), ids(awaitItem().small))

            advanceTimeBy(30_001L)

            assertEquals(listOf("dlouhy"), ids(awaitItem().small))
        }
    }

    @Test
    fun `without foreground the activation stays in the database`() = runTest {
        val engine = engine()
        val scenario = scenario("pulz", conditions = listOf(Condition.HaEvent(code = "x", durationSec = 60)))
        scenarios.emit(scenario)
        engine.activate(scenario, trigger = scenario.instantCondition)

        advanceTimeBy(120_000L)

        assertEquals(1, activations.rows().size)
        assertEquals(1, engine.pruneExpired())
    }

    @Test
    fun `a fallback does not get an activation`() = runTest {
        val engine = engine()
        val trvaly = fallback("trvaly")
        scenarios.emit(trvaly)

        assertNull(engine.activate(trvaly))
        assertTrue(activations.rows().isEmpty())
    }

    @Test
    fun `without an HA value states are not read`() = runTest {
        val engine = engine()
        scenarios.emit(fallback("trvaly"))

        engine.queues.test {
            awaitItem()

            assertEquals(emptySet<String>(), haStates.requested)
        }
    }

    @Test
    fun `an HA state fills in the tile only once it arrives`() = runTest {
        val engine = engine()
        val waiting = fallback("teplomer").copy(content = haTemplate("{ha:sensor.teplota}"))
        scenarios.emit(waiting, fallback("trvaly"))

        engine.queues.test {
            assertEquals(listOf("trvaly"), ids(awaitItem().small))
            assertEquals(setOf("sensor.teplota"), haStates.requested)

            haStates.emit("sensor.teplota" to "21.5")

            val filled = awaitItem().small.entries.map { it.scenario.id }
            assertEquals(listOf("teplomer", "trvaly"), filled)
        }
    }

    @Test
    fun `home screen foreground re-reads states`() = runTest {
        val engine = engine()
        scenarios.emit(fallback("trvaly"))

        backgroundScope.launch { engine.runForeground() }
        runCurrent()

        assertEquals(1, haStates.refreshes)
    }

    @Test
    fun `a Lock Mac tile carries the Mac's lock state as it changes`() = runTest {
        val engine = engine()
        scenarios.emit(fallback("mac").copy(content = macLockTemplate()))

        engine.queues.test {
            assertNull(awaitItem().small.entries.single().macLocked)

            macLocked.value = false
            assertEquals(false, awaitItem().small.entries.single().macLocked)

            macLocked.value = true
            assertEquals(true, awaitItem().small.entries.single().macLocked)
        }
    }

    @Test
    fun `without a Lock Mac tile the lock state is not followed`() = runTest {
        val engine = engine()
        scenarios.emit(fallback("trvaly"))

        engine.queues.test {
            awaitItem()

            assertEquals(0, macLocked.subscriptionCount.value)
            macLocked.value = true
            runCurrent()
            expectNoEvents()
        }
    }
}

private fun macLockTemplate() = TileContent.Template(
    layout = TemplateLayout.ICON_TEXT,
    items = listOf(TemplateItem(actions = listOf(TapAction.SystemAction(SystemActionType.MAC_LOCK)))),
)

private fun haTemplate(text: String) = TileContent.Template(
    layout = TemplateLayout.ICON_TEXT,
    text = text,
    items = listOf(TemplateItem(actions = listOf(TapAction.HaAction(service = "light.turn_off")))),
)
