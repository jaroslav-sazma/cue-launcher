package app.cue.launcher.system

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.engine.FakeScenarioSource
import app.cue.launcher.engine.HaStateSource
import app.cue.launcher.engine.HaStates
import app.cue.launcher.engine.StateAspect
import app.cue.launcher.engine.StateNeeds
import app.cue.launcher.engine.scenario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HaConditionsTest {

    private val scenarios = FakeScenarioSource()

    private val asked = mutableListOf<Pair<String, Set<String>>>()

    private val states = object : HaStateSource {
        override fun observeStates(entities: Set<String>): Flow<Map<String, String>> = answer("text", entities)

        override fun observeGate(entities: Set<String>): Flow<Map<String, String>> = answer("gate", entities)

        override fun observeGateForEvent(entities: Set<String>): Flow<Map<String, String>> =
            answer("event", entities)

        override fun refresh() = Unit

        override fun nudge() = Unit

        private fun answer(path: String, entities: Set<String>): Flow<Map<String, String>> {
            asked += path to entities
            return flowOf(mapOf(TV to " Playing"))
        }
    }

    private val tv = Condition.HaState(entity = TV, states = listOf("playing"))

    @Test
    fun `the home screen follows the gate of every enabled scenario`() = runTest {
        scenarios.emit(
            scenario("tv", conditions = listOf(tv)),
            scenario("off", conditions = listOf(tv.copy(entity = "light.hall"))).copy(enabled = false),
        )

        val state = HaConditions(scenarios, states).observe().first()

        assertEquals(listOf("gate" to setOf(TV)), asked)
        assertEquals(HaStates(mapOf(TV to "playing")), state.ha)
    }

    @Test
    fun `an event reads only the entities its listeners name, on the path that waits for the read`() = runTest {
        scenarios.emit(
            scenario("tv", conditions = listOf(tv)),
            scenario("hall", conditions = listOf(tv.copy(entity = "light.hall"))),
        )

        val state = HaConditions(scenarios, states)
            .observeForEvent(StateNeeds(setOf(StateAspect.HA), haEntities = setOf(TV)))
            .first()

        assertEquals(listOf("event" to setOf(TV)), asked)
        assertEquals(HaStates(mapOf(TV to "playing")), state.ha)
    }

    private companion object {
        const val TV = "media_player.living_room_tv"
    }
}
