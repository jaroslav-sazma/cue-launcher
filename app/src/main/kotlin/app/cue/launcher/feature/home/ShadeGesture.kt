package app.cue.launcher.feature.home

import kotlin.math.abs

object ShadeGesture {

    fun opensShade(travelXPx: Float, travelYPx: Float, thresholdPx: Float): Boolean =
        travelYPx >= thresholdPx && travelYPx > abs(travelXPx)

    fun startsInSystemGestureZone(
        startY: Float,
        containerHeightPx: Float,
        topInsetPx: Float,
        bottomInsetPx: Float,
    ): Boolean = startY < topInsetPx || startY >= containerHeightPx - bottomInsetPx
}
