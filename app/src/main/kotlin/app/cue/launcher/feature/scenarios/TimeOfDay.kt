package app.cue.launcher.feature.scenarios

internal const val MINUTES_IN_HOUR = 60

internal const val HOURS_IN_DAY = 24

@Suppress("ReturnCount")
internal fun parseHourMinute(value: String): Pair<Int, Int>? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0 until HOURS_IN_DAY || minute !in 0 until MINUTES_IN_HOUR) return null
    return hour to minute
}
