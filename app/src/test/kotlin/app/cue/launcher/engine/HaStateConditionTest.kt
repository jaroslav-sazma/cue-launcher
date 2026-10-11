package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HaStateConditionTest {

    private val tv = Condition.HaState(entity = "media_player.living_room_tv", states = listOf("playing", "paused"))

    private fun reported(vararg states: Pair<String, String>) = SystemState(ha = HaStates.of(states.toMap()))

    @Test
    fun `the condition holds while the entity is in one of its states`() {
        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(tv, reported("media_player.living_room_tv" to "paused")),
        )
        assertEquals(
            ConditionOutcome.FAILS,
            stateConditionOutcome(tv, reported("media_player.living_room_tv" to "off")),
        )
    }

    @Test
    fun `states compare in any case and without padding`() {
        val loose = tv.copy(entity = " Media_Player.Living_Room_TV ", states = listOf(" PLAYING "))

        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(loose, reported("media_player.living_room_tv" to "Playing ")),
        )
    }

    @Test
    fun `an entity without a state is unknown, negated or not`() {
        val elsewhere = reported("light.kitchen" to "on")

        assertEquals(ConditionOutcome.UNKNOWN, stateConditionOutcome(tv, SystemState()))
        assertEquals(ConditionOutcome.UNKNOWN, stateConditionOutcome(tv, elsewhere))
        assertEquals(ConditionOutcome.UNKNOWN, stateConditionOutcome(tv.copy(negate = true), elsewhere))
    }

    @Test
    fun `negation turns a measured state around`() {
        val off = tv.copy(negate = true)

        assertEquals(
            ConditionOutcome.HOLDS,
            stateConditionOutcome(off, reported("media_player.living_room_tv" to "off")),
        )
        assertEquals(
            ConditionOutcome.FAILS,
            stateConditionOutcome(off, reported("media_player.living_room_tv" to "playing")),
        )
    }

    @Test
    fun `an unknown state hides a tile it alone holds up`() {
        val tile = scenario("tv", conditions = listOf(tv))
        val running = listOf(Activation(scenarioId = "tv", since = 0L))

        assertEquals(listOf("tv"), reevaluationPlan(listOf(tile), running, reported()).deactivate)
        assertTrue(
            reevaluationPlan(listOf(tile), running, reported("media_player.living_room_tv" to "playing")).isEmpty,
        )
    }

    @Test
    fun `the gate follows the entities of enabled scenarios only`() {
        val door = Condition.HaState(entity = "BINARY_SENSOR.door", states = listOf("on"))
        val scenarios = listOf(
            scenario("tv", conditions = listOf(tv, Condition.Charging())),
            scenario("door", conditions = listOf(door)),
            scenario("off", conditions = listOf(tv.copy(entity = "light.hall"))).copy(enabled = false),
            scenario("typo", conditions = listOf(tv.copy(entity = "the TV"))),
        )

        assertEquals(setOf("media_player.living_room_tv", "binary_sensor.door"), haGateEntities(scenarios))
        assertEquals(setOf(StateAspect.HA, StateAspect.POWER), stateAspectsOf(scenarios))
    }

    @Test
    fun `a reading of Home Assistant survives the merge of other aspects`() {
        val merged = reported("media_player.living_room_tv" to "playing").mergedWith(SystemState(charging = true))

        assertEquals("playing", merged.ha?.stateOf("media_player.living_room_tv"))
        assertEquals(true, merged.charging)
    }
}
