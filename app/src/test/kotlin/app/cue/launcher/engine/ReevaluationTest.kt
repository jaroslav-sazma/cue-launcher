package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.Scenario
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReevaluationTest {

    private fun running(vararg ids: String): List<Activation> =
        ids.map { Activation(scenarioId = it, since = 0L) }

    private fun expiring(id: String, at: Long = 10_000L): List<Activation> =
        listOf(Activation(scenarioId = id, since = 0L, expiresAt = at))

    @Test
    fun `under any one holding condition is enough to activate`() {
        val scenario = scenario(
            "or",
            conditions = listOf(Condition.Charging(), Condition.AudioOutput()),
        ).copy(match = ConditionMatch.ANY)

        val plan = reevaluationPlan(listOf(scenario), emptyList(), SystemState(charging = true, audioOutput = false))

        assertEquals(listOf(scenario), plan.activate)
    }

    @Test
    fun `under any the activation ends only once no condition holds`() {
        val scenario = scenario(
            "or",
            conditions = listOf(Condition.Charging(), Condition.AudioOutput()),
        ).copy(match = ConditionMatch.ANY)
        val state = { charging: Boolean -> SystemState(charging = charging, audioOutput = false) }

        assertTrue(reevaluationPlan(listOf(scenario), running("or"), state(true)).isEmpty)
        assertEquals(listOf("or"), reevaluationPlan(listOf(scenario), running("or"), state(false)).deactivate)
    }

    @Test
    fun `under any a running event outlives a state condition that does not hold`() {
        val scenario = scenario(
            "or",
            conditions = listOf(Condition.Charging(), Condition.HaEvent(code = "zvonek")),
        ).copy(match = ConditionMatch.ANY)

        val plan = reevaluationPlan(listOf(scenario), expiring("or"), SystemState(charging = false))

        assertTrue(plan.isEmpty)
    }

    @Test
    fun `under any a state condition takes the expiry off a running event`() {
        val scenario = scenario(
            "or",
            conditions = listOf(Condition.Charging(), Condition.HaEvent(code = "zvonek")),
        ).copy(match = ConditionMatch.ANY)

        val plan = reevaluationPlan(listOf(scenario), expiring("or"), SystemState(charging = true))

        assertEquals(listOf(scenario), plan.activate)
        assertTrue(plan.deactivate.isEmpty())
    }

    @Test
    fun `an event-only scenario under any is left to its countdown`() {
        val scenario = scenario(
            "or",
            conditions = listOf(Condition.HaEvent(code = "a"), Condition.HaEvent(code = "b")),
        ).copy(match = ConditionMatch.ANY)

        assertTrue(reevaluationPlan(listOf(scenario), expiring("or"), SystemState()).isEmpty)
        assertTrue(reevaluationPlan(listOf(scenario), emptyList(), SystemState()).isEmpty)
    }

    @Test
    fun `a valid state activates a scenario`() {
        val scenario = scenario("stav", conditions = listOf(Condition.Charging()))

        val plan = reevaluationPlan(listOf(scenario), emptyList(), SystemState(charging = true))

        assertEquals(listOf(scenario), plan.activate)
        assertTrue(plan.deactivate.isEmpty())
    }

    @Test
    fun `a running activation is not activated again`() {
        val scenario = scenario("stav", conditions = listOf(Condition.Charging()))

        val plan = reevaluationPlan(listOf(scenario), running("stav"), SystemState(charging = true))

        assertTrue(plan.isEmpty)
    }

    @Test
    fun `the end of a state ends the activation`() {
        val scenario = scenario("stav", conditions = listOf(Condition.Charging()))

        val plan = reevaluationPlan(listOf(scenario), running("stav"), SystemState(charging = false))

        assertEquals(listOf("stav"), plan.deactivate)
    }

    @Test
    fun `an unmeasured state ends a running activation`() {
        val scenario = scenario("stav", conditions = listOf(Condition.Charging()))

        val plan = reevaluationPlan(listOf(scenario), running("stav"), SystemState())

        assertEquals(listOf("stav"), plan.deactivate)
        assertTrue(plan.activate.isEmpty())
    }

    @Test
    fun `an unmeasured state activates nothing`() {
        val scenario = scenario("stav", conditions = listOf(Condition.Charging()))

        val plan = reevaluationPlan(listOf(scenario), emptyList(), SystemState())

        assertTrue(plan.isEmpty)
    }

    @Test
    fun `an instant scenario is not activated by re-evaluation`() {
        val scenario = scenario("pulz", conditions = listOf(Condition.HaEvent(code = "zvonek")))

        val plan = reevaluationPlan(listOf(scenario), emptyList(), SystemState())

        assertTrue(plan.isEmpty)
    }

    @Test
    fun `a running instant scenario survives an unmeasured state`() {
        val scenario = scenario(
            "pulz",
            conditions = listOf(Condition.HaEvent(code = "zvonek"), Condition.Charging()),
        )

        val plan = reevaluationPlan(listOf(scenario), running("pulz"), SystemState())

        assertTrue(plan.isEmpty)
    }

    @Test
    fun `a running instant scenario ends on an invalid state condition`() {
        val scenario = scenario(
            "pulz",
            conditions = listOf(Condition.HaEvent(code = "zvonek"), Condition.Charging()),
        )

        val plan = reevaluationPlan(listOf(scenario), running("pulz"), SystemState(charging = false))

        assertEquals(listOf("pulz"), plan.deactivate)
    }

    @Test
    fun `a disabled scenario ends its activation`() {
        val scenario = scenario("stav", conditions = listOf(Condition.Charging())).copy(enabled = false)

        val plan = reevaluationPlan(listOf(scenario), running("stav"), SystemState(charging = true))

        assertEquals(listOf("stav"), plan.deactivate)
        assertTrue(plan.activate.isEmpty())
    }

    @Test
    fun `a fallback has no activation`() {
        val plan = reevaluationPlan(listOf(fallback("trvaly")), running("trvaly"), SystemState())

        assertEquals(listOf("trvaly"), plan.deactivate)
    }

    @Test
    fun `an activation without a scenario is deleted`() {
        val plan = reevaluationPlan(emptyList(), running("sirotek"), SystemState())

        assertEquals(listOf("sirotek"), plan.deactivate)
    }

    @Test
    fun `the plan handles multiple scenarios at once`() {
        val scenarios: List<Scenario> = listOf(
            scenario("nabiji", conditions = listOf(Condition.Charging())),
            scenario("sluchatka", conditions = listOf(Condition.AudioOutput())),
            fallback("trvaly"),
        )

        val plan = reevaluationPlan(
            scenarios,
            running("sluchatka", "sirotek"),
            SystemState(charging = true, audioOutput = false),
        )

        assertEquals(listOf("nabiji"), plan.activate.map(Scenario::id))
        assertEquals(listOf("sluchatka", "sirotek"), plan.deactivate)
    }
}
