package app.cue.launcher.feature.home

import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.TapAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TileClickTest {

    private fun actions(record: (String) -> Unit) = TileActions(
        perform = { _, _ -> record("perform") },
        close = {},
        openCalendar = { record("calendar") },
        openAlarms = { record("alarms") },
        openMedia = { record("media") },
    )

    @Test
    fun `media leads to the chosen player`() {
        val calls = mutableListOf<String>()
        val click = actions(calls::add).tileClick(LauncherWidgetType.MEDIA, emptyList()) { null }

        click?.invoke()

        assertEquals(listOf("media"), calls)
    }

    @Test
    fun `clock leads to alarms`() {
        val calls = mutableListOf<String>()
        val click = actions(calls::add).tileClick(LauncherWidgetType.CLOCK, emptyList()) { null }

        click?.invoke()

        assertEquals(listOf("alarms"), calls)
    }

    @Test
    fun `calendar leads to the default calendar app`() {
        val calls = mutableListOf<String>()
        val click = actions(calls::add).tileClick(LauncherWidgetType.CALENDAR, emptyList()) { null }

        click?.invoke()

        assertEquals(listOf("calendar"), calls)
    }

    @Test
    fun `a tile without actions is not clickable`() {
        assertNull(actions {}.tileClick(widget = null, tileActions = emptyList()) { null })
    }

    @Test
    fun `a template triggers its actions`() {
        val calls = mutableListOf<String>()
        val click = actions(calls::add)
            .tileClick(widget = null, tileActions = listOf(TapAction.App("com.spotify.music"))) { null }

        click?.invoke()

        assertEquals(listOf("perform"), calls)
    }
}
