package app.cue.launcher.feature.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelProgressTest {

    @Test
    fun `panel is one, home screen is zero`() {
        assertEquals(1f, panelProgress(page = 0, pageOffsetFraction = 0f, pageCount = 2))
        assertEquals(0f, panelProgress(page = 1, pageOffsetFraction = 0f, pageCount = 2))
    }

    @Test
    fun `a swipe in progress is in between`() {
        assertEquals(0.7f, panelProgress(page = 0, pageOffsetFraction = 0.3f, pageCount = 2))
    }

    @Test
    fun `overdragging past the edge is clamped`() {
        assertEquals(1f, panelProgress(page = 0, pageOffsetFraction = -0.2f, pageCount = 2))
        assertEquals(0f, panelProgress(page = 1, pageOffsetFraction = 0.2f, pageCount = 2))
    }

    @Test
    fun `more pages divide the range evenly`() {
        assertEquals(0.5f, panelProgress(page = 1, pageOffsetFraction = 0f, pageCount = 3))
    }

    @Test
    fun `a single page does not move anywhere`() {
        assertEquals(0f, panelProgress(page = 0, pageOffsetFraction = 0f, pageCount = 1))
    }

    @Test
    fun `the panel is awake from its first uncovered pixel`() {
        assertTrue(panelInView(panelProgress = 1f))
        assertTrue(panelInView(panelProgress = 0.01f))
        assertFalse(panelInView(panelProgress = 0f))
    }
}
