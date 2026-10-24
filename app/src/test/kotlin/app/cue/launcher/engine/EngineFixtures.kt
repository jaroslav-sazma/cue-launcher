package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

internal fun scenario(
    id: String,
    priority: Int = 0,
    size: TileSize = TileSize.GRID_1X1,
    conditions: List<Condition> = listOf(Condition.Charging()),
    content: TileContent = contentFor(size),
): Scenario = Scenario(
    id = id,
    name = "Scénář $id",
    priority = priority,
    size = size,
    conditions = conditions,
    content = content,
)

internal fun fallback(
    id: String,
    priority: Int = 0,
    size: TileSize = TileSize.GRID_1X1,
): Scenario = scenario(id = id, priority = priority, size = size, conditions = emptyList())

internal fun contentFor(size: TileSize): TileContent = when (size) {
    TileSize.GRID_1X1 -> TileContent.Template(
        layout = TemplateLayout.ICON_TEXT,
        items = listOf(TemplateItem(actions = listOf(TapAction.App(packageName = "com.example")))),
    )

    TileSize.GRID_2X2 -> TileContent.Template(
        layout = TemplateLayout.TEXT_ONLY,
        text = "Text",
        items = listOf(TemplateItem(actions = listOf(TapAction.App(packageName = "com.example")))),
    )

    TileSize.HERO_4X2, TileSize.OVERLAY -> TileContent.LauncherWidget(LauncherWidgetType.CALENDAR)
}

internal class FakeScenarioSource : ScenarioSource {

    private val rows = MutableStateFlow<List<Scenario>>(emptyList())

    var reads: Int = 0
        private set

    override fun observeScenarios(): Flow<List<Scenario>> {
        reads++
        return rows
    }

    fun emit(vararg scenarios: Scenario) {
        rows.value = scenarios.toList()
    }

    fun scenario(id: String): Scenario = rows.value.first { it.id == id }
}

internal class FakeActivationStore : ActivationStore {

    private val rows = MutableStateFlow<Map<String, Activation>>(emptyMap())

    private val revision = MutableStateFlow(0)

    override fun observeActivations(): Flow<List<Activation>> =
        combine(rows, revision) { current, _ -> current.values.sortedByDescending(Activation::since) }

    // Runs once, right after the next read: a write that lands between a snapshot and what is done
    // with it.
    var afterNextRead: (() -> Unit)? = null

    override suspend fun activations(): List<Activation> = rows().also {
        afterNextRead?.let { write ->
            afterNextRead = null
            write()
        }
    }

    override suspend fun activation(scenarioId: String): Activation? = rows.value[scenarioId]

    override suspend fun activate(activation: Activation) {
        rows.update { it + (activation.scenarioId to activation) }
    }

    override suspend fun deactivate(scenarioId: String): Boolean {
        val existing = rows.value[scenarioId] ?: return false
        rows.update { it - existing.scenarioId }
        return true
    }

    override suspend fun deactivateIfUnchanged(activation: Activation): Boolean {
        if (rows.value[activation.scenarioId] != activation) return false
        rows.update { it - activation.scenarioId }
        return true
    }

    override suspend fun pruneExpired(now: Long): Int {
        val expired = rows.value.values.filter { it.isExpired(now) }
        if (expired.isEmpty()) return 0
        rows.update { current -> current - expired.map(Activation::scenarioId).toSet() }
        return expired.size
    }

    fun rows(): List<Activation> = rows.value.values.toList()

    fun put(activation: Activation) {
        rows.update { it + (activation.scenarioId to activation) }
    }

    fun reemit() {
        revision.update { it + 1 }
    }
}

internal class FakeHaEventLog : HaEventLog {

    val recorded = mutableListOf<Pair<HaEvent, Int>>()

    override suspend fun record(event: HaEvent, matched: Int) {
        recorded += event to matched
    }
}

internal class FakeHaStateSource(initial: Map<String, String> = emptyMap()) : HaStateSource {

    private val values = MutableStateFlow(initial)

    var requested: Set<String>? = null
        private set

    var refreshes: Int = 0
        private set

    var nudges: Int = 0
        private set

    override fun observeStates(entities: Set<String>): Flow<Map<String, String>> {
        requested = entities
        if (entities.isEmpty()) return flowOf(emptyMap())
        return values.map { states -> states.filterKeys { it in entities } }
    }

    override fun observeGate(entities: Set<String>): Flow<Map<String, String>> = observeStates(entities)

    override fun observeGateForEvent(entities: Set<String>): Flow<Map<String, String>> = observeStates(entities)

    override fun refresh() {
        refreshes++
    }

    override fun nudge() {
        nudges++
    }

    fun emit(vararg states: Pair<String, String>) {
        values.value = states.toMap()
    }
}

internal class FakeSystemStateSource : SystemStateSource {

    private val state = MutableStateFlow(SystemState())

    var measurements: Int = 0
        private set

    override fun observeState(): Flow<SystemState> = flow {
        measurements++
        emitAll(state)
    }

    val asked = mutableListOf<Set<StateAspect>>()

    override fun observeState(aspects: Set<StateAspect>): Flow<SystemState> {
        asked += aspects
        return observeState()
    }

    val needed = mutableListOf<StateNeeds>()

    override suspend fun measure(needs: StateNeeds): SystemState {
        needed += needs
        return super.measure(needs)
    }

    fun emit(snapshot: SystemState) {
        state.value = snapshot
    }
}
