package app.cue.launcher.feature.widgets

import kotlin.math.abs

enum class WidgetTouchOwner {

    UNDECIDED,

    WIDGET,

    ANCESTOR,
}

class WidgetTouchArbitration(private val slopPx: Float) {

    var owner: WidgetTouchOwner = WidgetTouchOwner.UNDECIDED
        private set

    // Which way the drag ran when the ancestor took it. The pager wants the ones across, the stack
    // of a bento tile the ones along, and a widget that scrolled to its end hands over along too.
    val vertical: Boolean get() = abs(travelYPx) >= abs(travelXPx)

    private var travelXPx = 0f
    private var travelYPx = 0f

    private var overscrollPx = 0f

    fun onDrag(
        deltaXPx: Float,
        deltaYPx: Float,
        canScrollUp: Boolean,
        canScrollDown: Boolean,
    ): Boolean = when (owner) {
        WidgetTouchOwner.ANCESTOR -> false
        WidgetTouchOwner.UNDECIDED -> decide(deltaXPx, deltaYPx, canScrollUp, canScrollDown)
        WidgetTouchOwner.WIDGET -> checkWidgetEnd(deltaYPx, canScrollUp, canScrollDown)
    }

    private fun decide(
        deltaXPx: Float,
        deltaYPx: Float,
        canScrollUp: Boolean,
        canScrollDown: Boolean,
    ): Boolean {
        travelXPx += deltaXPx
        travelYPx += deltaYPx
        owner = when {
            abs(travelXPx) < slopPx && abs(travelYPx) < slopPx -> WidgetTouchOwner.UNDECIDED

            abs(travelXPx) > abs(travelYPx) -> WidgetTouchOwner.ANCESTOR
            scrollsWithinWidget(travelYPx, canScrollUp, canScrollDown) -> WidgetTouchOwner.WIDGET
            else -> WidgetTouchOwner.ANCESTOR
        }
        return owner == WidgetTouchOwner.ANCESTOR
    }

    private fun checkWidgetEnd(
        deltaYPx: Float,
        canScrollUp: Boolean,
        canScrollDown: Boolean,
    ): Boolean {
        if (scrollsWithinWidget(deltaYPx, canScrollUp, canScrollDown)) {
            overscrollPx = 0f
        } else {
            overscrollPx += deltaYPx
        }
        val handover = abs(overscrollPx) >= slopPx
        if (handover) owner = WidgetTouchOwner.ANCESTOR
        return handover
    }

    private companion object {

        fun scrollsWithinWidget(deltaYPx: Float, canScrollUp: Boolean, canScrollDown: Boolean) =
            when {
                deltaYPx > 0f -> canScrollUp
                deltaYPx < 0f -> canScrollDown
                else -> false
            }
    }
}
