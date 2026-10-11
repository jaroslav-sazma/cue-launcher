package app.cue.launcher.designsystem

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class LabelVisibilityTest {

    @ParameterizedTest(name = "fontScale {0} → labels visible")
    @ValueSource(floats = [0.85f, 1.0f, 1.15f])
    fun `up to 120 percent labels are drawn`(fontScale: Float) {
        assertTrue(labelsVisibleAt(fontScale))
    }

    @Test
    fun `the boundary of 120 percent is still fine`() {
        assertTrue(labelsVisibleAt(Tokens.Type.HIDE_LABELS_ABOVE_FONT_SCALE))
    }

    @ParameterizedTest(name = "fontScale {0} → labels hidden")
    @ValueSource(floats = [1.21f, 1.5f, 2.0f])
    fun `above 120 percent labels disappear and only the icon remains`(fontScale: Float) {
        assertFalse(labelsVisibleAt(fontScale))
    }
}
