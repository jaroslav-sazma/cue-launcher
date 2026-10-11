package app.cue.launcher.data.ha

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ha_event_log")
data class HaEventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receivedAt: Long,
    val code: String,

    val op: String,
    val payload: String,

    val matched: Int,

    // The contract version the sending blueprint stamped on the event. Zero means it came from a copy
    // written before the stamp existed.
    @ColumnInfo(defaultValue = "0") val version: Int = 0,
)

@Entity(tableName = "ha_code_seen")
data class HaCodeSeenEntity(
    @PrimaryKey val code: String,
    val lastSeenAt: Long,
)

const val HA_LOG_LIMIT = 100

const val HA_REJECTED_LOG_LIMIT = 10

const val HA_LOG_REJECTED = -1
