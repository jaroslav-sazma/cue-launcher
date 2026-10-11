package app.cue.launcher.system

import kotlin.math.roundToInt

internal fun monochromeAlpha(luminance: IntArray, edgeSampleCount: Int): IntArray {
    val min = luminance.min()
    val max = luminance.max()
    if (min >= max) return luminance.copyOf()

    val range = (max - min).toFloat()
    val flip = backgroundIsBright(luminance, edgeSampleCount, min, range)
    return IntArray(luminance.size) { index ->
        val stretched = ((luminance[index] - min) * MAX_ALPHA / range).roundToInt()
        contrastCurve(if (flip) MAX_ALPHA - stretched else stretched)
    }
}

private fun backgroundIsBright(
    luminance: IntArray,
    edgeSampleCount: Int,
    min: Int,
    range: Float,
): Boolean {
    val edge = edgeSampleCount.coerceIn(1, luminance.size / 2)
    var sum = 0L
    for (index in 0 until edge) {
        sum += luminance[index]
        sum += luminance[luminance.size - 1 - index]
    }
    val average = sum / (edge * 2f)
    return (average - min) / range > BRIGHT_THRESHOLD
}

private fun contrastCurve(value: Int): Int {
    val half = MAX_ALPHA / 2 + 1
    val shaped = if (value > half) {
        val coefficient = 1 - (value - half).toDouble() / half
        MAX_ALPHA - coefficient * (MAX_ALPHA - value)
    } else {
        val coefficient = 1 - (half - value).toDouble() / half
        coefficient * value
    }
    return shaped.roundToInt().coerceIn(0, MAX_ALPHA)
}

private const val BRIGHT_THRESHOLD = 0.5f

private const val MAX_ALPHA = 255
