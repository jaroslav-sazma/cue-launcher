package app.cue.launcher.system.notifications

import app.cue.launcher.data.notifications.BadgeKey
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PostedBadgeTest {

    private val posted = PostedBadge(
        key = BadgeKey("com.example.mail", userSerial = 0),
        number = 0,
        isGroupSummary = false,
        hasContent = true,
        ongoing = false,
    )

    @Test
    fun `a notification the ranking has not caught up with yet still counts`() {
        val candidate = posted.candidate(ranking = null)

        assertTrue(candidate.canShowBadge) { "a missing ranking must not hide a fresh notification" }
    }

    @Test
    fun `an ongoing notification is dropped only on the legacy default channel`() {
        val candidate = posted.copy(ongoing = true).candidate(ranking = null)

        assertFalse(candidate.ongoingOnDefaultChannel) { "without a channel there is no default channel to match" }
    }
}
