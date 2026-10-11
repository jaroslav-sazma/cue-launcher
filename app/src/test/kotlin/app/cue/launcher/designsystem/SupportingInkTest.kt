package app.cue.launcher.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SupportingInkTest {

    private fun contrast(ink: Color, ground: Color): Double =
        contrastRatio(ink.luminance().toDouble(), ground.luminance().toDouble())

    private val grounds = listOf(Color(0xFF1B1B1F), Color(0xFF2B2930), Color(0xFFF7F2FA), Color(0xFFECE6F0))

    private val inks = listOf(Color(0xFFC4C7C5), Color(0xFF8E9099), Color(0xFF49454F), Color(0xFF6F6A75))

    // Wallpaper colours can leave little headroom; the shade must stop before the text stops reading.
    @Test
    fun `the shade never takes body text below the contrast floor`() {
        grounds.forEach { ground ->
            inks.filter { contrast(it, ground) >= TARGET_RATIO }.forEach { ink ->
                val shade = lightestShade(ink, ground)
                assertTrue(contrast(shade, ground) >= TARGET_RATIO) { "$ink on $ground became $shade" }
            }
        }
    }

    // The point is to step back from the text it explains, so whatever headroom there is gets used.
    @Test
    fun `the shade lands on the floor rather than short of it`() {
        grounds.forEach { ground ->
            inks.filter { contrast(it, ground) >= TARGET_RATIO }.forEach { ink ->
                val shade = lightestShade(ink, ground)
                assertEquals(TARGET_RATIO, contrast(shade, ground), FLOOR_TOLERANCE) { "$ink on $ground" }
            }
        }
    }

    @Test
    fun `the shade is opaque`() {
        assertEquals(1f, lightestShade(Color(0xFF49454F), Color(0xFFF7F2FA)).alpha)
    }

    @Test
    fun `an ink at or below the floor is left alone`() {
        listOf(Color(0xFF777777), Color(0xFF949494)).forEach { ink ->
            assertTrue(contrast(ink, Color.White) <= TARGET_RATIO)
            assertEquals(ink, lightestShade(ink, Color.White))
        }
    }

    // With almost no headroom the search may find no step that passes; the ink must then come back as
    // it was, not as a round trip through the colour space that can land a hair below the floor.
    @Test
    fun `an ink just above the floor stays above it`() {
        val ink = Color(0xFF767676)

        assertTrue(contrast(ink, Color.White) > TARGET_RATIO)
        assertTrue(contrast(lightestShade(ink, Color.White), Color.White) >= TARGET_RATIO)
    }

    @Test
    fun `a translucent ground leaves the ink unshaded`() {
        val ink = Color(0xFF49454F)

        assertEquals(ink, lightestShade(ink, Color(0x80F7F2FA)))
        assertEquals(ink, lightestShade(ink, Color.Transparent))
    }

    private companion object {
        // Each channel is kept in 8 bits, so the shade can only come this close to the exact floor.
        const val FLOOR_TOLERANCE = 0.1
    }
}
