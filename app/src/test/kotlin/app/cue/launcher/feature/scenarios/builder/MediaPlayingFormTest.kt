package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.system.InstalledApp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaPlayingFormTest {

    @Test
    fun `a ticked app that is not installed keeps a row of its own`() {
        val players = listOf(InstalledApp(ComponentRef("com.spotify.music", "Main", userSerial = 0), "Spotify"))

        val rows = mediaAppRows(players, ticked = listOf("org.gone.radio", "com.spotify.music"))

        assertEquals(
            listOf(
                MediaAppRow("com.spotify.music", "Spotify", installed = true),
                MediaAppRow("org.gone.radio", "org.gone.radio", installed = false),
            ),
            rows,
        )
    }

    @Test
    fun `ticking adds an app once and unticking removes it`() {
        val picked = toggledPackages(listOf("a"), "b", on = true)

        assertEquals(listOf("a", "b"), picked)
        assertEquals(listOf("a", "b"), toggledPackages(picked, "b", on = true))
        assertEquals(listOf("b"), toggledPackages(picked, "a", on = false))
        assertEquals(emptyList<String>(), toggledPackages(listOf("b"), "b", on = false))
    }
}
