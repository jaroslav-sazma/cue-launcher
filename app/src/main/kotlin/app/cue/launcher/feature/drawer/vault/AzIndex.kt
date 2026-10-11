package app.cue.launcher.feature.drawer.vault

const val NO_LETTER = -1

fun azLetterAt(y: Float, heightPx: Float, count: Int): Int {
    if (count <= 0 || heightPx <= 0f) return NO_LETTER
    return (y / heightPx * count).toInt().coerceIn(0, count - 1)
}

fun azExclusionSpan(heightPx: Float, limitPx: Float): ClosedFloatingPointRange<Float> {
    val height = heightPx.coerceAtLeast(0f)
    return (height - limitPx).coerceAtLeast(0f)..height
}
