package app.cue.launcher.data.layout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LauncherLayoutDao {

    @Query("SELECT * FROM launcher_item ORDER BY container, position")
    fun observeItems(): Flow<List<LauncherItemEntity>>

    @Query("SELECT * FROM launcher_item ORDER BY container, position")
    suspend fun items(): List<LauncherItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LauncherItemEntity): Long

    @Query("SELECT * FROM launcher_item WHERE container = :container ORDER BY position")
    suspend fun itemsIn(container: String): List<LauncherItemEntity>

    @Query("DELETE FROM launcher_item WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "DELETE FROM launcher_item WHERE type = 'shortcut' " +
            "AND packageName = :packageName AND userSerial = :userSerial",
    )
    suspend fun deleteByPackage(packageName: String, userSerial: Long): Int

    @Query("SELECT DISTINCT userSerial FROM launcher_item WHERE type = 'shortcut'")
    suspend fun profiles(): List<Long>

    @Query("DELETE FROM launcher_item WHERE type = 'shortcut' AND userSerial = :userSerial")
    suspend fun deleteByProfile(userSerial: Long): Int

    @Query("SELECT COUNT(*) FROM launcher_item")
    suspend fun count(): Int

    @Query("DELETE FROM launcher_item")
    suspend fun deleteAll()
}
