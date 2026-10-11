package app.cue.launcher.system

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalendarSelectionTest {

    @Test
    fun `without a choice the query follows the calendar app`() {
        val selection = calendarSelection(null)
        assertTrue(selection.startsWith("visible = 1"), selection)
        assertTrue(!selection.contains("calendar_id IN"), selection)
    }

    @Test
    fun `a choice replaces the visibility flag`() {
        val selection = calendarSelection(setOf(3L, 1L))
        assertTrue(selection.startsWith("calendar_id IN (1,3)"), selection)
        assertTrue(!selection.contains("visible = 1"), selection)
    }

    @Test
    fun `an empty choice matches no calendar`() {
        assertTrue(calendarSelection(emptySet()).startsWith("calendar_id IN (-1)"))
    }
}
