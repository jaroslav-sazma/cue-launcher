package app.cue.launcher.data.widgets

import androidx.room.withTransaction
import app.cue.launcher.data.CueDatabase
import app.cue.launcher.data.scenario.LauncherWidgetType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@SingleIn(AppScope::class)
@Inject
class PanelColumnRepository(
    private val database: CueDatabase,
    private val hosted: HostedWidgetDao,
    private val launcherWidgets: PanelWidgetDao,
) {

    fun observeColumn(): Flow<List<PanelItem>> = combine(
        hosted.observeWidgets(WidgetPlacement.PANEL.storageKey),
        launcherWidgets.observeWidgets(),
    ) { rows, widgets ->
        panelColumnOf(
            hosted = rows.map(HostedWidgetEntity::toHostedWidget),
            launcherWidgets = widgets.mapNotNull(PanelWidgetEntity::toPanelItem),
        )
    }

    suspend fun nextPosition(): Int =
        maxOf(hosted.maxPosition(WidgetPlacement.PANEL.storageKey), launcherWidgets.maxPosition()) + 1

    suspend fun addLauncherWidget(
        widget: LauncherWidgetType,
        addedAt: Long = System.currentTimeMillis(),
    ) {
        launcherWidgets.upsert(
            PanelWidgetEntity(widget = widget.jsonName, position = nextPosition(), addedAt = addedAt),
        )
    }

    suspend fun removeLauncherWidget(id: Long): Boolean = launcherWidgets.deleteById(id) > 0

    suspend fun replaceLauncherWidgets(
        widgets: List<PanelLauncherWidget>,
        addedAt: Long = System.currentTimeMillis(),
    ) {
        database.withTransaction {
            launcherWidgets.deleteAll()
            widgets.forEach { entry ->
                launcherWidgets.upsert(
                    PanelWidgetEntity(
                        widget = entry.widget.jsonName,
                        position = entry.position,
                        addedAt = addedAt,
                    ),
                )
            }
        }
    }

    suspend fun move(key: PanelItemKey, delta: Int): Boolean {
        val order = column().map(PanelItem::key)
        val moved = movedWidgetOrder(order, key, delta) ?: return false
        database.withTransaction {
            moved.forEachIndexed { position, item ->
                when (item.kind) {
                    PanelItemKind.APP_WIDGET -> hosted.setPosition(item.id.toInt(), position)
                    PanelItemKind.LAUNCHER_WIDGET -> launcherWidgets.setPosition(item.id, position)
                }
            }
        }
        return true
    }

    suspend fun setHeightRows(appWidgetId: Int, heightRows: Int) {
        hosted.setHeightRows(appWidgetId, heightRows.coerceAtLeast(PANEL_WIDGET_ROWS_MIN))
    }

    suspend fun column(): List<PanelItem> = panelColumnOf(
        hosted = hosted.widgets(WidgetPlacement.PANEL.storageKey).map(HostedWidgetEntity::toHostedWidget),
        launcherWidgets = launcherWidgets.widgets().mapNotNull(PanelWidgetEntity::toPanelItem),
    )
}
