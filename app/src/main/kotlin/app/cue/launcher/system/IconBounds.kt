package app.cue.launcher.system

import kotlin.math.roundToInt

internal data class IconBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

internal const val LEGACY_ICON_SCALE = 0.7f

internal fun adaptiveIconBounds(sizePx: Int, extraInsetFraction: Float): IconBounds {
    val inset = (sizePx * extraInsetFraction).roundToInt()
    return IconBounds(left = -inset, top = -inset, right = sizePx + inset, bottom = sizePx + inset)
}

internal fun legacyIconBounds(sizePx: Int, intrinsicWidth: Int, intrinsicHeight: Int): IconBounds {
    val side = (sizePx * LEGACY_ICON_SCALE).roundToInt()
    var width = side
    var height = side
    if (intrinsicWidth > 0 && intrinsicHeight > 0) {
        when {
            intrinsicWidth > intrinsicHeight ->
                height = (side * intrinsicHeight.toFloat() / intrinsicWidth).roundToInt()

            intrinsicHeight > intrinsicWidth ->
                width = (side * intrinsicWidth.toFloat() / intrinsicHeight).roundToInt()
        }
    }
    val left = (sizePx - width) / 2
    val top = (sizePx - height) / 2
    return IconBounds(left = left, top = top, right = left + width, bottom = top + height)
}

internal fun fullBleedIconBounds(sizePx: Int): IconBounds =
    IconBounds(left = 0, top = 0, right = sizePx, bottom = sizePx)
