package app.cue.launcher.system.widget

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetRefreshTest {

    @Test
    fun `a widget that has never been refreshed is due`() {
        assertTrue(widgetRefreshDue(lastRefreshAt = null, now = 0L, throttleMs = THROTTLE))
    }

    @Test
    fun `a second look right after the first asks nobody`() {
        assertFalse(widgetRefreshDue(lastRefreshAt = 1_000L, now = 1_500L, throttleMs = THROTTLE))
    }

    @Test
    fun `the throttle window is inclusive at its end`() {
        assertTrue(widgetRefreshDue(lastRefreshAt = 1_000L, now = 31_000L, throttleMs = THROTTLE))
    }

    @Test
    fun `a night of sleep counts towards the window`() {
        assertTrue(widgetRefreshDue(lastRefreshAt = 1_000L, now = 8 * HOUR, throttleMs = THROTTLE))
    }

    private companion object {
        const val THROTTLE = 30_000L

        const val HOUR = 60 * 60 * 1_000L
    }
}
