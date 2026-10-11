package app.cue.launcher.data.icons

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.cue.launcher.data.layout.StoredPackage
import kotlinx.coroutines.flow.Flow

@Dao
interface IconOverrideDao {

    @Query("SELECT * FROM icon_override")
    fun observeOverrides(): Flow<List<IconOverrideEntity>>

    @Query("SELECT * FROM icon_override")
    suspend fun overrides(): List<IconOverrideEntity>

    @Transaction
    suspend fun replaceAll(overrides: List<IconOverrideEntity>) {
        deleteAll()
        overrides.forEach { entity -> insert(entity) }
    }

    @Query("DELETE FROM icon_override")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(override: IconOverrideEntity)

    @Query(
        "DELETE FROM icon_override WHERE packageName = :packageName " +
            "AND activityClassName = :className AND userSerial = :userSerial",
    )
    suspend fun delete(packageName: String, className: String, userSerial: Long)

    @Query("DELETE FROM icon_override WHERE packageName = :packageName AND userSerial = :userSerial")
    suspend fun deleteByPackage(packageName: String, userSerial: Long): Int

    @Query("DELETE FROM icon_override WHERE userSerial = :userSerial")
    suspend fun deleteByProfile(userSerial: Long): Int

    @Query("SELECT DISTINCT userSerial FROM icon_override")
    suspend fun profiles(): List<Long>

    @Query("SELECT DISTINCT packageName, userSerial FROM icon_override")
    suspend fun packages(): List<StoredPackage>
}
