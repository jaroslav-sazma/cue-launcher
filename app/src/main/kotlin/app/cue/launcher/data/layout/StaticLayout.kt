package app.cue.launcher.data.layout

import app.cue.launcher.data.scenario.LauncherWidgetType
import kotlinx.serialization.Serializable

private const val GRID_COLUMNS = 4
private const val GRID_ROWS = 3
private const val DOCK_ROWS = 1

enum class LayoutContainer(val storageKey: String, val columns: Int, val rows: Int) {
    GRID(storageKey = "grid", columns = GRID_COLUMNS, rows = GRID_ROWS),
    DOCK(storageKey = "dock", columns = GRID_COLUMNS, rows = DOCK_ROWS),
    PANEL_DOCK(storageKey = "panel_dock", columns = GRID_COLUMNS, rows = DOCK_ROWS),
    ;

    val slotCount: Int get() = columns * rows

    fun isValidPosition(position: Int): Boolean = position in 0 until slotCount

    fun fitsArea(position: Int, columnSpan: Int, rowSpan: Int = 1): Boolean =
        columnSpan >= 1 && rowSpan >= 1 && isValidPosition(position) &&
            columnOf(position) + columnSpan <= columns &&
            rowOf(position) + rowSpan <= rows

    fun rowOf(position: Int): Int = position / columns

    fun columnOf(position: Int): Int = position % columns

    fun positionOf(row: Int, column: Int): Int = row * columns + column

    companion object {
        fun fromStorageKey(storageKey: String): LayoutContainer? =
            entries.firstOrNull { it.storageKey == storageKey }

        val home: List<LayoutContainer> = listOf(GRID, DOCK)
    }
}

@Serializable
data class ComponentRef(val packageName: String, val className: String, val userSerial: Long)

sealed interface LauncherItem {
    val id: Long
    val container: LayoutContainer
    val position: Int

    val columnSpan: Int get() = 1

    val rowSpan: Int get() = 1

    data class Shortcut(
        override val id: Long,
        override val container: LayoutContainer,
        override val position: Int,
        val component: ComponentRef,
    ) : LauncherItem

    data class LauncherWidget(
        override val id: Long,
        override val container: LayoutContainer,
        override val position: Int,
        val widget: LauncherWidgetType,
    ) : LauncherItem {
        override val columnSpan: Int get() = widget.staticColumns

        override val rowSpan: Int get() = widget.staticRows
    }

    data class AppWidget(
        override val id: Long,
        override val container: LayoutContainer,
        override val position: Int,
        val appWidgetId: Int,
        override val columnSpan: Int,
        override val rowSpan: Int,
    ) : LauncherItem

    data class Folder(
        override val id: Long,
        override val container: LayoutContainer,
        override val position: Int,
        val name: String,
        val items: List<ComponentRef>,
    ) : LauncherItem
}

fun LauncherItem.withId(id: Long): LauncherItem = when (this) {
    is LauncherItem.Shortcut -> copy(id = id)
    is LauncherItem.Folder -> copy(id = id)
    is LauncherItem.LauncherWidget -> copy(id = id)
    is LauncherItem.AppWidget -> copy(id = id)
}

val LauncherItem.coveredPositions: List<Int>
    get() = areaPositions(container, position, columnSpan, rowSpan)

fun areaPositions(
    container: LayoutContainer,
    position: Int,
    columnSpan: Int,
    rowSpan: Int,
): List<Int> = (0 until rowSpan).flatMap { row ->
    (0 until columnSpan).map { column -> position + row * container.columns + column }
}

data class StaticLayout(private val slots: Map<LayoutContainer, List<LauncherItem?>>) {

    fun slots(container: LayoutContainer): List<LauncherItem?> =
        slots[container] ?: List(container.slotCount) { null }

    fun itemAt(container: LayoutContainer, position: Int): LauncherItem? =
        slots(container).getOrNull(position)

    fun allItems(): List<LauncherItem> =
        LayoutContainer.entries.flatMap { container -> slots(container).filterNotNull().distinct() }

    fun isEmpty(): Boolean = slots.values.all { row -> row.all { it == null } }

    companion object {
        val EMPTY: StaticLayout = staticLayoutOf(emptyList())
    }
}

fun staticLayoutOf(items: List<LauncherItem>): StaticLayout {
    val slots = LayoutContainer.entries.associateWith { container ->
        arrayOfNulls<LauncherItem>(container.slotCount)
    }
    for (item in items.filter { it.container.fitsArea(it.position, it.columnSpan, it.rowSpan) }) {
        val containerSlots = slots.getValue(item.container)
        if (item.coveredPositions.none { containerSlots[it] != null }) {
            item.coveredPositions.forEach { position -> containerSlots[position] = item }
        }
    }
    return StaticLayout(slots.mapValues { (_, containerSlots) -> containerSlots.toList() })
}

fun StaticLayout.areaIsFree(
    container: LayoutContainer,
    position: Int,
    columnSpan: Int,
    rowSpan: Int,
    ignoring: LauncherItem? = null,
): Boolean = container.fitsArea(position, columnSpan, rowSpan) &&
    areaPositions(container, position, columnSpan, rowSpan).all { covered ->
        val item = itemAt(container, covered)
        item == null || item.id == ignoring?.id
    }

fun StaticLayout.firstFreeArea(
    container: LayoutContainer,
    columnSpan: Int,
    rowSpan: Int,
    ignoring: LauncherItem? = null,
): Int? = (0 until container.slotCount).firstOrNull { position ->
    areaIsFree(container, position, columnSpan, rowSpan, ignoring)
}
