package app.cue.launcher.feature.drawer

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object LastUsed {

    fun daysAgo(
        lastLaunchedAt: Long,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val then = Instant.ofEpochMilli(lastLaunchedAt).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(then, today).coerceAtLeast(0)
    }

    const val TODAY = 0L
    const val YESTERDAY = 1L
}
