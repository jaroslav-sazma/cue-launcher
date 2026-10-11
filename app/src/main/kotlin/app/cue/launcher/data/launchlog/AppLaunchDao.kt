package app.cue.launcher.data.launchlog

import androidx.room.Dao
import androidx.room.Query
import app.cue.launcher.data.layout.StoredPackage
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLaunchDao {

    @Query("SELECT * FROM app_launch ORDER BY launchedAt DESC")
    fun observeLaunches(): Flow<List<AppLaunchEntity>>

    // The counter has to survive the upsert, so this cannot be @Insert(REPLACE).
    @Query(
        """
        INSERT INTO app_launch (packageName, activityClassName, userSerial, launchedAt, searchHits)
        VALUES (:packageName, :activityClassName, :userSerial, :launchedAt, :searchHit)
        ON CONFLICT(packageName, activityClassName, userSerial) DO UPDATE SET
            launchedAt = :launchedAt,
            searchHits = app_launch.searchHits + :searchHit
        """,
    )
    suspend fun record(
        packageName: String,
        activityClassName: String,
        userSerial: Long,
        launchedAt: Long,
        searchHit: Int,
    )

    @Query("DELETE FROM app_launch WHERE packageName = :packageName AND userSerial = :userSerial")
    suspend fun deleteByPackage(packageName: String, userSerial: Long): Int

    @Query("DELETE FROM app_launch WHERE userSerial = :userSerial")
    suspend fun deleteByProfile(userSerial: Long): Int

    @Query("SELECT DISTINCT userSerial FROM app_launch")
    suspend fun profiles(): List<Long>

    @Query("SELECT DISTINCT packageName, userSerial FROM app_launch")
    suspend fun packages(): List<StoredPackage>

    @Query("DELETE FROM app_launch")
    suspend fun deleteAll()
}
