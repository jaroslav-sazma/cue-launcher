package app.cue.launcher.data.scenario

// Machine-facing English (correction feedback for a model); the user sees defectText() instead.
@Suppress("CyclomaticComplexMethod")
fun ScenarioDefect.description(): String = when (this) {
    is ScenarioDefect.UnsupportedSchemaVersion ->
        "schemaVersion is $found, only $supported is supported."

    ScenarioDefect.Malformed.NotJsonObject -> "The document is not a JSON object."

    ScenarioDefect.Malformed.ScenariosNotList -> "The \"scenarios\" field is not a JSON array."

    ScenarioDefect.Malformed.ConfigUnreadable -> "The launcher configuration fields cannot be read."

    ScenarioDefect.Malformed.ScenarioNotObject -> "A scenario entry is not a JSON object."

    is ScenarioDefect.Malformed.UnknownVersion -> "Unknown scenario version $version."

    is ScenarioDefect.Malformed.Unreadable ->
        detail?.let { "The scenario JSON cannot be read: $it" } ?: "The scenario JSON cannot be read."

    is ScenarioDefect.UnknownEnum -> "Field \"$field\" has unknown value \"$value\"."

    is ScenarioDefect.MissingField -> "Required field \"$field\" is missing."

    ScenarioDefect.OverlayUnsupported -> "Size \"${TileSize.OVERLAY.jsonName}\" is not supported."

    is ScenarioDefect.BlankId -> "Scenario \"$name\" has a blank \"id\"."

    is ScenarioDefect.NegativePriority -> "\"priority\" must be 0 or more, got $priority."

    is ScenarioDefect.PinnedPositionOutOfRange ->
        "\"pinnedPosition\" $position does not fit size \"${size.jsonName}\"."

    is ScenarioDefect.MultipleInstantConditions ->
        "With match \"all\" at most one instant condition is allowed, got $count."

    is ScenarioDefect.DurationOnStateCondition ->
        "\"durationSec\" is only allowed on instant conditions, not on \"$type\"."

    is ScenarioDefect.UntilOnStateCondition ->
        "\"until\" is only allowed on instant conditions, not on \"$type\"."

    is ScenarioDefect.InvalidDuration -> "\"durationSec\" must be positive, got $durationSec."

    is ScenarioDefect.LayoutSizeMismatch ->
        "Layout \"${layout.jsonName}\" needs size \"${layout.size.jsonName}\", got \"${size.jsonName}\"."

    is ScenarioDefect.ItemCountMismatch ->
        "Layout \"${layout.jsonName}\" needs exactly $expected items, got $actual."

    is ScenarioDefect.WidgetSizeMismatch -> widget.size
        ?.let { "Widget \"${widget.jsonName}\" needs size \"${it.jsonName}\", got \"${size.jsonName}\"." }
        ?: "Widget \"${widget.jsonName}\" cannot be a scenario tile of size \"${size.jsonName}\"."

    is ScenarioDefect.InvalidTime -> "\"$field\" must be HH:mm (00:00-23:59), got \"$value\"."

    is ScenarioDefect.InvalidDays -> "\"days\" must contain only 1-7 (1 = Monday), got $days."

    is ScenarioDefect.TooManyExtractRules -> "\"extract\" allows at most $max rules, got $count."

    is ScenarioDefect.InvalidExtractRule -> extractDescription()

    is ScenarioDefect.InvalidHaEntity ->
        "\"$entity\" is not a Home Assistant entity id; write domain.object_id, like media_player.living_room."
}

private fun ScenarioDefect.InvalidExtractRule.extractDescription(): String = when (problem) {
    ExtractProblem.NAME ->
        "Extract rule name \"$name\" must be 1-$EXTRACT_NAME_MAX_LENGTH characters of a-z, 0-9 and _."
    ExtractProblem.RESERVED_NAME -> "Extract rule name \"$name\" is reserved."
    ExtractProblem.DUPLICATE_NAME -> "Extract rule name \"$name\" is used more than once."
    ExtractProblem.MISSING_UNTIL ->
        "Extract rule \"$name\" has kind \"${ExtractKind.UNTIL.jsonName}\" but no \"until\" marker."
    ExtractProblem.ANCHOR_TOO_LONG ->
        "Extract rule \"$name\": \"after\" is longer than $EXTRACT_MARKER_MAX_LENGTH characters."
    ExtractProblem.MARKER_TOO_LONG ->
        "Extract rule \"$name\": \"until\" is longer than $EXTRACT_MARKER_MAX_LENGTH characters."
}
