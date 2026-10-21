package app.cue.launcher.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrandTypeTest {

    @Test
    fun `a family carries every weight and both styles, Google Sans Flex before sans-serif`() {
        val fonts = (brandFontFamily(14.sp, emphasized = false) as FontListFontFamily).fonts
        val cuts = BRAND_WEIGHTS.flatMap { weight -> BRAND_STYLES.map { weight to it } }

        assertEquals(cuts + cuts, fonts.map { it.weight to it.style })
        assertTrue(fonts.take(cuts.size).all { "google-sans-flex" in it.toString() })
        assertTrue(fonts.drop(cuts.size).all { "sans-serif" in it.toString() })
    }

    @Test
    fun `every axis is spelled out, rounded only when emphasized`() {
        val baseline = axesOf(BrandFace.Flex, FontWeight.Medium, FontStyle.Normal, 16, false)
        val emphasized = axesOf(BrandFace.Flex, FontWeight.Bold, FontStyle.Normal, 16, true)

        assertEquals(
            mapOf("wght" to 500f, "wdth" to 100f, "GRAD" to 0f, "ROND" to 0f, "opsz" to 16f, "slnt" to 0f),
            baseline,
        )
        assertEquals(100f, emphasized["ROND"])
        assertEquals(700f, emphasized["wght"])
    }

    @Test
    fun `italic is a slant on Google Sans Flex and the ital axis on sans-serif`() {
        assertEquals(-10f, axesOf(BrandFace.Flex, FontWeight.Normal, FontStyle.Italic, 14, false)["slnt"])
        assertEquals(1f, axesOf(BrandFace.System, FontWeight.Normal, FontStyle.Italic, 14, false)["ital"])
        assertEquals(0f, axesOf(BrandFace.System, FontWeight.Normal, FontStyle.Normal, 14, false)["ital"])
    }

    @Test
    fun `the optical size follows the text size in whole sp and families are shared`() {
        val family = brandFontFamily(10.5f.sp, emphasized = true)
        val opsz = (family as FontListFontFamily).fonts
            .map { (it as AndroidFont).variationSettings.settings.single { s -> s.axisName == "opsz" } }
            .map { it.toVariationValue(null) }
            .distinct()

        assertEquals(listOf(11f), opsz)
        assertSame(family, brandFontFamily(10.5f.sp, emphasized = true))
    }

    @Test
    fun `an emphasized style is its baseline twin a weight step heavier and rounded`() {
        val material = Typography()
        val baseline = styles(material).take(BASELINE_STYLES)
        val brand = styles(brandTypography(material))

        (baseline + baseline).zip(brand).forEachIndexed { index, (from, to) ->
            val emphasized = index >= BASELINE_STYLES
            val step = if (emphasized) 100 else 0
            assertEquals(from.fontWeight!!.weight + step, to.fontWeight!!.weight)
            assertEquals(from.fontSize, to.fontSize)
            assertEquals(from.lineHeight, to.lineHeight)
            assertEquals(from.letterSpacing, to.letterSpacing)
            assertSame(brandFontFamily(from.fontSize, emphasized), to.fontFamily)
        }
    }

    @Test
    fun `sans-serif gets every axis it has pinned in fonts xml, so Roboto renders as before`() {
        assertEquals(
            mapOf("wght" to 400f, "wdth" to 100f, "GRAD" to 0f, "ROND" to 0f, "opsz" to 14f, "ital" to 0f),
            axesOf(BrandFace.System, FontWeight.Normal, FontStyle.Normal, 14, false),
        )
    }

    private fun axesOf(
        face: BrandFace,
        weight: FontWeight,
        style: FontStyle,
        opticalSize: Int,
        emphasized: Boolean,
    ): Map<String, Float> = brandAxes(face, weight, style, BrandKey(opticalSize, emphasized))
        .settings.associate { it.axisName to it.toVariationValue(null) }

    private fun styles(t: Typography): List<TextStyle> = with(t) {
        listOf(
            displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall,
            titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall,
            labelLarge, labelMedium, labelSmall,
            displayLargeEmphasized, displayMediumEmphasized, displaySmallEmphasized,
            headlineLargeEmphasized, headlineMediumEmphasized, headlineSmallEmphasized,
            titleLargeEmphasized, titleMediumEmphasized, titleSmallEmphasized,
            bodyLargeEmphasized, bodyMediumEmphasized, bodySmallEmphasized,
            labelLargeEmphasized, labelMediumEmphasized, labelSmallEmphasized,
        )
    }

    private companion object {
        const val BASELINE_STYLES = 15
    }
}
