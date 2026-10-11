package app.cue.launcher.feature.icons

import androidx.compose.runtime.mutableStateOf
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.notifications.BadgeKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationCountsTest {

    private val counts = NotificationCounts(
        mutableStateOf(
            mapOf(
                BadgeKey("com.example.mail", userSerial = 0) to 3,
                BadgeKey("com.example.mail", userSerial = 10) to 2,
                BadgeKey("com.example.chat", userSerial = 0) to 1,
            ),
        ),
    )

    @Test
    fun `a folder counts a package once however many of its activities it holds`() {
        val folder = listOf(
            ComponentRef("com.example.mail", "Inbox", userSerial = 0),
            ComponentRef("com.example.mail", "Compose", userSerial = 0),
            ComponentRef("com.example.chat", "Main", userSerial = 0),
        )

        assertEquals(4, counts.of(folder))
    }

    @Test
    fun `the same package in another profile counts on its own`() {
        val folder = listOf(
            ComponentRef("com.example.mail", "Inbox", userSerial = 0),
            ComponentRef("com.example.mail", "Inbox", userSerial = 10),
        )

        assertEquals(5, counts.of(folder))
    }
}
