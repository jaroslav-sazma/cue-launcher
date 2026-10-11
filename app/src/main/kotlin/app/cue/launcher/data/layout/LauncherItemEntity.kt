package app.cue.launcher.data.layout

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.cue.launcher.data.scenario.LauncherWidgetType

@Entity(
    tableName = "launcher_item",
    indices = [Index(value = ["container", "position"], unique = true)],
)
data class LauncherItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val container: String,
    val position: Int,
    val type: String,
    val packageName: String,
    val activityClassName: String,

    val userSerial: Long,

    val label: String? = null,

    val widgetType: String? = null,

    val appWidgetId: Int? = null,

    val columnSpan: Int? = null,
    val rowSpan: Int? = null,
)

enum class LauncherItemType(val storageKey: String) {
    SHORTCUT("shortcut"),
    FOLDER("folder"),
    LAUNCHER_WIDGET("launcher_widget"),
    APP_WIDGET("app_widget"),
    ;

    companion object {
        fun fromStorageKey(storageKey: String): LauncherItemType? =
            entries.firstOrNull { it.storageKey == storageKey }
    }
}

fun LauncherItemEntity.toLauncherItem(members: List<ComponentRef> = emptyList()): LauncherItem? {
    val layoutContainer = LayoutContainer.fromStorageKey(container) ?: return null
    return when (LauncherItemType.fromStorageKey(type)) {
        LauncherItemType.SHORTCUT -> LauncherItem.Shortcut(
            id = id,
            container = layoutContainer,
            position = position,
            component = ComponentRef(
                packageName = packageName,
                className = activityClassName,
                userSerial = userSerial,
            ),
        )

        LauncherItemType.FOLDER -> LauncherItem.Folder(
            id = id,
            container = layoutContainer,
            position = position,
            name = label.orEmpty(),
            items = members,
        )

        LauncherItemType.LAUNCHER_WIDGET -> widgetType
            ?.let(LauncherWidgetType::fromJsonName)
            ?.let { widget ->
                LauncherItem.LauncherWidget(
                    id = id,
                    container = layoutContainer,
                    position = position,
                    widget = widget,
                )
            }

        LauncherItemType.APP_WIDGET -> appWidgetId?.let { widgetId ->
            LauncherItem.AppWidget(
                id = id,
                container = layoutContainer,
                position = position,
                appWidgetId = widgetId,
                columnSpan = columnSpan ?: 1,
                rowSpan = rowSpan ?: 1,
            )
        }

        null -> null
    }
}

fun LauncherItem.toEntity(): LauncherItemEntity = when (this) {
    is LauncherItem.Shortcut -> LauncherItemEntity(
        id = id,
        container = container.storageKey,
        position = position,
        type = LauncherItemType.SHORTCUT.storageKey,
        packageName = component.packageName,
        activityClassName = component.className,
        userSerial = component.userSerial,
    )

    is LauncherItem.Folder -> LauncherItemEntity(
        id = id,
        container = container.storageKey,
        position = position,
        type = LauncherItemType.FOLDER.storageKey,
        packageName = "",
        activityClassName = "",
        userSerial = 0,
        label = name,
    )

    is LauncherItem.LauncherWidget -> LauncherItemEntity(
        id = id,
        container = container.storageKey,
        position = position,
        type = LauncherItemType.LAUNCHER_WIDGET.storageKey,
        packageName = "",
        activityClassName = "",
        userSerial = 0,
        widgetType = widget.jsonName,
    )

    is LauncherItem.AppWidget -> LauncherItemEntity(
        id = id,
        container = container.storageKey,
        position = position,
        type = LauncherItemType.APP_WIDGET.storageKey,
        packageName = "",
        activityClassName = "",
        userSerial = 0,
        appWidgetId = appWidgetId,
        columnSpan = columnSpan,
        rowSpan = rowSpan,
    )
}
