package app.cue.launcher.system

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.IconMode
import app.cue.launcher.designsystem.IconTint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IconTintingTest {

    private val tint = IconTint(background = 0xCC1A1D1D.toInt(), foreground = 0xFFF2EEE9.toInt())

    private val chrome = ComponentRef("com.android.chrome", "com.google.android.apps.chrome.Main", 0L)

    private val maps = ComponentRef("com.google.android.apps.maps", "com.google.android.maps.MapsActivity", 0L)

    @Test
    fun `the monochrome layer takes priority over the foreground`() {
        assertEquals(
            TintedSource.MONOCHROME,
            tintedSourceOf(hasMonochrome = true, hasForeground = true),
        )
    }

    @Test
    fun `an adaptive icon without a monochrome layer gives a silhouette from the foreground`() {
        assertEquals(
            TintedSource.FOREGROUND,
            tintedSourceOf(hasMonochrome = false, hasForeground = true),
        )
    }

    @Test
    fun `an icon without layers is the last resort`() {
        assertEquals(
            TintedSource.LEGACY,
            tintedSourceOf(hasMonochrome = false, hasForeground = false),
        )
    }

    @Test
    fun `tinting only happens in tinted mode`() {
        val tinted = IconStyle(mode = IconMode.TINTED, tint = tint)

        assertTrue(tinted.tints(chrome))
        assertFalse(tinted.copy(mode = IconMode.NATIVE).tints(chrome))
        assertFalse(tinted.copy(mode = IconMode.ICONPACK).tints(chrome))
    }

    @Test
    fun `an app with an exception stays native`() {
        val style = IconStyle(mode = IconMode.TINTED, tint = tint, overrides = setOf(chrome))

        assertFalse(style.tints(chrome))
        assertTrue(style.tints(maps))
    }

    @Test
    fun `without colors there is no tinting`() {
        assertFalse(IconStyle(mode = IconMode.TINTED, tint = null).tints(chrome))
    }

    @Test
    fun `changing the mode discards finished icons`() {
        val native = IconStyle(mode = IconMode.NATIVE, tint = tint)
        val tinted = native.copy(mode = IconMode.TINTED)

        assertTrue(iconsMustRedraw(native, tinted))
        assertTrue(iconsMustRedraw(tinted, native))
    }

    @Test
    fun `the mode preview ignores per-app exceptions`() {
        val live = IconStyle(mode = IconMode.NATIVE, tint = tint, overrides = setOf(chrome))

        val preview = IconRequest(mode = IconMode.TINTED).styleOver(live)

        assertEquals(IconMode.TINTED, preview.mode)
        assertTrue(preview.tints(chrome))
    }

    // A bento chip holds its own colour, so an app icon in it is tinted even when the appearance
    // setting says native and the app has an exception of its own.
    @Test
    fun `a bento icon is tinted whatever the setting says`() {
        val live = IconStyle(mode = IconMode.NATIVE, tint = null, overrides = setOf(chrome))
        val onChip = IconTint(background = 0, foreground = 0xFFF2EEE9.toInt())

        val style = IconRequest(mode = IconMode.TINTED, tint = onChip).styleOver(live)

        assertTrue(style.tints(chrome))
        assertTrue(style.tints(maps))
        assertEquals(onChip, style.tint)
    }

    @Test
    fun `a different surface changes only the tint, not the mode or exceptions`() {
        val live = IconStyle(mode = IconMode.TINTED, tint = tint, overrides = setOf(chrome))
        val onSurface = IconTint(background = 0xFFDEE4E0.toInt(), foreground = 0xFF171D1B.toInt())

        val style = IconRequest(tint = onSurface).styleOver(live)

        assertEquals(IconMode.TINTED, style.mode)
        assertEquals(onSurface, style.tint)
        assertFalse(style.tints(chrome))
        assertTrue(style.tints(maps))
    }

    @Test
    fun `an empty request leaves the home screen appearance alone`() {
        val live = IconStyle(mode = IconMode.TINTED, tint = tint, overrides = setOf(chrome))

        assertEquals(live, IconRequest().styleOver(live))
    }

    @Test
    fun `color and exceptions only redraw tinted icons`() {
        val nativeMode = IconStyle(mode = IconMode.NATIVE, tint = tint)
        val tintedMode = IconStyle(mode = IconMode.TINTED, tint = tint)
        val other = IconTint(background = 0, foreground = 0)

        assertFalse(iconsMustRedraw(nativeMode, nativeMode.copy(tint = other)))
        assertFalse(iconsMustRedraw(nativeMode, nativeMode.copy(overrides = setOf(chrome))))
        assertTrue(iconsMustRedraw(tintedMode, tintedMode.copy(tint = other)))
        assertTrue(iconsMustRedraw(tintedMode, tintedMode.copy(overrides = setOf(chrome))))
    }
}
