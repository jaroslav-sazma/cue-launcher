package app.cue.launcher.data.ha

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HaEventLogDao {

    @Query("SELECT * FROM ha_event_log ORDER BY receivedAt DESC, id DESC LIMIT :limit")
    fun observeLog(limit: Int): Flow<List<HaEventLogEntity>>

    @Insert
    suspend fun insert(row: HaEventLogEntity)

    @Query("DELETE FROM ha_event_log WHERE id NOT IN (SELECT id FROM ha_event_log ORDER BY id DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    // Rejected rows get a tighter cap of their own: a foreign sender spamming the receiver must not
    // be able to push the user's real events out of the log and hide its own tracks with them.
    @Query(
        "DELETE FROM ha_event_log WHERE matched < 0 AND id NOT IN " +
            "(SELECT id FROM ha_event_log WHERE matched < 0 ORDER BY id DESC LIMIT :keep)",
    )
    suspend fun pruneRejected(keep: Int)

    @Query("DELETE FROM ha_event_log")
    suspend fun clearLog()

    @Query("SELECT * FROM ha_code_seen")
    fun observeSeen(): Flow<List<HaCodeSeenEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeen(row: HaCodeSeenEntity)
}
