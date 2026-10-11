package app.cue.launcher.data.widgets

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PanelWidgetDao {

    @Query("SELECT * FROM panel_widget ORDER BY position, addedAt, id")
    fun observeWidgets(): Flow<List<PanelWidgetEntity>>

    @Query("SELECT * FROM panel_widget ORDER BY position, addedAt, id")
    suspend fun widgets(): List<PanelWidgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(widget: PanelWidgetEntity): Long

    @Query("DELETE FROM panel_widget WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM panel_widget")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(position), -1) FROM panel_widget")
    suspend fun maxPosition(): Int

    @Query("UPDATE panel_widget SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)
}
