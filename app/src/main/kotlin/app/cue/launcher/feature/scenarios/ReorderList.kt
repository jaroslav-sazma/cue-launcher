package app.cue.launcher.feature.scenarios

import kotlin.math.roundToInt

fun reorderTargetIndex(from: Int, offsetPx: Float, rowHeightPx: Float, count: Int): Int {
    if (count <= 0) return 0
    val moved = if (rowHeightPx <= 0f) 0 else (offsetPx / rowHeightPx).roundToInt()
    return (from + moved).coerceIn(0, count - 1)
}

fun reorderShift(index: Int, from: Int, to: Int): Int = when {
    index == from -> 0
    from < to && index in (from + 1)..to -> -1
    to < from && index in to..(from - 1) -> 1
    else -> 0
}

fun <T> List<T>.reordered(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
