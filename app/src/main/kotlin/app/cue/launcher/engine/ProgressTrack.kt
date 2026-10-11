package app.cue.launcher.engine

import app.cue.launcher.data.scenario.SemanticStyle
import app.cue.launcher.data.scenario.TileProgress
import app.cue.launcher.data.scenario.TileProgressSegment

data class ProgressTrack(
    val fraction: Float?,
    val bands: List<ProgressBand>,
    val marks: List<ProgressMark> = emptyList(),
) {

    val percent: Int? get() = fraction?.let { (it * PERCENT).toInt() }
}

data class ProgressBand(
    val start: Float,
    val end: Float,
    val style: SemanticStyle = SemanticStyle.UNSPECIFIED,
    val filled: Boolean = false,
)

data class ProgressMark(
    val at: Float,
    val style: SemanticStyle = SemanticStyle.UNSPECIFIED,
)

fun progressTrack(progress: TileProgress?): ProgressTrack? {
    if (progress == null) return null
    val total = progressTotal(progress)
    return if (total > 0) {
        progress.trackOf(total)
    } else {

        INDETERMINATE.takeIf { progress.indeterminate }
    }
}

private fun TileProgress.trackOf(total: Int): ProgressTrack {
    val fraction = if (indeterminate) null else (value.toFloat() / total).coerceIn(0f, 1f)
    return ProgressTrack(
        fraction = fraction,
        bands = bandsOf(segments, total, fraction ?: 0f),

        marks = points
            .map { ProgressMark(at = it.position.toFloat() / total, style = it.style) }
            .filter { it.at in 0f..1f },
    )
}

private fun progressTotal(progress: TileProgress): Int {
    val fromSegments = progress.segments.sumOf { it.length.coerceAtLeast(0) }
    return if (fromSegments > 0) fromSegments else progress.max
}

private fun bandsOf(
    segments: List<TileProgressSegment>,
    total: Int,
    fraction: Float,
): List<ProgressBand> {
    if (segments.none { it.length > 0 }) return splitAt(ProgressBand(0f, 1f), fraction)
    var offset = 0
    return buildList {
        segments.filter { it.length > 0 }.forEach { segment ->
            val start = offset.toFloat() / total
            offset += segment.length
            val end = (offset.toFloat() / total).coerceAtMost(1f)
            addAll(splitAt(ProgressBand(start, end, segment.style), fraction))
        }
    }
}

private fun splitAt(band: ProgressBand, fraction: Float): List<ProgressBand> = when {
    band.end <= band.start -> emptyList()
    fraction <= band.start -> listOf(band)
    fraction >= band.end -> listOf(band.copy(filled = true))
    else -> listOf(
        band.copy(end = fraction, filled = true),
        band.copy(start = fraction),
    )
}

private val INDETERMINATE = ProgressTrack(fraction = null, bands = listOf(ProgressBand(0f, 1f)))

private const val PERCENT = 100
