package app.cue.launcher.system.widget

data class WidgetSizeSpec(
    val minWidthPx: Int,
    val minHeightPx: Int,

    val horizontallyResizable: Boolean,

    val verticallyResizable: Boolean = false,

    val minResizeWidthPx: Int = minWidthPx,
    val minResizeHeightPx: Int = minHeightPx,
) {

    val smallestWidthPx: Int
        get() = if (horizontallyResizable && minResizeWidthPx > 0) {
            minOf(minWidthPx, minResizeWidthPx)
        } else {
            minWidthPx
        }

    val smallestHeightPx: Int
        get() = if (verticallyResizable && minResizeHeightPx > 0) {
            minOf(minHeightPx, minResizeHeightPx)
        } else {
            minHeightPx
        }
}

fun widgetFitsSlot(spec: WidgetSizeSpec, slotWidthPx: Int, slotHeightPx: Int): Boolean =
    widgetFitsSlot(spec.smallestWidthPx, spec.smallestHeightPx, slotWidthPx, slotHeightPx)

fun widgetFitsSlot(smallestWidthPx: Int, smallestHeightPx: Int, slotWidthPx: Int, slotHeightPx: Int): Boolean =
    smallestWidthPx <= slotWidthPx && smallestHeightPx <= slotHeightPx

data class WidgetBox(val widthPx: Int, val heightPx: Int)

// The panel row grid is authoritative: the slot height is what the user set, whatever the provider
// asks for. Only the width still falls back to the natural size of a non-resizable widget.
fun panelWidgetBox(spec: WidgetSizeSpec, slotWidthPx: Int, slotHeightPx: Int): WidgetBox {
    val available = slotWidthPx.coerceAtLeast(0)
    val natural = spec.minWidthPx.coerceAtLeast(0)
    val width = if (spec.horizontallyResizable) available else minOf(natural, available)
    return WidgetBox(widthPx = width, heightPx = slotHeightPx.coerceAtLeast(0))
}

fun bentoWidgetBox(spec: WidgetSizeSpec, slotWidthPx: Int, slotHeightPx: Int): WidgetBox {
    val availableWidth = slotWidthPx.coerceAtLeast(0)
    val availableHeight = slotHeightPx.coerceAtLeast(0)
    val naturalWidth = spec.minWidthPx.coerceAtLeast(0)
    val naturalHeight = spec.minHeightPx.coerceAtLeast(0)
    return WidgetBox(
        widthPx = if (spec.horizontallyResizable) availableWidth else minOf(naturalWidth, availableWidth),
        heightPx = if (spec.verticallyResizable) availableHeight else minOf(naturalHeight, availableHeight),
    )
}
