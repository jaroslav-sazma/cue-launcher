package app.cue.launcher.feature.home

import app.cue.launcher.data.scenario.NotificationReply
import app.cue.launcher.data.scenario.TriggerPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TileReplyTest {

    @Test
    fun `the dialog title is the notification title`() {
        val payload = TriggerPayload(
            fields = mapOf("notif:title" to "Honza", "notif:text" to "Kde jsi?"),
            reply = NotificationReply(key = "prvni"),
        )

        assertEquals("Honza", replyTitleOf(payload, scenarioName = "Zprávy"))
    }

    @Test
    fun `without a notification title, the scenario name applies`() {
        val payload = TriggerPayload(reply = NotificationReply(key = "prvni"))

        assertEquals("Zprávy", replyTitleOf(payload, scenarioName = "Zprávy"))
    }

    @Test
    fun `a blank title is not used`() {
        val payload = TriggerPayload(fields = mapOf("notif:title" to "   "))

        assertEquals("Zprávy", replyTitleOf(payload, scenarioName = "Zprávy"))
    }
}
