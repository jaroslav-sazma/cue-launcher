package app.cue.launcher.data.widgets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WidgetOrderTest {

    private val column = listOf(11, 22, 33)

    @Test
    fun `moving up swaps the widget with the previous one`() {
        assertEquals(listOf(22, 11, 33), movedWidgetOrder(column, item = 22, delta = WIDGET_MOVE_UP))
    }

    @Test
    fun `moving down swaps the widget with the next one`() {
        assertEquals(listOf(22, 11, 33), movedWidgetOrder(column, 11, WIDGET_MOVE_DOWN))
    }

    @Test
    @DisplayName("From the edge of a column nothing shifts outward")
    fun `an edge widget cannot leave the column`() {
        assertNull(movedWidgetOrder(column, 11, WIDGET_MOVE_UP))
        assertNull(movedWidgetOrder(column, 33, WIDGET_MOVE_DOWN))
    }

    @Test
    fun `a widget outside the column does not move`() {
        assertNull(movedWidgetOrder(column, item = 44, delta = WIDGET_MOVE_DOWN))
        assertNull(movedWidgetOrder(order = emptyList<Int>(), item = 11, delta = WIDGET_MOVE_UP))
    }

    @Test
    fun `a zero move is not a change`() {
        assertNull(movedWidgetOrder(column, 22, delta = 0))
    }

    @Test
    fun `moving across multiple spots leaves the rest in order`() {
        assertEquals(listOf(22, 33, 11), movedWidgetOrder(column, 11, delta = 2))
        assertNull(movedWidgetOrder(column, 11, delta = 3))
    }

    @Test
    fun `the whole column order is returned`() {
        assertEquals(column.size, movedWidgetOrder(column, 22, WIDGET_MOVE_UP)?.size)
        assertEquals(column.toSet(), movedWidgetOrder(column, 22, WIDGET_MOVE_UP)?.toSet())
    }
}
