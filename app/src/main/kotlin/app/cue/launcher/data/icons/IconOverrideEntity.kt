package app.cue.launcher.data.icons

import androidx.room.Entity
import app.cue.launcher.data.layout.ComponentRef

@Entity(
    tableName = "icon_override",
    primaryKeys = ["packageName", "activityClassName", "userSerial"],
)
data class IconOverrideEntity(
    val packageName: String,
    val activityClassName: String,
    val userSerial: Long,
)

fun IconOverrideEntity.toComponentRef(): ComponentRef = ComponentRef(
    packageName = packageName,
    className = activityClassName,
    userSerial = userSerial,
)

fun ComponentRef.toIconOverrideEntity(): IconOverrideEntity = IconOverrideEntity(
    packageName = packageName,
    activityClassName = className,
    userSerial = userSerial,
)
