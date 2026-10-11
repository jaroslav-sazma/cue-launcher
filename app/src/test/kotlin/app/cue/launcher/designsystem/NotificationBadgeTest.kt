package app.cue.launcher.designsystem

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationBadgeTest {

    @Test
    fun `a count over 9 is capped so the label never grows past two characters`() {
        assertFalse(badgeOverflows(9))
        assertTrue(badgeOverflows(10))
        assertTrue(badgeOverflows(1000))
    }
}
