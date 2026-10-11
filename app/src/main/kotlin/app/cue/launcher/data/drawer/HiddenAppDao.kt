package app.cue.launcher.data.drawer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.cue.launcher.data.layout.StoredPackage
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenAppDao {

    @Query("SELECT * FROM hidden_app")
    fun observeHidden(): Flow<List<HiddenAppEntity>>

    @Query("SELECT * FROM hidden_app")
    suspend fun hidden(): List<HiddenAppEntity>

    @Transaction
    suspend fun replaceAll(hidden: List<HiddenAppEntity>) {
        deleteAll()
        hidden.forEach { entity -> insert(entity) }
    }

    @Query("DELETE FROM hidden_app")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(hidden: HiddenAppEntity)

    @Query(
        "DELETE FROM hidden_app WHERE packageName = :packageName " +
            "AND activityClassName = :className AND userSerial = :userSerial",
    )
    suspend fun delete(packageName: String, className: String, userSerial: Long)

    @Query("DELETE FROM hidden_app WHERE packageName = :packageName AND userSerial = :userSerial")
    suspend fun deleteByPackage(packageName: String, userSerial: Long): Int

    @Query("DELETE FROM hidden_app WHERE userSerial = :userSerial")
    suspend fun deleteByProfile(userSerial: Long): Int

    @Query("SELECT DISTINCT userSerial FROM hidden_app")
    suspend fun profiles(): List<Long>

    @Query("SELECT DISTINCT packageName, userSerial FROM hidden_app")
    suspend fun packages(): List<StoredPackage>
}
