package app.cue.launcher.feature.home

import app.cue.launcher.engine.QueueEntry

fun stackStep(travelPx: Float, thresholdPx: Float): Int = when {
    travelPx <= -thresholdPx -> 1
    travelPx >= thresholdPx -> -1
    else -> 0
}

fun stackIndexOf(pages: List<QueueEntry>, selectedId: String?): Int =
    pages.indexOfFirst { it.scenario.id == selectedId }.coerceAtLeast(0)

fun stackPageAt(index: Int, step: Int, pageCount: Int): Int =
    if (pageCount <= 0) 0 else (index + step).coerceIn(0, pageCount - 1)

fun stackDotWindow(pageCount: Int, activeIndex: Int, maxDots: Int): IntRange {
    val dots = minOf(pageCount, maxDots)
    if (dots <= 0) return IntRange.EMPTY
    val active = activeIndex.coerceIn(0, pageCount - 1)
    val start = (active - dots + 1).coerceAtLeast(0)
    return start until start + dots
}

fun stackPlusCount(pageCount: Int, dotCount: Int): Int = (pageCount - dotCount).coerceAtLeast(0)
