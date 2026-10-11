package app.cue.launcher.data.widgets

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "panel_widget")
data class PanelWidgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val widget: String,
    val position: Int,

    val addedAt: Long,
)
