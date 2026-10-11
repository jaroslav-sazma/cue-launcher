package app.cue.launcher.feature.home

import androidx.compose.ui.unit.dp
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.widgets.widgetInnerRadius
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LauncherWidgetFrameTest {

    @Test
    @DisplayName("The inner corner radius of a 4×2 wrapper lands on the radius.widgetInner token")
    fun `the inner corner radius lands on the token`() {
        assertEquals(Tokens.Radius.widgetInner, widgetInnerRadius(Tokens.Radius.tile4x2))
    }

    @Test
    fun `a smaller class has a smaller inner radius by the same frame`() {
        assertEquals(
            Tokens.Radius.tile2x2 - Tokens.Padding.widgetHolder,
            widgetInnerRadius(Tokens.Radius.tile2x2),
        )
    }

    @Test
    @DisplayName("A radius smaller than the frame does not go negative")
    fun `a small radius is clamped to zero`() {
        assertEquals(0.dp, widgetInnerRadius(2.dp))
    }
}
