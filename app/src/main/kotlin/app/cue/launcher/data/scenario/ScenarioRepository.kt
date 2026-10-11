package app.cue.launcher.data.scenario

import app.cue.launcher.engine.ScenarioSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
@Inject
class ScenarioRepository(private val dao: ScenarioDao) : ScenarioSource {

    override fun observeScenarios(): Flow<List<Scenario>> =
        dao.observeScenarios().map { rows -> rows.mapNotNull(ScenarioEntity::toScenario) }

    suspend fun scenarios(): List<Scenario> = dao.scenarios().mapNotNull(ScenarioEntity::toScenario)

    suspend fun scenario(id: String): Scenario? = dao.scenario(id)?.toScenario()

    suspend fun isEmpty(): Boolean = dao.count() == 0

    // A pinned position is shared, never owned: saving one never touches anybody else's pin. Which
    // of the scenarios on a position is drawn is decided when the bento is laid out.
    suspend fun save(scenario: Scenario) = dao.upsert(scenario.toEntity())

    suspend fun saveAll(scenarios: List<Scenario>) = dao.upsertAll(scenarios.map(Scenario::toEntity))

    suspend fun delete(id: String): Boolean = dao.deleteById(id) > 0

    suspend fun setEnabled(id: String, enabled: Boolean): Boolean {
        val scenario = scenario(id) ?: return false
        save(scenario.copy(enabled = enabled))
        return true
    }

    suspend fun setPriority(id: String, priority: Int): Boolean {
        val scenario = scenario(id) ?: return false
        save(scenario.copy(priority = priority))
        return true
    }

    suspend fun knownIds(): Set<String> = dao.ids().toSet()
}
