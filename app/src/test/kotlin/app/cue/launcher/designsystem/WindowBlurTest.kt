package app.cue.launcher.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WindowBlurTest {

    private val max = 60

    @Test
    fun `the home screen has no blur`() {
        assertEquals(0, wallpaperBlurRadiusPx(fraction = 0f, maxRadiusPx = max))
    }

    @Test
    fun `the radius grows linearly with the fraction`() {
        assertEquals(max / 2, wallpaperBlurRadiusPx(fraction = 0.5f, maxRadiusPx = max))
        assertEquals(max, wallpaperBlurRadiusPx(fraction = 1f, maxRadiusPx = max))
    }

    @Test
    fun `a fraction outside the range is clamped`() {
        assertEquals(0, wallpaperBlurRadiusPx(fraction = -0.4f, maxRadiusPx = max))
        assertEquals(max, wallpaperBlurRadiusPx(fraction = 1.3f, maxRadiusPx = max))
    }

    @Test
    fun `a non-positive max radius turns off blur`() {
        assertEquals(0, wallpaperBlurRadiusPx(fraction = 1f, maxRadiusPx = 0))
        assertEquals(0, wallpaperBlurRadiusPx(fraction = 1f, maxRadiusPx = -20))
    }

    @Test
    fun `the drawer does not blur up to the threshold`() {
        assertEquals(0f, drawerBlurFraction(progress = 0f))
        assertEquals(0f, drawerBlurFraction(progress = 0.5f))
        assertEquals(0f, drawerBlurFraction(Tokens.Motion.WALLPAPER_BLUR_START))
    }

    @Test
    fun `the drawer grows gradually past the threshold`() {
        val start = Tokens.Motion.WALLPAPER_BLUR_START
        assertEquals(0.5f, drawerBlurFraction(start + (1f - start) / 2f), 1e-6f)
        assertTrue(drawerBlurFraction(progress = 0.9f) in 0.01f..0.99f)
        assertEquals(1f, drawerBlurFraction(progress = 1f))
    }

    @Test
    fun `the drawer is clamped outside the range`() {
        assertEquals(0f, drawerBlurFraction(progress = -0.4f))
        assertEquals(1f, drawerBlurFraction(progress = 1.3f))
    }
}
