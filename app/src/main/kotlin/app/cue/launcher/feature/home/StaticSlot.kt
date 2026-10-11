package app.cue.launcher.feature.home

import app.cue.launcher.data.layout.LauncherItem
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.data.layout.StaticLayout

data class StaticSlot(
    val container: LayoutContainer,
    val position: Int,
    val item: LauncherItem?,
) {

    val row: Int get() = container.rowOf(position)

    val column: Int get() = container.columnOf(position)

    val isContinuation: Boolean get() = item != null && item.position != position

    val columnSpan: Int get() = item?.columnSpan ?: 1

    val rowSpan: Int get() = item?.rowSpan ?: 1
}

fun staticSlotRows(layout: StaticLayout, container: LayoutContainer): List<List<StaticSlot>> =
    layout.slots(container)
        .mapIndexed { position, item -> StaticSlot(container, position, item) }
        .chunked(container.columns)

fun staticSlotCells(slots: List<StaticSlot>): List<StaticSlot> = slots.filterNot { it.isContinuation }
