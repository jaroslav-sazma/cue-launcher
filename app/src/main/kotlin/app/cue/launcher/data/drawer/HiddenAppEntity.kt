package app.cue.launcher.data.drawer

import androidx.room.Entity
import app.cue.launcher.data.layout.ComponentRef

@Entity(
    tableName = "hidden_app",
    primaryKeys = ["packageName", "activityClassName", "userSerial"],
)
data class HiddenAppEntity(
    val packageName: String,
    val activityClassName: String,
    val userSerial: Long,
)

fun HiddenAppEntity.toComponentRef(): ComponentRef = ComponentRef(
    packageName = packageName,
    className = activityClassName,
    userSerial = userSerial,
)

fun ComponentRef.toHiddenAppEntity(): HiddenAppEntity = HiddenAppEntity(
    packageName = packageName,
    activityClassName = className,
    userSerial = userSerial,
)
