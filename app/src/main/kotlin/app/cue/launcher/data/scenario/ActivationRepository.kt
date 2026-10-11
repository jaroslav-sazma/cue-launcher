package app.cue.launcher.data.scenario

import app.cue.launcher.engine.ActivationStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
@Inject
class ActivationRepository(private val dao: ActivationDao) : ActivationStore {

    override fun observeActivations(): Flow<List<Activation>> =
        dao.observeActivations().map { rows -> rows.map(ActivationEntity::toActivation) }

    override suspend fun activations(): List<Activation> =
        dao.activations().map(ActivationEntity::toActivation)

    override suspend fun activation(scenarioId: String): Activation? =
        dao.activation(scenarioId)?.toActivation()

    override suspend fun activate(activation: Activation) = dao.upsert(activation.toEntity())

    override suspend fun deactivate(scenarioId: String): Boolean = dao.deleteById(scenarioId) > 0

    override suspend fun pruneExpired(now: Long): Int = dao.deleteExpired(now)
}
