package app.cue.launcher.designsystem

data class MenuPlacement(
    val x: Int,
    val y: Int,

    val originX: Float,
    val originY: Float,
)

@Suppress("LongParameterList")
fun menuPlacement(
    targetTop: Int,
    targetBottom: Int,
    pressX: Int,
    pressY: Int,
    popupWidth: Int,
    popupHeight: Int,
    windowWidth: Int,
    gap: Int,
    inset: Int,
    safeTop: Int,
    safeBottom: Int,
): MenuPlacement {
    val maxX = (windowWidth - popupWidth - inset).coerceAtLeast(inset)
    val x = (pressX - popupWidth / 2).coerceIn(inset, maxX)

    val below = targetBottom + gap
    val above = targetTop - gap - popupHeight
    val lastY = (safeBottom - popupHeight).coerceAtLeast(safeTop)
    val y = when {
        below + popupHeight <= safeBottom -> below
        above >= safeTop -> above
        else -> below.coerceIn(safeTop, lastY)
    }

    return MenuPlacement(
        x = x,
        y = y,
        originX = originFraction(pressX - x, popupWidth),
        originY = originFraction(pressY - y, popupHeight),
    )
}

private fun originFraction(offset: Int, size: Int): Float =
    if (size <= 0) CENTER_ORIGIN else (offset.toFloat() / size).coerceIn(0f, 1f)

private const val CENTER_ORIGIN = 0.5f
