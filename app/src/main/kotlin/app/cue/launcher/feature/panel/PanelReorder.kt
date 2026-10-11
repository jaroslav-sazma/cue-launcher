package app.cue.launcher.feature.panel

import app.cue.launcher.data.widgets.movedWidgetOrder

data class PanelSlotBounds(val index: Int, val top: Float, val bottom: Float)

data class PanelMove(val from: Int, val to: Int)

fun panelDropIndex(slots: List<PanelSlotBounds>, y: Float): Int? {
    val ordered = slots.sortedBy(PanelSlotBounds::top)
    if (ordered.isEmpty()) return null
    return (ordered.firstOrNull { y <= it.bottom } ?: ordered.last()).index
}

fun panelPreviewOrder(itemCount: Int, dragged: Int?, target: Int?): List<Int> {
    val order = (0 until itemCount).toList()
    if (dragged == null || target == null || dragged == target) return order
    return movedWidgetOrder(order, dragged, target - dragged) ?: order
}

fun panelPreviewTop(slots: List<PanelSlotBounds>, order: List<Int>, index: Int): Float? {
    val ordered = slots.sortedBy(PanelSlotBounds::index)
    val byIndex = slots.associateBy(PanelSlotBounds::index)
    val gap = if (ordered.size > 1) ordered[1].top - ordered[0].bottom else 0f
    val first = ordered.firstOrNull()
    val preceding = order.takeWhile { it != index }
    val above = preceding.mapNotNull(byIndex::get)

    val complete = first != null && index in order && above.size == preceding.size
    return if (!complete) {
        null
    } else {
        first.top + above.fold(0f) { total, slot -> total + (slot.bottom - slot.top) + gap }
    }
}

fun panelAutoScrollSpeed(
    pointerY: Float,
    viewportTop: Float,
    viewportBottom: Float,
    edge: Float,
    maxSpeed: Float,
): Float {
    if (edge <= 0f || viewportBottom <= viewportTop) return 0f
    val aboveEdge = viewportTop + edge - pointerY
    val belowEdge = pointerY - (viewportBottom - edge)
    return when {
        aboveEdge > 0f -> -maxSpeed * (aboveEdge.coerceAtMost(edge) / edge)
        belowEdge > 0f -> maxSpeed * (belowEdge.coerceAtMost(edge) / edge)
        else -> 0f
    }
}
