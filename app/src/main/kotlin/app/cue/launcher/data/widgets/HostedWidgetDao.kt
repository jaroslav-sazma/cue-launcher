package app.cue.launcher.data.widgets

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HostedWidgetDao {

    @Query("SELECT * FROM hosted_widget WHERE placement = :placement ORDER BY position, boundAt, appWidgetId")
    fun observeWidgets(placement: String): Flow<List<HostedWidgetEntity>>

    @Query("SELECT * FROM hosted_widget ORDER BY position, boundAt, appWidgetId")
    suspend fun widgets(): List<HostedWidgetEntity>

    @Query("SELECT * FROM hosted_widget WHERE placement = :placement ORDER BY position, boundAt, appWidgetId")
    suspend fun widgets(placement: String): List<HostedWidgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(widget: HostedWidgetEntity)

    @Query("DELETE FROM hosted_widget WHERE appWidgetId IN (:appWidgetIds)")
    suspend fun deleteByIds(appWidgetIds: List<Int>): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM hosted_widget WHERE placement = :placement")
    suspend fun maxPosition(placement: String): Int

    @Query("UPDATE hosted_widget SET position = :position WHERE appWidgetId = :appWidgetId")
    suspend fun setPosition(appWidgetId: Int, position: Int)

    @Query("UPDATE hosted_widget SET heightRows = :heightRows WHERE appWidgetId = :appWidgetId")
    suspend fun setHeightRows(appWidgetId: Int, heightRows: Int)
}
