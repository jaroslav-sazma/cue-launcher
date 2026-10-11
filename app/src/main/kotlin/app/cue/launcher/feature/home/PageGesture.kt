package app.cue.launcher.feature.home

import kotlin.math.abs

object PageGesture {

    // The action lives right of the home screen, so it is the right-to-left travel that arms it.
    fun triggersAction(travelXPx: Float, travelYPx: Float, thresholdPx: Float): Boolean =
        -travelXPx >= thresholdPx && -travelXPx > abs(travelYPx)

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
