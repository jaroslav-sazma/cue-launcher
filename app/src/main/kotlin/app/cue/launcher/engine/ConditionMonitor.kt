package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TriggerPayload
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Suppress("LongParameterList", "TooManyFunctions")
@SingleIn(AppScope::class)
@Inject
class ConditionMonitor(
    private val scenarios: ScenarioSource,
    private val activations: ActivationStore,
    private val systemState: SystemStateSource,
    private val localClock: LocalClock,
    private val engine: ScenarioEngine,
    private val haEventLog: HaEventLog,
    private val clock: WallClock,
) {

    private val notifications = Mutex()

    // Only the signals an enabled scenario reads are followed; with nothing gated by Wi-Fi, no network
    // callback is registered even on screen.
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun runForeground() = coroutineScope {
        val definitions = scenarios.observeScenarios()
            .shareIn(this, SharingStarted.WhileSubscribed(), replay = 1)
        val state = definitions
            .map { stateAspectsOf(it.filter(Scenario::enabled)) }
            .distinctUntilChanged()
            .flatMapLatest { systemState.observeState(it) }
        combine(definitions, state, ::Evaluation)
            .collectLatest { evaluation ->
                apply(evaluation.scenarios, evaluation.state)
                awaitTimeInstants(evaluation.scenarios, evaluation.state)
            }
    }

    suspend fun apply(scenarios: List<Scenario>, state: SystemState): ReevaluationPlan {
        val running = activations.activations()
        val plan = reevaluationPlan(scenarios, running, state)
        plan.deactivate.forEach { engine.deactivate(it) }

        // A state taking over from an event keeps what the event brought — the values on the tile, a
        // remembered place — while activating without a trigger drops the notification key and the
        // end event: the state holds the tile now, so the notification going away or the end event
        // arriving must no longer end it.
        plan.activate.forEach { scenario ->
            val previous = running.firstOrNull { it.scenarioId == scenario.id }?.payload
            engine.activate(scenario, payload = previous ?: TriggerPayload.EMPTY)
        }
        return plan
    }

    // Ends go before fires everywhere, so an end event that is also the scenario's own trigger
    // restarts it rather than ending it.
    suspend fun onBluetoothDisconnected(mac: String) {
        val definitions = scenarios.observeScenarios().first()
        endedByBluetoothDisconnect(activations.activations(), mac).forEach { engine.deactivateIfUnchanged(it) }
        val listeners = bluetoothDisconnectListeners(definitions, mac)
        if (listeners.isEmpty()) return
        val state = stateFor(listeners)
        dueBluetoothDisconnects(definitions, state, mac).forEach { scenario ->
            engine.activate(scenario, trigger = activationTrigger(scenario, state, btDisconnectTrigger(scenario, mac)))
        }
    }

    // An end event matches the code whatever the op, as it only says the event arrived.
    suspend fun onHaEvent(event: HaEvent) {
        val definitions = scenarios.observeScenarios().first()
        val listeners = haEventListeners(definitions, event.code)
        val ended = endedByHaEvent(activations.activations(), event.code)

        haEventLog.record(event, haEventWaiters(definitions, listeners, ended.map(Activation::scenarioId), event.code))
        ended.forEach { engine.deactivateIfUnchanged(it) }
        if (listeners.isEmpty()) return
        if (event.op == HaEventOp.END) {
            listeners.forEach { engine.deactivate(it.id) }
            return
        }
        val state = stateFor(listeners)
        val payload = haPayload(event)
        dueHaEvents(definitions, state, event.code).forEach { scenario ->
            engine.activate(scenario, payload, activationTrigger(scenario, state, haEventTrigger(scenario, event.code)))
        }
    }

    // known: the scenarios the listener already holds, so a post does not read and parse them all
    // from the database again.
    suspend fun onNotificationPosted(notification: PostedNotification, known: List<Scenario>? = null) {
        notifications.withLock {
            val definitions = known ?: scenarios.observeScenarios().first()
            endedByNotification(activations.activations(), notification).forEach { engine.deactivate(it.scenarioId) }
            val listeners = notificationListeners(definitions, notification)
            if (listeners.isEmpty()) return@withLock
            val state = stateFor(listeners)
            dueNotifications(definitions, state, notification).forEach { scenario ->
                val trigger = notificationTrigger(scenario, notification)
                val payload = notificationPayload(
                    notification,
                    trigger?.extract.orEmpty(),
                    dismissible = trigger?.until == null,
                )
                engine.activate(scenario, payload, activationTrigger(scenario, state, trigger))
            }
        }
    }

    suspend fun onNotificationRemoved(
        removed: PostedNotification,
        active: List<PostedNotification>,
        known: List<Scenario>? = null,
    ) = notifications.withLock {
        val definitions = known ?: scenarios.observeScenarios().first()
        val ending = notificationEndListeners(definitions, removed, active)
        if (ending.isEmpty()) return@withLock
        heldByNotification(activations.activations(), ending).forEach { engine.deactivate(it) }
    }

    // endedAt is when the listener heard of it, not when the queue got to it, so a wait in the
    // queue cannot stretch a blink past the condition's minimum.
    suspend fun onNotificationEnded(ended: PostedNotification, endedAt: Long, known: List<Scenario>? = null) =
        notifications.withLock {
            val definitions = known ?: scenarios.observeScenarios().first()
            val listeners = notificationEndedListeners(definitions, ended, endedAt)
            if (listeners.isEmpty()) return@withLock
            val state = stateFor(listeners)
            dueNotificationEnds(definitions, state, ended, endedAt).forEach { scenario ->
                val trigger = notificationEndedTrigger(scenario, ended, endedAt)
                engine.activate(scenario, notificationEndedPayload(ended), activationTrigger(scenario, state, trigger))
            }
        }

    suspend fun awaitsPlace(): Boolean = placeSavedListeners(scenarios.observeScenarios().first()).isNotEmpty()

    // False when no scenario took the place, its gate shut: the tile that asked for it then stays,
    // so the place is not lost along with the prompt.
    suspend fun onPlaceSaved(place: SavedPlace): Boolean {
        val definitions = scenarios.observeScenarios().first()
        val state = stateFor(placeSavedListeners(definitions))
        val due = duePlaceSaves(definitions, state)
        if (due.isEmpty()) return false
        due.forEach { scenario ->
            val trigger = activationTrigger(scenario, state, placeSavedTrigger(scenario))
            engine.activate(scenario, placePayload(place), trigger)
        }
        answeredPlacePrompts(definitions, activations.activations()).forEach { engine.deactivate(it) }
        return true
    }

    // The snapshot is read under the same lock the live callbacks take, so a post or a removal that
    // lands while the replay runs is applied after it rather than overwritten by an older picture.
    suspend fun reconcileNotifications(since: Long, read: () -> NotificationSnapshot?) = notifications.withLock {
        val definitions = scenarios.observeScenarios().first()
        val running = activations.activations()
        val listening = definitions.filter { it.enabled && it.notificationConditions.isNotEmpty() }
        if (listening.isEmpty() && running.none { it.notificationEnd != null }) return@withLock
        val snapshot = read() ?: return@withLock
        val candidates = snapshot.active.filter { it.postTime > since }
        val firing = listening.filter { scenario -> candidates.any { notificationTrigger(scenario, it) != null } }
        val state = stateFor(firing)
        val plan = notificationReconciliation(
            scenarios = definitions,
            activations = running,
            state = state,
            replay = NotificationReplay(snapshot.active, since, clock.nowMillis(), clock.zone()),
        )
        plan.end.forEach { engine.deactivate(it) }
        plan.fire.forEach { fired ->
            val notification = snapshot.enrich(fired.notification)
            val trigger = notificationTrigger(fired.scenario, notification)
            val payload = notificationPayload(
                notification,
                trigger?.extract.orEmpty(),
                dismissible = trigger?.until == null,
            )
            engine.activate(
                scenario = fired.scenario,
                payload = payload,
                trigger = activationTrigger(fired.scenario, state, trigger),
                firedAt = notification.postTime,
            )
        }
    }

    private suspend fun stateFor(listeners: List<Scenario>): SystemState {
        val needs = stateNeedsOf(listeners)
        return if (needs.aspects.isEmpty()) SystemState() else systemState.measure(needs)
    }

    private suspend fun awaitTimeInstants(scenarios: List<Scenario>, state: SystemState) {
        val instants = timeInstantsOf(scenarios)
        if (instants.isEmpty()) return
        while (true) {

            val reading = localClock.reading()
            fireDueInstants(scenarios, state.mergedWith(SystemState(clock = reading.clock)))
            val wait = timeInstantWaitMillis(instants, reading) ?: return
            delay(wait)
        }
    }

    private suspend fun fireDueInstants(scenarios: List<Scenario>, state: SystemState) {
        dueTimeInstants(scenarios, activations.activations(), state, clock.nowMillis()).forEach { scenario ->
            val trigger = timeInstantTrigger(scenario, state.clock)
            engine.activate(scenario, trigger = activationTrigger(scenario, state, trigger))
        }
    }
}

private data class Evaluation(
    val scenarios: List<Scenario>,
    val state: SystemState,
)
