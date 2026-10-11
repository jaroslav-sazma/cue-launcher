package app.cue.launcher.feature.panel

import androidx.compose.ui.unit.dp
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.layout.LauncherItem
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.home.StaticSlot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PanelBarLayoutTest {

    @Test
    @DisplayName("With gesture navigation the strip lands exactly on the token")
    fun `gesture navigation gives the token height`() {
        assertEquals(Tokens.Size.PanelBar.height, panelBarStripHeight(navigationBarInset = 24.dp))
        assertEquals(34.dp, panelBarBottomPadding(navigationBarInset = 24.dp))
    }

    @Test
    fun `three-button nav stretches the strip by the inset difference`() {
        assertEquals(48.dp, panelBarBottomPadding(navigationBarInset = 48.dp))
        assertEquals(
            Tokens.Size.PanelBar.height + 14.dp,
            panelBarStripHeight(navigationBarInset = 48.dp),
        )
    }

    @Test
    fun `the rest of the strip remains above the icons`() {
        val strip = panelBarStripHeight(navigationBarInset = 24.dp)

        assertEquals(15.dp, strip - Tokens.Size.icon - panelBarBottomPadding(24.dp))
    }

    @Test
    fun `without an inset, room remains for the gesture zone`() {
        assertEquals(Tokens.Size.PanelBar.height, panelBarStripHeight(navigationBarInset = 0.dp))
    }

    @Test
    fun `an empty dock shows what its slots are for`() {
        assertTrue(panelBarNeedsSlotHint(dock(null, null, null, null)))
    }

    @Test
    fun `a single icon is clue enough for the whole dock`() {
        assertFalse(panelBarNeedsSlotHint(dock(null, null, null, shortcut())))
    }

    private fun dock(vararg items: LauncherItem?): List<StaticSlot> =
        items.mapIndexed { position, item -> StaticSlot(LayoutContainer.PANEL_DOCK, position, item) }

    private fun shortcut(): LauncherItem = LauncherItem.Shortcut(
        id = 1L,
        container = LayoutContainer.PANEL_DOCK,
        position = 3,
        component = ComponentRef(packageName = "app.cue.example", className = "Main", userSerial = 0L),
    )
}
