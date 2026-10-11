package app.cue.launcher.data.widgets

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.cue.launcher.data.layout.ComponentRef

@Entity(tableName = "hosted_widget")
data class HostedWidgetEntity(
    @PrimaryKey val appWidgetId: Int,
    val providerPackage: String,
    val providerClass: String,

    val userSerial: Long,

    val boundAt: Long,

    @ColumnInfo(defaultValue = "0") val position: Int = 0,

    @ColumnInfo(defaultValue = "panel") val placement: String = WidgetPlacement.PANEL.storageKey,

    @ColumnInfo(defaultValue = "$PANEL_WIDGET_ROWS_DEFAULT")
    val heightRows: Int = PANEL_WIDGET_ROWS_DEFAULT,
)

data class HostedWidget(
    val appWidgetId: Int,
    val provider: ComponentRef,
    val boundAt: Long,
    val position: Int = 0,
    val placement: WidgetPlacement = WidgetPlacement.PANEL,

    val heightRows: Int = PANEL_WIDGET_ROWS_DEFAULT,
)

// Only the widget panel reads these; every other placement keeps the default and ignores it.
const val PANEL_WIDGET_ROWS_DEFAULT = 2
const val PANEL_WIDGET_ROWS_MIN = 1

fun HostedWidgetEntity.toHostedWidget(): HostedWidget = HostedWidget(
    appWidgetId = appWidgetId,
    provider = ComponentRef(
        packageName = providerPackage,
        className = providerClass,
        userSerial = userSerial,
    ),
    boundAt = boundAt,
    position = position,
    placement = WidgetPlacement.fromStorageKey(placement),
    heightRows = heightRows.coerceAtLeast(PANEL_WIDGET_ROWS_MIN),
)

fun HostedWidget.toEntity(): HostedWidgetEntity = HostedWidgetEntity(
    appWidgetId = appWidgetId,
    providerPackage = provider.packageName,
    providerClass = provider.className,
    userSerial = provider.userSerial,
    boundAt = boundAt,
    position = position,
    placement = placement.storageKey,
    heightRows = heightRows.coerceAtLeast(PANEL_WIDGET_ROWS_MIN),
)
