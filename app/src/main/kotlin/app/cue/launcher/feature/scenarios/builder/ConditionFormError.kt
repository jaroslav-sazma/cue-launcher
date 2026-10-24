package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.ha.haEntityIdOrNull
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EXTRACT_MAX_RULES
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.ExtractProblem
import app.cue.launcher.data.scenario.WEEK_DAYS
import app.cue.launcher.data.scenario.WifiMode
import app.cue.launcher.data.scenario.extractProblems
import app.cue.launcher.data.scenario.tidyExtractRules
import app.cue.launcher.data.scenario.tidyHaEntity
import app.cue.launcher.data.scenario.tidyHaStates
import app.cue.launcher.data.scenario.tidyPackages
import app.cue.launcher.data.scenario.withUntil
import app.cue.launcher.engine.haEventCode
import app.cue.launcher.feature.scenarios.parseHourMinute

enum class ConditionFormError {

    WIFI_SSID,

    HA_CODE,

    HA_ENTITY,

    HA_STATES,

    NOTIFICATION_PACKAGE,

    NOTIFICATION_EXTRACT_COUNT,

    NOTIFICATION_EXTRACT_NAME,

    NOTIFICATION_EXTRACT_RESERVED,

    NOTIFICATION_EXTRACT_DUPLICATE,

    NOTIFICATION_EXTRACT_UNTIL,

    NOTIFICATION_EXTRACT_LENGTH,

    TIME,

    DAYS,

    DURATION,

    END_PACKAGE,

    END_HA_CODE,

    END_TIME,

    END_DAYS,
    ;

    companion object {
        fun of(problem: ExtractProblem): ConditionFormError = when (problem) {
            ExtractProblem.NAME -> NOTIFICATION_EXTRACT_NAME
            ExtractProblem.RESERVED_NAME -> NOTIFICATION_EXTRACT_RESERVED
            ExtractProblem.DUPLICATE_NAME -> NOTIFICATION_EXTRACT_DUPLICATE
            ExtractProblem.MISSING_UNTIL -> NOTIFICATION_EXTRACT_UNTIL
            ExtractProblem.ANCHOR_TOO_LONG, ExtractProblem.MARKER_TOO_LONG -> NOTIFICATION_EXTRACT_LENGTH
        }
    }
}

fun parseTimeOfDay(text: String): String? =
    parseHourMinute(text)?.let { (hour, minute) -> "%02d:%02d".format(hour, minute) }

@Suppress("CyclomaticComplexMethod")
fun conditionFormError(condition: Condition): ConditionFormError? = when (condition) {
    is Condition.TimeWindow -> timeError(condition.from, condition.to) ?: daysError(condition.days)
    is Condition.TimeInstant -> timeError(condition.at) ?: daysError(condition.days)
    is Condition.Wifi -> ssidError(condition)
    is Condition.HaEvent -> ConditionFormError.HA_CODE.takeIf { condition.code.isBlank() }
    is Condition.HaState -> when {
        haEntityIdOrNull(condition.entity) == null -> ConditionFormError.HA_ENTITY
        tidyHaStates(condition.states).isEmpty() -> ConditionFormError.HA_STATES
        else -> null
    }

    is Condition.NotificationFrom -> when {
        condition.packageName.isBlank() -> ConditionFormError.NOTIFICATION_PACKAGE
        condition.extract.size > EXTRACT_MAX_RULES -> ConditionFormError.NOTIFICATION_EXTRACT_COUNT
        else -> extractProblems(condition.extract).firstOrNull()?.let { (_, problem) -> ConditionFormError.of(problem) }
    }

    is Condition.NotificationEnded ->
        ConditionFormError.NOTIFICATION_PACKAGE.takeIf { condition.packageName.isBlank() }

    else -> null
}

fun endEventError(end: EndEvent): ConditionFormError? = when (end) {
    is EndEvent.AtTime -> when {
        parseTimeOfDay(end.at) == null -> ConditionFormError.END_TIME
        end.days.none { it in WEEK_DAYS } -> ConditionFormError.END_DAYS
        else -> null
    }

    is EndEvent.Notification -> ConditionFormError.END_PACKAGE.takeIf { end.packageName.isBlank() }
    is EndEvent.HaEvent -> ConditionFormError.END_HA_CODE.takeIf { end.code.isBlank() }
    EndEvent.Alarm, is EndEvent.BtDisconnected -> null
}

fun canonicalCondition(condition: Condition): Condition {
    val canonical = canonicalTrigger(condition)
    val until = (canonical as? Condition.InstantCondition)?.until
    return if (canonical is Condition.InstantCondition && until != null) {
        canonical.withUntil(canonicalEndEvent(until))
    } else {
        canonical
    }
}

private fun canonicalEndEvent(end: EndEvent): EndEvent = when (end) {
    is EndEvent.AtTime -> end.copy(
        at = parseTimeOfDay(end.at) ?: end.at,
        days = end.days.filter { it in WEEK_DAYS }.distinct().sorted(),
    )

    is EndEvent.Notification -> end.copy(
        packageName = end.packageName.trim(),
        contains = end.contains?.trim()?.ifEmpty { null },
    )

    is EndEvent.HaEvent -> end.copy(code = haEventCode(end.code).orEmpty())
    EndEvent.Alarm, is EndEvent.BtDisconnected -> end
}

private fun canonicalTrigger(condition: Condition): Condition = when (condition) {
    is Condition.TimeWindow -> condition.copy(
        from = parseTimeOfDay(condition.from) ?: condition.from,
        to = parseTimeOfDay(condition.to) ?: condition.to,
        days = condition.days.filter { it in WEEK_DAYS }.distinct().sorted(),
    )

    is Condition.TimeInstant -> condition.copy(
        at = parseTimeOfDay(condition.at) ?: condition.at,
        days = condition.days.filter { it in WEEK_DAYS }.distinct().sorted(),
    )

    is Condition.Wifi -> condition.copy(

        ssid = if (condition.mode == WifiMode.SSID) condition.ssid?.trim() else null,
    )

    is Condition.HaEvent -> condition.copy(code = haEventCode(condition.code).orEmpty())
    is Condition.MediaPlaying -> condition.copy(packages = tidyPackages(condition.packages))
    is Condition.HaState -> condition.copy(
        entity = tidyHaEntity(condition.entity),
        states = tidyHaStates(condition.states),
    )

    is Condition.NotificationFrom -> condition.copy(
        packageName = condition.packageName.trim(),
        contains = condition.contains?.trim()?.ifEmpty { null },
        extract = tidyExtractRules(condition.extract),
    )

    is Condition.NotificationEnded -> condition.copy(packageName = condition.packageName.trim())

    else -> condition
}

private fun ssidError(condition: Condition.Wifi): ConditionFormError? =
    ConditionFormError.WIFI_SSID.takeIf {
        condition.mode == WifiMode.SSID && condition.ssid.isNullOrBlank()
    }

private fun timeError(vararg values: String): ConditionFormError? =
    ConditionFormError.TIME.takeIf { values.any { parseTimeOfDay(it) == null } }

private fun daysError(days: List<Int>): ConditionFormError? =
    ConditionFormError.DAYS.takeIf { days.none { it in WEEK_DAYS } }
