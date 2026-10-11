package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TriggerPayload
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
@Inject
class ScenarioEngine(
    private val scenarios: ScenarioSource,
    private val activations: ActivationStore,
    private val clock: WallClock,
    private val haStates: HaStateSource,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val queues: Flow<BentoQueues> =
        combine(scenarios.observeScenarios(), activations.observeActivations(), ::EngineInput)
            .distinctUntilChanged()
            .flatMapLatest { input ->
                val refs = haStateRefs(input.scenarios, input.activations, clock.nowMillis())
                haStates.observeStates(refs).map { states ->
                    sizeClassQueues(input.scenarios, input.activations, clock.nowMillis(), states)
                }
            }
            .distinctUntilChanged()

    suspend fun activate(
        scenario: Scenario,
        payload: TriggerPayload = TriggerPayload.EMPTY,
        trigger: Condition.InstantCondition? = null,
        firedAt: Long? = null,
    ): Activation? {
        val previous = activations.activation(scenario.id)
        val now = firedAt ?: clock.nowMillis()
        val activation = activationOf(scenario, now, previous, payload, trigger) ?: return null
        activations.activate(activation)
        return activation
    }

    suspend fun deactivate(scenarioId: String): Boolean = activations.deactivate(scenarioId)

    suspend fun pruneExpired(): Int = activations.pruneExpired(clock.nowMillis())

    suspend fun runForeground() {
        pruneExpired()
        haStates.refresh()
        activations.observeActivations()
            .map { running -> running.mapNotNull(Activation::expiresAt).minOrNull() }
            .distinctUntilChanged()

            .collectLatest { soonest ->
                if (soonest == null) return@collectLatest
                val wait = soonest - clock.nowMillis()
                if (wait > 0) delay(wait)

                pruneExpired()
            }
    }
}

private data class EngineInput(
    val scenarios: List<Scenario>,
    val activations: List<Activation>,
)
