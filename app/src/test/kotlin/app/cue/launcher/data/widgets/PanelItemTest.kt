package app.cue.launcher.data.widgets

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.LauncherWidgetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PanelItemTest {

    @Test
    fun `a column is ordered by position regardless of kind`() {
        val column = panelColumnOf(
            hosted = listOf(hosted(appWidgetId = 7, position = 2)),
            launcherWidgets = listOf(
                launcherWidget(id = 1, position = 0),
                launcherWidget(id = 2, position = 3),
            ),
        )

        assertEquals(
            listOf(
                PanelItemKey(PanelItemKind.LAUNCHER_WIDGET, 1),
                PanelItemKey(PanelItemKind.APP_WIDGET, 7),
                PanelItemKey(PanelItemKind.LAUNCHER_WIDGET, 2),
            ),
            column.map(PanelItem::key),
        )
    }

    @Test
    fun `an equal position is decided by kind and identity`() {
        val column = panelColumnOf(
            hosted = listOf(hosted(appWidgetId = 9, position = 0), hosted(appWidgetId = 3, position = 0)),
            launcherWidgets = listOf(launcherWidget(id = 5, position = 0)),
        )

        assertEquals(
            listOf(
                PanelItemKey(PanelItemKind.APP_WIDGET, 3),
                PanelItemKey(PanelItemKind.APP_WIDGET, 9),
                PanelItemKey(PanelItemKind.LAUNCHER_WIDGET, 5),
            ),
            column.map(PanelItem::key),
        )
    }

    @Test
    fun `empty inputs give an empty column`() {
        assertEquals(emptyList<PanelItem>(), panelColumnOf(emptyList(), emptyList()))
    }

    @Test
    fun `an unknown widget kind is skipped`() {
        val row = PanelWidgetEntity(id = 1, widget = "kamera", position = 0, addedAt = 0)

        assertNull(row.toPanelItem())
        assertEquals(
            LauncherWidgetType.CALENDAR,
            row.copy(widget = "calendar").toPanelItem()?.widget,
        )
    }

    @Test
    fun `moving works across both kinds of items`() {
        val order = listOf(
            PanelItemKey(PanelItemKind.APP_WIDGET, 7),
            PanelItemKey(PanelItemKind.LAUNCHER_WIDGET, 7),
        )

        val moved = movedWidgetOrder(order, order[1], WIDGET_MOVE_UP)

        assertEquals(listOf(order[1], order[0]), moved)
        assertNull(movedWidgetOrder(order, order[0], WIDGET_MOVE_UP))
    }

    private fun hosted(appWidgetId: Int, position: Int) = HostedWidget(
        appWidgetId = appWidgetId,
        provider = ComponentRef("com.android.deskclock", "com.android.deskclock.Widget", 0),
        boundAt = 0,
        position = position,
    )

    private fun launcherWidget(id: Long, position: Int) = PanelItem.LauncherWidget(
        id = id,
        widget = LauncherWidgetType.CLOCK,
        position = position,
    )
}
