package app.cue.launcher.engine

import app.cue.launcher.data.calendar.CalendarEvent
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalendarRulesTest {

    @Test
    fun `a condition for an ongoing event needs the calendar`() {
        assertTrue(usesCalendar(listOf(scenario(conditions = listOf(Condition.CalendarEvent())))))
    }

    @Test
    fun `a tile with a calendar needs the calendar even without a condition`() {
        val widget = scenario(content = TileContent.LauncherWidget(LauncherWidgetType.CALENDAR))

        assertTrue(usesCalendar(listOf(widget)))
    }

    @Test
    fun `a disabled scenario counts too`() {
        val off = scenario(conditions = listOf(Condition.CalendarEvent())).copy(enabled = false)

        assertTrue(usesCalendar(listOf(off)))
    }

    @Test
    fun `another custom widget or another condition does not want the calendar`() {
        val hodiny = scenario(content = TileContent.LauncherWidget(LauncherWidgetType.CLOCK))
        val nabijeni = scenario(conditions = listOf(Condition.Charging()))

        assertFalse(usesCalendar(listOf(hodiny, nabijeni)))
        assertFalse(usesCalendar(emptyList()))
    }

    private fun scenario(
        conditions: List<Condition> = emptyList(),
        content: TileContent = TileContent.Template(
            layout = TemplateLayout.ICON_TEXT,
            items = listOf(TemplateItem(actions = listOf(TapAction.App(packageName = "com.example")))),
        ),
    ): Scenario = Scenario(
        id = "test",
        name = "Test",
        size = if (content is TileContent.LauncherWidget) TileSize.HERO_4X2 else TileSize.GRID_1X1,
        conditions = conditions,
        content = content,
    )
}

class CalendarOngoingTest {

    private val now = 1_600_000_000_000L

    @Test
    fun `an ongoing meeting holds`() {
        val events = listOf(event(start = now - MINUTE, end = now + MINUTE))

        assertTrue(calendarEventOngoing(events, now))
    }

    @Test
    fun `the start holds immediately and the end no longer does`() {
        val events = listOf(event(start = now, end = now + HOUR))

        assertTrue(calendarEventOngoing(events, now))
        assertFalse(calendarEventOngoing(events, now + HOUR))
    }

    @Test
    fun `neither a future nor a finished meeting holds`() {
        val budouci = listOf(event(start = now + HOUR, end = now + 2 * HOUR))
        val skoncena = listOf(event(start = now - 2 * HOUR, end = now - HOUR))

        assertFalse(calendarEventOngoing(budouci, now))
        assertFalse(calendarEventOngoing(skoncena, now))
        assertFalse(calendarEventOngoing(emptyList(), now))
    }

    @Test
    fun `an all-day event does not count`() {
        val events = listOf(event(start = now - HOUR, end = now + HOUR, allDay = true))

        assertFalse(calendarEventOngoing(events, now))
        assertNull(calendarEdgeWaitMillis(events, now))
    }

    @Test
    fun `a zero-length and a broken event do not count`() {
        val nulova = listOf(event(start = now, end = now))
        val pozpatku = listOf(event(start = now + HOUR, end = now - HOUR))

        assertFalse(calendarEventOngoing(nulova, now))
        assertFalse(calendarEventOngoing(pozpatku, now))
        assertNull(calendarEdgeWaitMillis(nulova, now))
        assertNull(calendarEdgeWaitMillis(pozpatku, now))
    }

    @Test
    fun `it waits for the nearest edge`() {
        val probihajici = event(start = now - HOUR, end = now + 10 * MINUTE)
        val budouci = event(start = now + 30 * MINUTE, end = now + 40 * MINUTE)

        assertEquals(10 * MINUTE, calendarEdgeWaitMillis(listOf(probihajici, budouci), now))
        assertEquals(30 * MINUTE, calendarEdgeWaitMillis(listOf(budouci), now))
    }

    @Test
    fun `the wait is capped at an hour`() {
        val zitra = listOf(event(start = now + 24 * HOUR, end = now + 25 * HOUR))

        assertEquals(HOUR, calendarEdgeWaitMillis(zitra, now))
    }

    @Test
    fun `without a future edge there is no waiting`() {
        val skoncena = listOf(event(start = now - 2 * HOUR, end = now - HOUR))

        assertNull(calendarEdgeWaitMillis(skoncena, now))
        assertNull(calendarEdgeWaitMillis(emptyList(), now))
    }

    private fun event(start: Long, end: Long, allDay: Boolean = false): CalendarEvent = CalendarEvent(
        eventId = 1,
        title = "Schůzka",
        location = null,
        startMillis = start,
        endMillis = end,
        allDay = allDay,
    )

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
