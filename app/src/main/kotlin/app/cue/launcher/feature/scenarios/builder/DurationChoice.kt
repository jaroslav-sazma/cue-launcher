package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.engine.DEFAULT_INSTANT_DURATION_SEC
import java.math.RoundingMode

enum class DurationChoice(val seconds: Int?) {
    SEC_30(SHORT_SECONDS),
    SEC_60(MEDIUM_SECONDS),
    MIN_5(LONG_SECONDS),

    CUSTOM(null),
}

// A missing durationSec runs for the engine default, so the form shows that instead of an empty field.
fun durationChoiceOf(seconds: Int?): DurationChoice {
    val effective = seconds ?: DEFAULT_INSTANT_DURATION_SEC
    return DurationChoice.entries.firstOrNull { it.seconds == effective } ?: DurationChoice.CUSTOM
}

fun durationSecondsOf(choice: DurationChoice, customMinutes: String): Int? =
    choice.seconds ?: minutesToSeconds(customMinutes)

// The pattern rejects exponents ("1e100000000" would expand into a huge integer) and non-ASCII digits.
private fun minutesToSeconds(text: String): Int? =
    text.trim().takeIf { MINUTES_PATTERN.matches(it) }
        ?.replace(',', '.')
        ?.toBigDecimal()
        ?.multiply(SECONDS_PER_MINUTE.toBigDecimal())
        ?.setScale(0, RoundingMode.HALF_UP)
        ?.toLong()
        ?.takeIf { it in 1..Int.MAX_VALUE }
        ?.toInt()

// Imported scenarios may hold durations that are not whole minutes; two decimals round-trip them.
fun customMinutesOf(seconds: Int?, decimalSeparator: Char): String =
    (seconds ?: DEFAULT_INSTANT_DURATION_SEC).toBigDecimal()
        .divide(SECONDS_PER_MINUTE.toBigDecimal(), 2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
        .replace('.', decimalSeparator)

fun durationError(choice: DurationChoice, customMinutes: String): ConditionFormError? =
    if (choice == DurationChoice.CUSTOM && durationSecondsOf(choice, customMinutes) == null) {
        ConditionFormError.DURATION
    } else {
        null
    }

fun withDuration(condition: Condition, seconds: Int?): Condition = when (condition) {
    is Condition.TimeInstant -> condition.copy(durationSec = seconds)
    is Condition.Alarm -> condition.copy(durationSec = seconds)
    is Condition.BtDisconnected -> condition.copy(durationSec = seconds)
    is Condition.NotificationFrom -> condition.copy(durationSec = seconds)
    is Condition.HaEvent -> condition.copy(durationSec = seconds)
    is Condition.NotificationEnded -> condition.copy(durationSec = seconds)
    is Condition.PlaceSaved -> condition.copy(durationSec = seconds)
    else -> condition
}

private val MINUTES_PATTERN = Regex("""\d{1,8}(?:[.,]\d{0,2})?|[.,]\d{1,2}""")

private const val SHORT_SECONDS = 30
private const val MEDIUM_SECONDS = 60
private const val LONG_SECONDS = 300
private const val SECONDS_PER_MINUTE = 60
