package app.cue.launcher.data.notifications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationBadgesTest {

    private val mail = BadgeKey("com.example.mail", userSerial = 0)
    private val workMail = BadgeKey("com.example.mail", userSerial = 10)
    private val chat = BadgeKey("com.example.chat", userSerial = 0)

    private fun candidate(key: BadgeKey, number: Int = 0) = BadgeCandidate(
        key = key,
        number = number,
        canShowBadge = true,
        isGroupSummary = false,
        hasContent = true,
        ongoingOnDefaultChannel = false,
    )

    @Test
    fun `every notification counts once unless it carries its own number`() {
        val counts = badgeCounts(
            listOf(candidate(mail), candidate(mail, number = 5), candidate(chat)),
        )

        assertEquals(mapOf(mail to 6, chat to 1), counts)
    }

    @Test
    fun `the same app in a work profile gets its own count`() {
        val counts = badgeCounts(listOf(candidate(mail), candidate(workMail), candidate(workMail)))

        assertEquals(mapOf(mail to 1, workMail to 2), counts)
    }

    @Test
    fun `what the system would not badge is left out`() {
        val counts = badgeCounts(
            listOf(
                candidate(mail).copy(canShowBadge = false),
                candidate(mail).copy(isGroupSummary = true),
                candidate(mail).copy(hasContent = false),
                candidate(mail).copy(ongoingOnDefaultChannel = true),
            ),
        )

        assertEquals(emptyMap<BadgeKey, Int>(), counts) { "an app with nothing to badge has no entry at all" }
    }

    @Test
    fun `a zero or negative number still counts as one notification`() {
        val counts = badgeCounts(listOf(candidate(mail, number = 0), candidate(mail, number = -3)))

        assertEquals(mapOf(mail to 2), counts)
    }
}
