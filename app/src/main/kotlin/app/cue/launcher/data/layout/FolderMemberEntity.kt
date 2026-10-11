package app.cue.launcher.data.layout

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folder_member",
    indices = [Index(value = ["folderId", "position"], unique = true)],
)
data class FolderMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val position: Int,
    val packageName: String,
    val activityClassName: String,

    val userSerial: Long,
)

fun FolderMemberEntity.toComponentRef(): ComponentRef = ComponentRef(
    packageName = packageName,
    className = activityClassName,
    userSerial = userSerial,
)

fun folderMemberEntities(folderId: Long, items: List<ComponentRef>): List<FolderMemberEntity> =
    items.mapIndexed { position, component ->
        FolderMemberEntity(
            folderId = folderId,
            position = position,
            packageName = component.packageName,
            activityClassName = component.className,
            userSerial = component.userSerial,
        )
    }
