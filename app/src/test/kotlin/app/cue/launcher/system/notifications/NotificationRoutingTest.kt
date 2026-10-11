package app.cue.launcher.system.notifications

import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class NotificationRoutingTest {

    private val scenarios = listOf(
        Scenario(
            id = "a",
            name = "A",
            size = TileSize.GRID_1X1,
            content = TileContent.Template(
                layout = TemplateLayout.ICON_TEXT,
                items = listOf(TemplateItem(actions = emptyList())),
            ),
        ),
    )

    @Test
    fun `before the scenarios arrive a notification is passed on but not reported as a match`() {
        var asked = false

        val routing = notificationRouting(watched = null) {
            asked = true
            true
        }

        assertEquals(NotificationRouting.UNJUDGED, routing)
        assertFalse(asked)
    }

    @Test
    fun `a notification a scenario listens to is a match`() {
        assertEquals(NotificationRouting.MATCHED, notificationRouting(scenarios) { true })
    }

    @Test
    fun `a notification no scenario listens to is ignored`() {
        assertEquals(NotificationRouting.IGNORED, notificationRouting(scenarios) { false })
    }
}
