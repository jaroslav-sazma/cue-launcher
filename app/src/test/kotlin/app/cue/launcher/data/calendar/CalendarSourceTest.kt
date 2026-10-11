package app.cue.launcher.data.calendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CalendarSourceTest {

    @Test
    fun `without a stored choice the visible calendars are selected`() {
        assertEquals(setOf(1L, 3L), selectedCalendarIds(SOURCES, stored = null))
    }

    @Test
    fun `a stored choice wins over the visibility flag`() {
        assertEquals(setOf(2L), selectedCalendarIds(SOURCES, stored = setOf(2L)))
    }

    @Test
    fun `a calendar that is gone drops out of the stored choice`() {
        assertEquals(setOf(1L), selectedCalendarIds(SOURCES, stored = setOf(1L, 99L)))
    }

    @Test
    fun `an empty choice stays empty`() {
        assertEquals(emptySet<Long>(), selectedCalendarIds(SOURCES, stored = emptySet()))
    }

    @Test
    fun `toggling adds and removes`() {
        assertEquals(setOf(1L, 2L), toggledCalendarIds(setOf(1L), id = 2L))
        assertEquals(setOf(1L), toggledCalendarIds(setOf(1L, 2L), id = 2L))
    }

    @Test
    fun `an unknown calendar list is never pruned`() {
        assertNull(prunedCalendarSelection(calendars = null, stored = setOf(1L, 2L)))
    }

    @Test
    fun `nothing stored means nothing to prune`() {
        assertNull(prunedCalendarSelection(SOURCES, stored = null))
        assertNull(prunedCalendarSelection(SOURCES, stored = emptySet()))
    }

    @Test
    fun `a stored choice with every calendar still present is left untouched`() {
        assertNull(prunedCalendarSelection(SOURCES, stored = setOf(1L, 2L)))
    }

    @Test
    fun `a calendar that disappeared is pruned out of the stored choice`() {
        assertEquals(setOf(1L), prunedCalendarSelection(SOURCES, stored = setOf(1L, 99L)))
    }

    @Test
    fun `losing every stored calendar falls back to the default`() {
        val pruned = prunedCalendarSelection(SOURCES, stored = setOf(98L, 99L))

        assertEquals(emptySet<Long>(), pruned)
        assertNull(pruned?.ifEmpty { null }, "the collapsed write goes back to the default")
    }

    private companion object {

        val SOURCES = listOf(
            calendarSource(id = 1, visible = true),
            calendarSource(id = 2, visible = false),
            calendarSource(id = 3, visible = true),
        )

        fun calendarSource(id: Long, visible: Boolean) = CalendarSource(
            id = id,
            name = "Calendar $id",
            account = null,
            color = 0,
            visible = visible,
        )
    }
}
