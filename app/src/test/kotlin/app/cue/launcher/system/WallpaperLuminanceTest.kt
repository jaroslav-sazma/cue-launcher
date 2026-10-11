package app.cue.launcher.system

import app.cue.launcher.designsystem.ContrastEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WallpaperLuminanceTest {

    private val threshold = ContrastEngine.WALLPAPER_LABEL_THRESHOLD

    @Test
    fun `a light wallpaper with an understated palette ends up above the threshold`() {

        val luminance = wallpaperLuminance(paletteLuminance = 0.35, supportsDarkText = true)
        assertTrue(luminance >= threshold, "a light wallpaper must yield a dark label")
        assertEquals(
            0x171918,
            ContrastEngine.compute(luminance, isDark = false).labelOnWallpaper,
        )
    }

    @Test
    fun `a dark wallpaper stays below the threshold`() {
        val luminance = wallpaperLuminance(paletteLuminance = 0.12, supportsDarkText = false)
        assertEquals(0.12, luminance)
        assertEquals(
            0xEFEAE4,
            ContrastEngine.compute(luminance, isDark = false).labelOnWallpaper,
        )
    }

    @Test
    fun `an overstated palette without the flag is pushed below the threshold`() {
        val luminance = wallpaperLuminance(paletteLuminance = 0.90, supportsDarkText = false)
        assertTrue(luminance < threshold, "without the flag the label must not turn black")
        assertEquals(
            0xEFEAE4,
            ContrastEngine.compute(luminance, isDark = false).labelOnWallpaper,
        )
    }

    @Test
    fun `a value inside the correct half is left alone`() {
        assertEquals(0.88, wallpaperLuminance(paletteLuminance = 0.88, supportsDarkText = true))
        assertEquals(0.30, wallpaperLuminance(paletteLuminance = 0.30, supportsDarkText = false))
    }
}
