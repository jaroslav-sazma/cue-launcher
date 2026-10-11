package app.cue.launcher.designsystem

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

internal const val TARGET_RATIO = 4.5

private const val FIT_STEP = 0.08

private const val SRGB_LINEAR_THRESHOLD = 0.03928
private const val SRGB_GAMMA_THRESHOLD = 0.0031308
private const val SRGB_SLOPE = 12.92
private const val SRGB_OFFSET = 0.055
private const val SRGB_SCALE = 1.055
private const val SRGB_EXPONENT = 2.4
private const val LUMA_R = 0.2126
private const val LUMA_G = 0.7152
private const val LUMA_B = 0.0722
private const val CONTRAST_OFFSET = 0.05

private const val BYTE_MASK = 0xFF
private const val BYTE_MAX = 255.0
private const val SHIFT_RED = 16
private const val SHIFT_GREEN = 8
private const val SHIFT_BLUE = 0

private const val REFERENCE_DARK_INK_LUMINANCE = 0.0106
private const val REFERENCE_LIGHT_INK_LUMINANCE = 0.8269

private const val WHITE = 0xFFFFFF
private const val BLACK = 0x000000

internal fun relativeLuminance(rgb: Int): Double {
    val r = toLinear((rgb shr SHIFT_RED and BYTE_MASK) / BYTE_MAX)
    val g = toLinear((rgb shr SHIFT_GREEN and BYTE_MASK) / BYTE_MAX)
    val b = toLinear((rgb shr SHIFT_BLUE and BYTE_MASK) / BYTE_MAX)
    return LUMA_R * r + LUMA_G * g + LUMA_B * b
}

internal fun contrastRatio(a: Double, b: Double): Double =
    (max(a, b) + CONTRAST_OFFSET) / (min(a, b) + CONTRAST_OFFSET)

// The ground this ink still reads on: the brightest one under a dark ink, the darkest over a light one.
internal fun groundFor(inkRgb: Int, brighter: Boolean): Double {
    val ink = relativeLuminance(inkRgb) + CONTRAST_OFFSET
    return (if (brighter) TARGET_RATIO * ink else ink / TARGET_RATIO) - CONTRAST_OFFSET
}

internal fun pickInk(groundLuminance: Double, darkInk: Int, lightInk: Int): Int =
    if (prefersDarkInk(groundLuminance)) darkInk else lightInk

internal fun fit(inkRgb: Int, groundLuminance: Double): Int {
    val towardWhite = !prefersDarkInk(groundLuminance)
    if (contrastRatio(relativeLuminance(inkRgb), groundLuminance) >= TARGET_RATIO) return inkRgb
    val target = if (towardWhite) 1.0 else 0.0
    var t = FIT_STEP
    var fitted = if (towardWhite) WHITE else BLACK
    while (t <= 1.0) {
        val moved = mixChannels(inkRgb, target, t)
        if (contrastRatio(relativeLuminance(moved), groundLuminance) >= TARGET_RATIO) {
            fitted = moved
            break
        }
        t += FIT_STEP
    }
    return fitted
}

internal fun mixLuminance(alpha: Double, gammaWallpaper: Double, gammaTop: Double): Double =
    toLinear(gammaWallpaper * (1 - alpha) + gammaTop * alpha)

internal fun toGamma(luminance: Double): Double =
    if (luminance <= SRGB_GAMMA_THRESHOLD) {
        luminance * SRGB_SLOPE
    } else {
        SRGB_SCALE * luminance.pow(1 / SRGB_EXPONENT) - SRGB_OFFSET
    }

internal fun toLinear(gamma: Double): Double =
    if (gamma <= SRGB_LINEAR_THRESHOLD) gamma / SRGB_SLOPE else ((gamma + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_EXPONENT)

private fun prefersDarkInk(groundLuminance: Double): Boolean =
    contrastRatio(REFERENCE_DARK_INK_LUMINANCE, groundLuminance) >=
        contrastRatio(REFERENCE_LIGHT_INK_LUMINANCE, groundLuminance)

private fun mixChannels(rgb: Int, target: Double, t: Double): Int {
    var result = 0
    for (shift in intArrayOf(SHIFT_RED, SHIFT_GREEN, SHIFT_BLUE)) {
        val v = (rgb shr shift and BYTE_MASK) / BYTE_MAX
        val mixed = ((v + (target - v) * t).coerceIn(0.0, 1.0) * BYTE_MAX).roundToInt()
        result = result or (mixed shl shift)
    }
    return result
}
