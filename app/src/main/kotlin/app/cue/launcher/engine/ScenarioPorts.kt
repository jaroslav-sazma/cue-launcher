package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Scenario
import kotlinx.coroutines.flow.Flow

interface ScenarioSource {

    fun observeScenarios(): Flow<List<Scenario>>
}

interface AlarmMemory {

    suspend fun rememberedAlarm(): Long?

    suspend fun rememberAlarm(upcoming: Long?)
}

interface ActivationStore {

    fun observeActivations(): Flow<List<Activation>>

    suspend fun activations(): List<Activation>

    suspend fun activation(scenarioId: String): Activation?

    suspend fun activate(activation: Activation)

    suspend fun deactivate(scenarioId: String): Boolean

    suspend fun pruneExpired(now: Long): Int
}
