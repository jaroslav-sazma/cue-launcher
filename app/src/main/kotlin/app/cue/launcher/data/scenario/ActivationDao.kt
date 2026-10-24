package app.cue.launcher.data.scenario

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivationDao {

    @Query("SELECT * FROM scenario_activation ORDER BY since DESC")
    fun observeActivations(): Flow<List<ActivationEntity>>

    @Query("SELECT * FROM scenario_activation ORDER BY since DESC")
    suspend fun activations(): List<ActivationEntity>

    @Query("SELECT * FROM scenario_activation WHERE scenarioId = :scenarioId")
    suspend fun activation(scenarioId: String): ActivationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(activation: ActivationEntity)

    @Query("DELETE FROM scenario_activation WHERE scenarioId = :scenarioId")
    suspend fun deleteById(scenarioId: String): Int

    // Compared decoded rather than in SQL: the same payload can be stored as more than one text.
    @Transaction
    suspend fun deleteIfUnchanged(expected: Activation): Boolean {
        val row = activation(expected.scenarioId) ?: return false
        return row.toActivation() == expected && deleteById(expected.scenarioId) > 0
    }

    @Query("DELETE FROM scenario_activation WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun deleteExpired(now: Long): Int
}
