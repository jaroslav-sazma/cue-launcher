package app.cue.launcher.designsystem

data class ScrollbarThumb(val offsetPx: Float, val lengthPx: Float)

fun scrollbarThumb(
    trackLengthPx: Float,
    viewportPx: Float,
    contentPx: Float,
    scrollOffsetPx: Float,
    minLengthPx: Float,
): ScrollbarThumb? {
    if (trackLengthPx <= 0f || viewportPx <= 0f || contentPx <= viewportPx) return null
    val length = (trackLengthPx * viewportPx / contentPx)
        .coerceAtLeast(minLengthPx.coerceAtMost(trackLengthPx))
    val progress = (scrollOffsetPx / (contentPx - viewportPx)).coerceIn(0f, 1f)
    return ScrollbarThumb(offsetPx = (trackLengthPx - length) * progress, lengthPx = length)
}
