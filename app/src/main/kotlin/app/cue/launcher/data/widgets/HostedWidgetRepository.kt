package app.cue.launcher.data.widgets

import androidx.room.withTransaction
import app.cue.launcher.data.CueDatabase
import app.cue.launcher.data.layout.ComponentRef
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@Inject
class HostedWidgetRepository(
    private val database: CueDatabase,
    private val dao: HostedWidgetDao,

    private val panel: PanelWidgetDao,
) {

    suspend fun widgets(): List<HostedWidget> = dao.widgets().map(HostedWidgetEntity::toHostedWidget)

    suspend fun knownIds(): Set<Int> = dao.widgets().mapTo(mutableSetOf()) { it.appWidgetId }

    suspend fun scenarioIds(): Set<Int> =
        dao.widgets(WidgetPlacement.SCENARIO.storageKey).mapTo(mutableSetOf()) { it.appWidgetId }

    suspend fun remember(
        appWidgetId: Int,
        provider: ComponentRef,
        placement: WidgetPlacement = WidgetPlacement.PANEL,
        boundAt: Long = System.currentTimeMillis(),
    ) {
        val position = when (placement) {
            WidgetPlacement.PANEL -> maxOf(dao.maxPosition(placement.storageKey), panel.maxPosition()) + 1
            WidgetPlacement.SCENARIO, WidgetPlacement.HOME -> 0
        }
        dao.upsert(HostedWidget(appWidgetId, provider, boundAt, position, placement).toEntity())
    }

    suspend fun forget(appWidgetIds: List<Int>): Int =
        if (appWidgetIds.isEmpty()) 0 else dao.deleteByIds(appWidgetIds)

    suspend fun replacePlaced(widgets: List<HostedWidget>): List<Int> = database.withTransaction {
        val removed = placedIds().toList()
        forget(removed)
        widgets.forEach { widget -> dao.upsert(widget.toEntity()) }
        removed
    }

    private suspend fun placedIds(): Set<Int> = dao.widgets()
        .filter { WidgetPlacement.fromStorageKey(it.placement) != WidgetPlacement.SCENARIO }
        .mapTo(mutableSetOf(), HostedWidgetEntity::appWidgetId)
}
