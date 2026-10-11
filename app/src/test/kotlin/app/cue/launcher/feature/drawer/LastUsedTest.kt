package app.cue.launcher.feature.drawer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LastUsedTest {

    private val prague = ZoneId.of("Europe/Prague")

    @Test
    fun `a launch an hour ago is today`() {
        assertEquals(LastUsed.TODAY, daysAgo(at = "2026-09-16T08:30", now = "2026-09-16T09:30"))
    }

    @Test
    fun `a launch late last night is yesterday, even an hour later`() {
        assertEquals(LastUsed.YESTERDAY, daysAgo(at = "2026-09-15T23:50", now = "2026-09-16T00:50"))
    }

    @Test
    fun `a launch this morning is today, even late at night`() {
        assertEquals(LastUsed.TODAY, daysAgo(at = "2026-09-16T06:00", now = "2026-09-16T23:59"))
    }

    @Test
    fun `an older launch is counted in days`() {
        assertEquals(5L, daysAgo(at = "2026-09-11T12:00", now = "2026-09-16T09:00"))
        assertEquals(366L, daysAgo(at = "2025-09-15T12:00", now = "2026-09-16T09:00"))
    }

    @Test
    fun `a launch in the future is reported as today`() {
        assertEquals(LastUsed.TODAY, daysAgo(at = "2026-09-18T12:00", now = "2026-09-16T09:00"))
    }

    private fun daysAgo(at: String, now: String): Long = LastUsed.daysAgo(
        lastLaunchedAt = millis(at),
        now = millis(now),
        zone = prague,
    )

    private fun millis(local: String): Long =
        LocalDateTime.parse(local).atZone(prague).toInstant().toEpochMilli()
}
