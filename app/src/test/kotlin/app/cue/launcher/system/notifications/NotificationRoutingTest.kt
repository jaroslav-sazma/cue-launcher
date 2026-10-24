package app.cue.launcher.system.notifications

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.engine.PostedNotification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `an end post with nothing running yet passes the filter and is a match`() {
        val until = EndEvent.Notification(packageName = "com.example.parcel", contains = "delivered")
        val ending = scenarios.single().copy(conditions = listOf(Condition.HaEvent(code = "washer", until = until)))
        val watched = Watched(listOf(ending), waiting = emptyList())
        val delivered = post(text = "Delivered to the door")
        val other = post(text = "On its way")

        assertTrue(watched.listensTo("Com.Example.Parcel"))
        assertEquals(NotificationRouting.MATCHED, notificationRouting(watched.scenarios) { watched.wants(delivered) })
        assertEquals(NotificationRouting.IGNORED, notificationRouting(watched.scenarios) { watched.wants(other) })
        assertFalse(Watched(listOf(ending.copy(enabled = false)), waiting = emptyList()).listensTo(PARCEL_APP))
    }

    private fun post(text: String) =
        PostedNotification(key = "k", packageName = PARCEL_APP, title = "Parcel", text = text)

    private companion object {
        const val PARCEL_APP = "com.example.parcel"
    }
}
