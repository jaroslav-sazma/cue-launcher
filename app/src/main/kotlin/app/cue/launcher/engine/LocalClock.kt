package app.cue.launcher.engine

import java.time.ZonedDateTime

private const val MILLIS_PER_SECOND = 1_000
private const val NANOS_PER_MILLI = 1_000_000

fun interface LocalClock {

    fun reading(): LocalClockReading

    companion object {
        val SYSTEM: LocalClock = LocalClock {

            val now = ZonedDateTime.now()
            LocalClockReading(
                clock = ClockState(
                    isoDayOfWeek = now.dayOfWeek.value,
                    minuteOfDay = now.hour * MINUTES_PER_HOUR + now.minute,
                ),
                millisIntoMinute = now.second * MILLIS_PER_SECOND + now.nano / NANOS_PER_MILLI,
            )
        }
    }
}

data class LocalClockReading(
    val clock: ClockState,
    val millisIntoMinute: Int,
)
