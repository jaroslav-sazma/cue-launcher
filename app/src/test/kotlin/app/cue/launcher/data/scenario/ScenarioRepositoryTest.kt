package app.cue.launcher.data.scenario

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScenarioRepositoryTest {

    private val dao = FakeScenarioDao()
    private val repository = ScenarioRepository(dao)

    private fun scenario(
        id: String,
        size: TileSize = TileSize.GRID_1X1,
        priority: Int = 0,
        position: Int? = null,
    ): Scenario = Scenario(
        id = id,
        name = id,
        priority = priority,
        pinnedPosition = position,
        size = size,
        content = TileContent.Template(TemplateLayout.ICON_TEXT),
    )

    private suspend fun pinOf(id: String): Int? = repository.scenario(id)?.pinnedPosition

    @Test
    fun `a position can be shared and saving one pin never removes another`() = runTest {
        repository.save(scenario("silence", priority = 900, position = 2))

        repository.save(scenario("sound", priority = 500, position = 2))

        assertEquals(2, pinOf("silence"))
        assertEquals(2, pinOf("sound"))
        assertEquals(900, repository.scenario("silence")?.priority)
    }

    @Test
    fun `a third scenario can join the same position`() = runTest {
        repository.save(scenario("a", position = 0))
        repository.save(scenario("b", position = 0))
        repository.save(scenario("c", position = 0))

        assertEquals(listOf(0, 0, 0), listOf(pinOf("a"), pinOf("b"), pinOf("c")))
    }

    @Test
    fun `saving a floating scenario leaves every pin alone`() = runTest {
        repository.save(scenario("gate", position = 2))

        repository.save(scenario("sound"))

        assertEquals(2, pinOf("gate"))
    }

    @Test
    fun `unpinning touches only the scenario that was saved`() = runTest {
        repository.save(scenario("silence", position = 2))
        repository.save(scenario("sound", position = 2))

        repository.save(scenario("sound", position = null))

        assertEquals(2, pinOf("silence"))
        assertEquals(null, pinOf("sound"))
    }

    @Test
    fun `import adds scenarios alongside without unpinning anybody`() = runTest {
        repository.save(scenario("gate", position = 2))

        repository.saveAll(listOf(scenario("imported", position = 2)))

        assertEquals(2, pinOf("gate"))
        assertEquals(2, pinOf("imported"))
    }

    @Test
    fun `a save reads nothing beyond the row it writes`() = runTest {
        repository.save(scenario("gate", position = 2))
        repository.save(scenario("sound", position = 2))

        assertEquals(0, dao.fullReads)
    }
}

private class FakeScenarioDao : ScenarioDao {

    private val rows = MutableStateFlow<Map<String, ScenarioEntity>>(emptyMap())

    var fullReads: Int = 0
        private set

    override fun observeScenarios(): Flow<List<ScenarioEntity>> = rows.map { it.values.toList() }

    override suspend fun scenarios(): List<ScenarioEntity> {
        fullReads++
        return rows.value.values.toList()
    }

    override suspend fun scenario(id: String): ScenarioEntity? = rows.value[id]

    override suspend fun ids(): List<String> = rows.value.keys.toList()

    override suspend fun count(): Int = rows.value.size

    override suspend fun upsert(scenario: ScenarioEntity) {
        rows.value = rows.value + (scenario.id to scenario)
    }

    override suspend fun upsertAll(scenarios: List<ScenarioEntity>) {
        rows.value = rows.value + scenarios.associateBy { it.id }
    }

    override suspend fun deleteById(id: String): Int {
        val existing = rows.value[id] ?: return 0
        rows.value = rows.value - existing.id
        return 1
    }
}
