package app.cue.launcher.data.widgets

import app.cue.launcher.data.layout.ComponentRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class HostedWidgetEntityTest {

    private val calendarProvider = ComponentRef(
        packageName = "com.google.android.calendar",
        className = "com.google.android.apps.calendar.widget.MonthWidgetProvider",
        userSerial = 0,
    )

    @Test
    fun `a hosted widget converts to a row and back`() {
        val widget = HostedWidget(
            appWidgetId = 42,
            provider = calendarProvider,
            boundAt = 1_758_000_000_000,
            position = 3,
        )

        assertEquals(widget, widget.toEntity().toHostedWidget())
    }

    @Test
    fun `without an order a widget starts at zero`() {
        val widget = HostedWidget(appWidgetId = 42, provider = calendarProvider, boundAt = 1)

        assertEquals(0, widget.toEntity().position)
    }

    @Test
    fun `the profile stays part of the provider`() {
        val work = HostedWidget(7, calendarProvider.copy(userSerial = 11), boundAt = 1)
        val personal = HostedWidget(8, calendarProvider, boundAt = 1)

        assertEquals(11, work.toEntity().userSerial)
        assertEquals(0, personal.toEntity().userSerial)
        assertNotEquals(work.provider, personal.provider)
    }

    @Test
    fun `the placement converts to a row and back`() {
        val scenario = HostedWidget(
            appWidgetId = 42,
            provider = calendarProvider,
            boundAt = 1,
            placement = WidgetPlacement.SCENARIO,
        )

        assertEquals("scenario", scenario.toEntity().placement)
        assertEquals(scenario, scenario.toEntity().toHostedWidget())
    }

    @Test
    fun `without a flag a widget belongs to the panel`() {
        val widget = HostedWidget(appWidgetId = 42, provider = calendarProvider, boundAt = 1)

        assertEquals(WidgetPlacement.PANEL, widget.placement)
        assertEquals("panel", widget.toEntity().placement)
    }

    @Test
    fun `an unknown placement key falls back to panel`() {
        val row = HostedWidget(1, calendarProvider, boundAt = 1).toEntity().copy(placement = "overlay")

        assertEquals(WidgetPlacement.PANEL, row.toHostedWidget().placement)
    }
}
