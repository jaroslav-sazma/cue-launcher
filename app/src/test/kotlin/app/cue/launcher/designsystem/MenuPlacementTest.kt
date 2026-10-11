package app.cue.launcher.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MenuPlacementTest {

    private val windowWidth = 1080
    private val safeTop = 100
    private val safeBottom = 2340
    private val popupWidth = 600
    private val popupHeight = 400
    private val gap = 40
    private val inset = 20

    private fun place(
        targetTop: Int,
        targetBottom: Int,
        pressX: Int,
        pressY: Int,
        popupHeight: Int = this.popupHeight,
    ) = menuPlacement(
        targetTop = targetTop,
        targetBottom = targetBottom,
        pressX = pressX,
        pressY = pressY,
        popupWidth = popupWidth,
        popupHeight = popupHeight,
        windowWidth = windowWidth,
        gap = gap,
        inset = inset,
        safeTop = safeTop,
        safeBottom = safeBottom,
    )

    @Test
    @DisplayName("The panel centers on the press point and goes below the target")
    fun `the panel goes below the target centered on the finger`() {
        val placement = place(targetTop = 800, targetBottom = 958, pressX = 540, pressY = 880)
        assertEquals(540 - popupWidth / 2, placement.x)
        assertEquals(958 + gap, placement.y)
    }

    @Test
    fun `the offset is measured from the target's edge`() {
        val high = place(targetTop = 800, targetBottom = 958, pressX = 540, pressY = 810)
        val low = place(targetTop = 800, targetBottom = 958, pressX = 540, pressY = 950)
        assertEquals(high.y, low.y)
    }

    @Test
    @DisplayName("Near the screen edges the panel is clamped to the inset")
    fun `the panel is clamped to the inset from the edges`() {
        assertEquals(inset, place(targetTop = 800, targetBottom = 958, pressX = 40, pressY = 880).x)
        assertEquals(
            windowWidth - popupWidth - inset,
            place(targetTop = 800, targetBottom = 958, pressX = 1040, pressY = 880).x,
        )
    }

    @Test
    @DisplayName("With no room below the target the panel flips above it")
    fun `the panel flips above the target`() {
        val placement = place(targetTop = 2100, targetBottom = 2258, pressX = 540, pressY = 2180)
        assertEquals(2100 - gap - popupHeight, placement.y)
    }

    @Test
    fun `a panel with no room lands on the zone's bottom edge`() {
        val tall = 2000
        val placement = place(
            targetTop = 2100,
            targetBottom = 2258,
            pressX = 540,
            pressY = 2180,
            popupHeight = tall,
        )
        assertEquals(safeBottom - tall, placement.y)
    }

    @Test
    @DisplayName("The panel grows out of the press point")
    fun `the origin is the press point in the panel's coordinates`() {
        val placement = place(targetTop = 800, targetBottom = 958, pressX = 540, pressY = 880)
        assertEquals(0.5f, placement.originX, 1e-6f)

        assertEquals(0f, placement.originY, 1e-6f)
    }

    @Test
    fun `the origin near the edge goes to the panel's edge`() {
        val placement = place(targetTop = 800, targetBottom = 958, pressX = 40, pressY = 880)
        assertEquals((40 - inset) / popupWidth.toFloat(), placement.originX, 1e-6f)
    }

    @Test
    fun `a panel too wide lands on the left edge`() {
        val placement = menuPlacement(
            targetTop = 800,
            targetBottom = 958,
            pressX = 540,
            pressY = 880,
            popupWidth = windowWidth + 120,
            popupHeight = popupHeight,
            windowWidth = windowWidth,
            gap = gap,
            inset = inset,
            safeTop = safeTop,
            safeBottom = safeBottom,
        )
        assertEquals(inset, placement.x)
    }
}
