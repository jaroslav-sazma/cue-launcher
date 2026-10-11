package app.cue.launcher.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ContrastEngineTest {

    @DisplayName("Glass opacity matches the values measured in light mode")
    @ParameterizedTest(name = "wallpaper {0} → thin {1} / regular {2} / thick {3}")
    @CsvSource(
        "0.85, 0.04, 0.10, 0.16",
        "0.30, 0.30, 0.44, 0.50",
        "0.08, 0.15, 0.28, 0.34",
    )
    fun `glass opacity in light mode`(
        wallpaperLuminance: Double,
        thin: Double,
        regular: Double,
        thick: Double,
    ) {
        val scheme = ContrastEngine.compute(wallpaperLuminance, isDark = false)
        assertEquals(thin, scheme.thinAlpha.toDouble(), TOLERANCE)
        assertEquals(regular, scheme.regularAlpha.toDouble(), TOLERANCE)
        assertEquals(thick, scheme.thickAlpha.toDouble(), TOLERANCE)
    }

    @Test
    fun `over a dark wallpaper the bump hits its ceiling`() {
        val scheme = ContrastEngine.compute(wallpaperLuminance = 0.08, isDark = false)
        assertEquals(ContrastEngine.BUMP_MAX, scheme.bump.toDouble(), TOLERANCE)
    }

    @Test
    fun `nothing is added on top of a light wallpaper`() {
        val scheme = ContrastEngine.compute(wallpaperLuminance = 0.85, isDark = false)
        assertEquals(0.0, scheme.bump.toDouble(), TOLERANCE)
        assertEquals(0.0, scheme.lift.toDouble(), TOLERANCE)
    }

    @DisplayName("In dark mode the tint deepens until the light ink reads on it")
    @ParameterizedTest(name = "wallpaper {0}")
    @CsvSource("0.36", "0.62", "0.85", "1.00")
    fun `dark mode tints against a light wallpaper`(wallpaperLuminance: Double) {
        val scheme = ContrastEngine.compute(wallpaperLuminance, isDark = true)

        assertTrue(scheme.bump > 0f, "the tint has to deepen, the bump was ${scheme.bump}")
        assertTrue(
            relativeLuminance(scheme.textPrimary) > relativeLuminance(MID_GREY),
            "the ink has to come out light, it was ${scheme.textPrimary.toHex()}",
        )
    }

    @Test
    @DisplayName("A dark wallpaper needs no tint of its own in dark mode")
    fun `auto scrim is not used in dark mode`() {
        val scheme = ContrastEngine.compute(wallpaperLuminance = 0.10, isDark = true)
        assertEquals(0.0, scheme.bump.toDouble(), TOLERANCE)
        assertEquals(0.0, scheme.lift.toDouble(), TOLERANCE)
        assertEquals(
            Tokens.Material.regular.opacityDark.toDouble(),
            scheme.regularAlpha.toDouble(),
            TOLERANCE,
        )
    }

    @DisplayName("The surface hierarchy never inverts (thin < regular < thick)")
    @ParameterizedTest(name = "wallpaper {0}, dark mode {1}")
    @CsvSource(
        "0.00, false", "0.08, false", "0.30, false", "0.50, false", "0.62, false", "0.85, false", "1.00, false",
        "0.00, true", "0.30, true", "0.85, true", "1.00, true",
    )
    fun `surface hierarchy`(wallpaperLuminance: Double, isDark: Boolean) {
        val scheme = ContrastEngine.compute(wallpaperLuminance, isDark)
        assertTrue(scheme.thinAlpha < scheme.regularAlpha, "thin < regular")
        assertTrue(scheme.regularAlpha < scheme.thickAlpha, "regular < thick")
        assertTrue(scheme.thickAlpha <= ContrastEngine.OPACITY_CAP.toFloat(), "opacity ceiling")
    }

    @DisplayName("The eyebrow (accent) shifts to the measured values")
    @ParameterizedTest(name = "wallpaper {0} → {1}")
    @CsvSource(
        "0.85, 2F6B60",
        "0.30, 245149",
        "0.08, 091513",
    )
    fun `eyebrow ink`(wallpaperLuminance: Double, expectedHex: String) {
        val scheme = ContrastEngine.compute(wallpaperLuminance, isDark = false)
        assertEquals(expectedHex, scheme.eyebrow.toHex())
    }

    @DisplayName("A chip label on a zone holds the measured ink")
    @ParameterizedTest(name = "wallpaper {0} → {1}")
    @CsvSource(
        "0.85, 434947",
        "0.08, EFEAE4",
    )
    fun `label on a zone`(wallpaperLuminance: Double, expectedHex: String) {
        val scheme = ContrastEngine.compute(wallpaperLuminance, isDark = false)
        assertEquals(expectedHex, scheme.labelOnZone.toHex())
    }

    @DisplayName("In dark mode a zone label stays light and readable over a wallpaper the system dims")
    @ParameterizedTest(name = "wallpaper {0}")
    @CsvSource("0.30", "0.45", "0.60", "0.85", "1.00")
    fun `label on a zone in dark mode`(wallpaperLuminance: Double) {
        val scheme = ContrastEngine.compute(wallpaperLuminance, isDark = true)
        val dimmedGround = mixLuminance(
            scheme.thinAlpha.toDouble(),
            toGamma(wallpaperLuminance) * (1 - BEDTIME_DIM),
            toGamma(relativeLuminance(0x1A1D1D)),
        )

        assertTrue(
            relativeLuminance(scheme.labelOnZone) > relativeLuminance(MID_GREY),
            "the ink has to come out light, it was ${scheme.labelOnZone.toHex()}",
        )
        assertPasses(scheme.labelOnZone, dimmedGround, "label.on-zone over a dimmed wallpaper")
    }

    @Test
    fun `a label on the wallpaper has a fixed threshold`() {
        assertEquals(0x171918, ContrastEngine.compute(0.62, isDark = false).labelOnWallpaper)
        assertEquals(0xEFEAE4, ContrastEngine.compute(0.61, isDark = false).labelOnWallpaper)
    }

    @DisplayName("A label contact shadow always has the opposite polarity to the label")
    @ParameterizedTest(name = "wallpaper {0}")
    @CsvSource("0.00", "0.30", "0.61", "0.62", "0.85", "1.00")
    fun `contact shadow of a label on the wallpaper`(wallpaperLuminance: Double) {
        val scheme = ContrastEngine.compute(wallpaperLuminance, isDark = false)
        assertTrue(
            contrastRatio(
                relativeLuminance(scheme.labelOnWallpaper),
                relativeLuminance(scheme.labelOnWallpaperShadow),
            ) >= TARGET_RATIO,
            "the shadow must have the opposite polarity",
        )
    }

    @DisplayName("Every ink passes 4.5:1 against its own background")
    @ParameterizedTest(name = "wallpaper {0}, dark mode {1}")
    @CsvSource(
        "0.00, false", "0.08, false", "0.30, false", "0.45, false", "0.62, false", "0.85, false", "1.00, false",
        "0.00, true", "0.10, true", "0.45, true", "0.85, true", "1.00, true",
    )
    fun `ink holds contrast`(wallpaperLuminance: Double, isDark: Boolean) {
        val scheme = ContrastEngine.compute(wallpaperLuminance, isDark)
        val gWall = toGamma(wallpaperLuminance)
        val gTop = toGamma(
            if (isDark) relativeLuminance(0x1A1D1D) else 1.0,
        )
        val tileGround = mixLuminance(scheme.regularAlpha.toDouble(), gWall, gTop)
        val zoneGround = mixLuminance(scheme.thinAlpha.toDouble(), gWall, gTop)
        val controlGround = mixLuminance(scheme.thickAlpha.toDouble(), gWall, gTop)

        assertPasses(scheme.textPrimary, tileGround, "text.primary")
        assertPasses(scheme.textSecondary, tileGround, "text.secondary")
        assertPasses(scheme.eyebrow, tileGround, "text.eyebrow")
        assertPasses(scheme.labelOnZone, zoneGround, "label.on-zone")
        assertPasses(scheme.tintedIcon, controlGround, "icon.tinted")

        assertPasses(scheme.semanticInfo, tileGround, "semantic.info")
        assertPasses(scheme.semanticSafe, tileGround, "semantic.safe")
        assertPasses(scheme.semanticCaution, tileGround, "semantic.caution")
        assertPasses(scheme.semanticDanger, tileGround, "semantic.danger")
    }

    @DisplayName("Semantic colors stay four distinct colors")
    @ParameterizedTest(name = "wallpaper {0}, dark mode {1}")
    @CsvSource("0.08, false", "0.30, false", "0.85, false", "0.10, true", "0.85, true")
    fun `semantic colors do not merge`(wallpaperLuminance: Double, isDark: Boolean) {
        val scheme = ContrastEngine.compute(wallpaperLuminance, isDark)
        val inks = listOf(
            scheme.semanticInfo,
            scheme.semanticSafe,
            scheme.semanticCaution,
            scheme.semanticDanger,
        )

        assertEquals(inks.size, inks.toSet().size, inks.map { it.toHex() }.toString())
    }

    @Test
    fun `glyph in a colored icon is chosen by its color`() {
        assertEquals(0x16150F, ContrastEngine.glyphInkOn(0xFFC107))
        assertEquals(0xFFFFFF, ContrastEngine.glyphInkOn(0x1B3A6B))
    }

    @Test
    fun `reduced transparency yields full opacity without a scrim`() {
        val scheme = ContrastEngine.compute(0.30, isDark = false, reducedTransparency = true)
        assertEquals(1.0, scheme.thinAlpha.toDouble(), TOLERANCE)
        assertEquals(1.0, scheme.thickAlpha.toDouble(), TOLERANCE)
        assertEquals(0.0, scheme.bump.toDouble(), TOLERANCE)
    }

    @ParameterizedTest(name = "wallpaper {0}")
    @CsvSource("0.85", "0.50", "0.30")
    fun `a chosen accent leaves the glass to the reference colour`(wallpaperLuminance: Double) {
        val primary = ContrastEngine.compute(wallpaperLuminance, isDark = false)
        val pale = ContrastEngine.compute(
            wallpaperLuminance,
            isDark = false,
            accents = ContrastAccents(accent = PALE_YELLOW, glassReference = ContrastEngine.ACCENT_LIGHT),
        )

        assertEquals(primary.bump, pale.bump)
        assertEquals(primary.thinAlpha, pale.thinAlpha)
        assertEquals(primary.regularAlpha, pale.regularAlpha)
        assertEquals(primary.thickAlpha, pale.thickAlpha)
        assertEquals(PALE_YELLOW, pale.accent)
    }

    @Test
    fun `a pale accent would max the glass if it were the reference`() {
        val unreferenced = ContrastEngine.compute(0.85, isDark = false, accents = ContrastAccents(PALE_YELLOW))

        assertEquals(ContrastEngine.BUMP_MAX, unreferenced.bump.toDouble(), TOLERANCE)
    }

    private fun assertPasses(ink: Int, ground: Double, role: String) {
        val ratio = contrastRatio(relativeLuminance(ink), ground)
        assertTrue(ratio >= TARGET_RATIO - RATIO_TOLERANCE) {
            "$role: ${ink.toHex()} on ground %.3f is only %.2f:1".format(ground, ratio)
        }
    }

    private companion object {
        const val TOLERANCE = 0.01
        const val RATIO_TOLERANCE = 0.01
        const val MID_GREY = 0x808080
        const val BEDTIME_DIM = 0.6
        const val PALE_YELLOW = 0xFFEB3B

        fun Int.toHex(): String = "%06X".format(this)
    }
}
