package app.cue.launcher.data.widgets

const val WIDGET_MOVE_UP: Int = -1
const val WIDGET_MOVE_DOWN: Int = 1

fun <T> movedWidgetOrder(order: List<T>, item: T, delta: Int): List<T>? {
    val from = order.indexOf(item)
    val to = from + delta
    if (from < 0 || delta == 0 || to !in order.indices) return null
    return order.toMutableList().apply {
        removeAt(from)
        add(to, item)
    }
}
