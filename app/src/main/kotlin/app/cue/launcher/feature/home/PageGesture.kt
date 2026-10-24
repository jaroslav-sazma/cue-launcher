package app.cue.launcher.feature.home

import kotlin.math.abs

object PageGesture {

    // The action lives right of the home screen, so it is the right-to-left travel that arms it.
    fun triggersAction(travelXPx: Float, travelYPx: Float, thresholdPx: Float): Boolean =
        -travelXPx >= thresholdPx && -travelXPx > abs(travelYPx)

    // Mirrors awaitLongPress: a pointer still within the slop when the long-press timeout runs
    // out has become a lift, a reorder or a menu, so it must never be read as a swipe afterwards.
    fun swipeHold(
        distanceFromDownPx: Float,
        elapsedMillis: Long,
        slopPx: Float,
        longPressTimeoutMillis: Long,
    ): SwipeHold = when {
        elapsedMillis >= longPressTimeoutMillis -> SwipeHold.LONG_PRESS
        distanceFromDownPx > slopPx -> SwipeHold.SWIPE
        else -> SwipeHold.UNDECIDED
    }

    fun startsInEdgeZone(
        startX: Float,
        containerWidthPx: Float,
        leftInsetPx: Float,
        rightInsetPx: Float,

    ): Boolean = startX < leftInsetPx || startX >= containerWidthPx - rightInsetPx

    fun blocksPaging(travelXPx: Float, travelYPx: Float, slopPx: Float): Boolean =
        abs(travelXPx) >= slopPx && abs(travelXPx) > abs(travelYPx)

    fun dragPageStep(xPx: Float, containerWidthPx: Float, edgeZonePx: Float): Int = when {
        containerWidthPx <= 0f || edgeZonePx <= 0f -> 0
        xPx < edgeZonePx -> -1
        xPx > containerWidthPx - edgeZonePx -> 1
        else -> 0
    }
}

enum class SwipeHold { UNDECIDED, SWIPE, LONG_PRESS }
