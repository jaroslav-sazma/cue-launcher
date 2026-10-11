package app.cue.launcher.designsystem

import kotlin.math.max
import kotlin.math.min

object ContrastEngine {

    const val OPACITY_CAP = 0.94

    const val BUMP_MAX = 0.18

    const val BUMP_THIN_FACTOR = 0.6

    const val WALLPAPER_LABEL_THRESHOLD = 0.62

    const val ACCENT_LIGHT = 0x2F6B60

    const val ACCENT_DARK = 0x8FD3C4

    const val DARK_SURFACE_TONE = 0x1A1D1D

    private const val INK_PRIMARY_DARK = 0x181A19
    private const val INK_PRIMARY_LIGHT = 0xF2EEE9
    private const val INK_SECONDARY_DARK = 0x434947
    private const val INK_SECONDARY_LIGHT = 0xCBC5BE
    private const val INK_ZONE_DARK = 0x434947
    private const val INK_ZONE_LIGHT = 0xEFEAE4
    private const val LABEL_ON_WALLPAPER_DARK = 0x171918
    private const val LABEL_ON_WALLPAPER_LIGHT = 0xEFEAE4

    private const val INK_TINTED_DARK = INK_PRIMARY_DARK
    private const val INK_TINTED_LIGHT = INK_PRIMARY_LIGHT

    private const val GLYPH_INK_DARK = 0x16150F
    private const val GLYPH_INK_LIGHT = 0xFFFFFF

    private const val SEMANTIC_INFO = 0x1B6EF3
    private const val SEMANTIC_SAFE = 0x1E8E3E
    private const val SEMANTIC_CAUTION = 0xE37400
    private const val SEMANTIC_DANGER = 0xD93025

    fun compute(
        wallpaperLuminance: Double,
        isDark: Boolean,
        reducedTransparency: Boolean = false,
        accents: ContrastAccents = ContrastAccents(if (isDark) ACCENT_DARK else ACCENT_LIGHT),
    ): ContrastScheme {
        val wpLum = wallpaperLuminance.coerceIn(0.0, 1.0)
        val gWall = toGamma(wpLum)
        val gTop = toGamma(if (isDark) relativeLuminance(DARK_SURFACE_TONE) else 1.0)

        val bump = bumpFor(isDark, reducedTransparency, accents.glassReference, gWall, gTop)

        val baseAlpha = { spec: MaterialSpec, bumpFactor: Double ->
            min(OPACITY_CAP, spec.baseOpacity(isDark) + bump * bumpFactor)
        }
        // A partial bump in dark mode would leave the zone a mid-grey veil, see bumpFor.
        val rawThin = baseAlpha(Tokens.Material.thin, if (isDark) 1.0 else BUMP_THIN_FACTOR)

        val zoneRaw = mixLuminance(rawThin, gWall, gTop)
        val zoneOk = reducedTransparency || !isDark && max(
            contrastRatio(relativeLuminance(INK_ZONE_DARK), zoneRaw),
            contrastRatio(relativeLuminance(INK_ZONE_LIGHT), zoneRaw),
        ) >= TARGET_RATIO
        // Dark mode always settles the zone on the light ink. Bedtime dims the wallpaper in the
        // compositor while WallpaperColors keep reporting the undimmed palette, so the real ground
        // can only be darker than estimated — which a light ink survives and a dark one does not.
        val zoneInk = if (isDark) INK_ZONE_LIGHT else INK_ZONE_DARK
        val lift = if (zoneOk) {
            0.0
        } else {
            max(
                0.0,
                alphaForGround(groundFor(zoneInk, brighter = !isDark), gWall, gTop, brighter = !isDark) - rawThin,
            )
        }

        val finalAlpha = { alpha: Double -> if (reducedTransparency) 1.0 else min(OPACITY_CAP, alpha + lift) }
        val thinAlpha = finalAlpha(rawThin)
        val regularAlpha = finalAlpha(baseAlpha(Tokens.Material.regular, 1.0))
        val thickAlpha = finalAlpha(baseAlpha(Tokens.Material.thick, 1.0))

        val zoneGround = mixLuminance(thinAlpha, gWall, gTop)
        val tileGround = mixLuminance(regularAlpha, gWall, gTop)
        val controlGround = mixLuminance(thickAlpha, gWall, gTop)
        val lightWallpaper = wpLum >= WALLPAPER_LABEL_THRESHOLD

        return ContrastScheme(
            thinAlpha = thinAlpha.toFloat(),
            regularAlpha = regularAlpha.toFloat(),
            thickAlpha = thickAlpha.toFloat(),
            bump = bump.toFloat(),
            lift = lift.toFloat(),
            textPrimary = fit(pickInk(tileGround, INK_PRIMARY_DARK, INK_PRIMARY_LIGHT), tileGround),
            textSecondary = fit(pickInk(tileGround, INK_SECONDARY_DARK, INK_SECONDARY_LIGHT), tileGround),
            eyebrow = fit(accents.accent, tileGround),
            labelOnZone = fit(pickInk(zoneGround, INK_ZONE_DARK, INK_ZONE_LIGHT), zoneGround),
            tintedIcon = fit(pickInk(controlGround, INK_TINTED_DARK, INK_TINTED_LIGHT), controlGround),
            labelOnWallpaper = if (lightWallpaper) LABEL_ON_WALLPAPER_DARK else LABEL_ON_WALLPAPER_LIGHT,

            labelOnWallpaperShadow = if (lightWallpaper) LABEL_ON_WALLPAPER_LIGHT else LABEL_ON_WALLPAPER_DARK,
            accent = accents.accent,
            glassReference = accents.glassReference,

            accentPalette = accents.palette.ifEmpty { listOf(accents.accent) }.map { fit(it, tileGround) },

            semanticInfo = fit(SEMANTIC_INFO, tileGround),
            semanticSafe = fit(SEMANTIC_SAFE, tileGround),
            semanticCaution = fit(SEMANTIC_CAUTION, tileGround),
            semanticDanger = fit(SEMANTIC_DANGER, tileGround),
        )
    }

