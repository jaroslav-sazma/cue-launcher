package app.cue.launcher.data.launchlog

import androidx.room.ColumnInfo
import androidx.room.Entity
import app.cue.launcher.data.layout.ComponentRef

@Entity(
    tableName = "app_launch",
    primaryKeys = ["packageName", "activityClassName", "userSerial"],
)
data class AppLaunchEntity(
    val packageName: String,
    val activityClassName: String,
    val userSerial: Long,

    val launchedAt: Long,

    @ColumnInfo(defaultValue = "0")
    val searchHits: Int = 0,
)

data class LaunchStats(val lastLaunchedAt: Long, val searchHits: Int)

fun AppLaunchEntity.toComponentRef(): ComponentRef = ComponentRef(
    packageName = packageName,
    className = activityClassName,
    userSerial = userSerial,
)

fun launchStatsOf(rows: List<AppLaunchEntity>): Map<ComponentRef, LaunchStats> =
    rows.associate { it.toComponentRef() to LaunchStats(it.launchedAt, it.searchHits) }
