package app.cue.launcher.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WallpaperParallaxTest {

    private val swing = Tokens.Motion.WALLPAPER_PARALLAX_SWING

    @Test
    fun `home rests exactly on the frame chosen in the picker`() {
        assertEquals(0f, wallpaperParallaxOffset(progress = 0f, rtl = false))
        assertEquals(1f, wallpaperParallaxOffset(progress = 0f, rtl = true))
    }

    @Test
    fun `the panel spends the swing into the spare width`() {
        assertEquals(swing, wallpaperParallaxOffset(progress = 1f, rtl = false), 1e-6f)
        assertEquals(1f - swing, wallpaperParallaxOffset(progress = 1f, rtl = true), 1e-6f)
        assertEquals(swing / 2f, wallpaperParallaxOffset(progress = 0.5f, rtl = false), 1e-6f)
    }

    @Test
    fun `progress outside the range never leaves the reported range`() {
        assertEquals(0f, wallpaperParallaxOffset(progress = -0.3f, rtl = false))
        assertEquals(swing, wallpaperParallaxOffset(progress = 1.4f, rtl = false), 1e-6f)
    }
}
