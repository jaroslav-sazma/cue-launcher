package app.cue.launcher.feature.drawer

object DrawerGesture {

    fun startsInSystemGestureZone(
        startY: Float,
        containerHeightPx: Float,
        bottomInsetPx: Float,
    ): Boolean = startY >= containerHeightPx - bottomInsetPx

    fun opensDrawer(travelPx: Float, thresholdPx: Float): Boolean = travelPx <= -thresholdPx

    fun closesDrawer(travelPx: Float, thresholdPx: Float): Boolean = travelPx >= thresholdPx
}