    // Light mode brightens the glass until the reference accent reads on it, and the cap keeps it
    // glass. Dark mode is the mirror image: the tint deepens until the light ink reads on it. There it
    // runs uncapped, because a half-dark veil would only take contrast away from both inks.
    private fun bumpFor(
        isDark: Boolean,
        reducedTransparency: Boolean,
        reference: Int,
        gWall: Double,
        gTop: Double,
    ): Double = when {
        reducedTransparency -> 0.0
        isDark -> max(
            0.0,
            alphaForGround(groundFor(INK_PRIMARY_LIGHT, brighter = false), gWall, gTop, brighter = false) -
                Tokens.Material.regular.opacityDark,
        )

        else -> min(
            BUMP_MAX,
            max(
                0.0,
                alphaForGround(groundFor(reference, brighter = true), gWall, gTop, brighter = true) -
                    Tokens.Material.regular.opacityLight,
            ),
        )
    }

    // How much of the top tone it takes to carry the ground to this luminance — up to it in light
    // mode, down to it in dark mode. A target the wallpaper already meets, or a tone lying on the
    // wrong side of it, needs nothing.
    private fun alphaForGround(
        groundLuminance: Double,
        gammaWallpaper: Double,
        gammaTop: Double,
        brighter: Boolean,
    ): Double {
        val target = toGamma(groundLuminance)
        val need = if (brighter) target - gammaWallpaper else gammaWallpaper - target
        val reach = if (brighter) gammaTop - gammaWallpaper else gammaWallpaper - gammaTop
        if (need <= 0.0 || reach <= 0.0) return 0.0
        return min(OPACITY_CAP, need / reach)
    }

    fun glyphInkOn(iconColor: Int): Int =
        pickInk(relativeLuminance(iconColor), GLYPH_INK_DARK, GLYPH_INK_LIGHT)
}

data class ContrastAccents(
    val accent: Int,
    val palette: List<Int> = emptyList(),

    // The colour the light glass is brightened for. It stays the system's own when the user picks
    // another accent, so that choice never changes how opaque the glass is.
    val glassReference: Int = accent,
)

data class ContrastScheme(
    val thinAlpha: Float,
    val regularAlpha: Float,
    val thickAlpha: Float,

    val bump: Float,

    val lift: Float,
    val textPrimary: Int,
    val textSecondary: Int,
    val eyebrow: Int,
    val labelOnZone: Int,
    val tintedIcon: Int,
    val labelOnWallpaper: Int,

    val labelOnWallpaperShadow: Int,
    val accent: Int,
    val glassReference: Int = accent,

    val accentPalette: List<Int> = listOf(accent),

    val semanticInfo: Int,
    val semanticSafe: Int,
    val semanticCaution: Int,
    val semanticDanger: Int,
) {

    fun alphaOf(level: GlassLevel): Float = when (level) {
        GlassLevel.THIN -> thinAlpha
        GlassLevel.REGULAR -> regularAlpha
        GlassLevel.THICK -> thickAlpha
    }
}

enum class GlassLevel {
    THIN,
    REGULAR,
    THICK,
    ;

    val spec: MaterialSpec
        get() = when (this) {
            THIN -> Tokens.Material.thin
            REGULAR -> Tokens.Material.regular
            THICK -> Tokens.Material.thick
        }
}
