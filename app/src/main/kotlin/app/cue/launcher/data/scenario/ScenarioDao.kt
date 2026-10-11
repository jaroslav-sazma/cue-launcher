package app.cue.launcher.data.scenario

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScenarioDao {

    @Query("SELECT * FROM scenario ORDER BY priority DESC, name, id")
    fun observeScenarios(): Flow<List<ScenarioEntity>>

    @Query("SELECT * FROM scenario ORDER BY priority DESC, name, id")
    suspend fun scenarios(): List<ScenarioEntity>

    @Query("SELECT * FROM scenario WHERE id = :id")
    suspend fun scenario(id: String): ScenarioEntity?

    @Query("SELECT id FROM scenario")
    suspend fun ids(): List<String>

    @Query("SELECT COUNT(*) FROM scenario")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(scenario: ScenarioEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(scenarios: List<ScenarioEntity>)

    @Query("DELETE FROM scenario WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
