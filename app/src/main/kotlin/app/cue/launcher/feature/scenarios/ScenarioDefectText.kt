package app.cue.launcher.feature.scenarios

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.EXTRACT_MARKER_MAX_LENGTH
import app.cue.launcher.data.scenario.EXTRACT_NAME_MAX_LENGTH
import app.cue.launcher.data.scenario.ExtractProblem
import app.cue.launcher.data.scenario.ScenarioDefect

@Composable
@Suppress("CyclomaticComplexMethod")
fun defectText(defect: ScenarioDefect): String = when (defect) {
    is ScenarioDefect.UnsupportedSchemaVersion ->
        stringResource(R.string.defect_unsupported_version, defect.found, defect.supported)

    ScenarioDefect.Malformed.NotJsonObject -> stringResource(R.string.defect_not_json_object)

    ScenarioDefect.Malformed.ScenariosNotList -> stringResource(R.string.defect_scenarios_not_list)

    ScenarioDefect.Malformed.ConfigUnreadable -> stringResource(R.string.defect_config_unreadable)

    ScenarioDefect.Malformed.ScenarioNotObject -> stringResource(R.string.defect_scenario_not_object)

    is ScenarioDefect.Malformed.UnknownVersion ->
        stringResource(R.string.defect_unknown_version, defect.version)

    is ScenarioDefect.Malformed.Unreadable -> defect.detail
        ?.let { stringResource(R.string.defect_malformed, it) }
        ?: stringResource(R.string.defect_unreadable)

    is ScenarioDefect.UnknownEnum ->
        stringResource(R.string.defect_unknown_enum, defect.value, defect.field)

    is ScenarioDefect.MissingField -> stringResource(R.string.defect_missing_field, defect.field)

    ScenarioDefect.OverlayUnsupported -> stringResource(R.string.defect_overlay)

    is ScenarioDefect.BlankId -> stringResource(R.string.defect_blank_id)

    is ScenarioDefect.NegativePriority ->
        stringResource(R.string.defect_negative_priority, defect.priority)

    is ScenarioDefect.PinnedPositionOutOfRange ->
        stringResource(R.string.defect_pinned_position, defect.position + 1, defect.size.jsonName)

    is ScenarioDefect.MultipleInstantConditions ->
        stringResource(R.string.defect_multiple_instants, defect.count)

    is ScenarioDefect.DurationOnStateCondition ->
        stringResource(R.string.defect_duration_on_state, defect.type)

    is ScenarioDefect.UntilOnStateCondition ->
        stringResource(R.string.defect_until_on_state, defect.type)

    is ScenarioDefect.InvalidDuration ->
        stringResource(R.string.defect_invalid_duration, defect.durationSec)

    is ScenarioDefect.LayoutSizeMismatch ->
        stringResource(R.string.defect_layout_size, defect.layout.jsonName, defect.size.jsonName)

    is ScenarioDefect.ItemCountMismatch ->
        stringResource(R.string.defect_item_count, defect.layout.jsonName, defect.expected, defect.actual)

    is ScenarioDefect.WidgetSizeMismatch ->
        stringResource(R.string.defect_widget_size, defect.widget.jsonName, defect.size.jsonName)

    is ScenarioDefect.InvalidTime ->
        stringResource(R.string.defect_invalid_time, defect.field, defect.value)

    is ScenarioDefect.InvalidDays ->
        stringResource(R.string.defect_invalid_days, defect.days.joinToString())

    is ScenarioDefect.TooManyExtractRules -> stringResource(R.string.defect_extract_too_many)

    is ScenarioDefect.InvalidExtractRule -> extractDefectText(defect)

    is ScenarioDefect.InvalidHaEntity -> stringResource(R.string.defect_ha_entity, defect.entity)
}

@Composable
private fun extractDefectText(defect: ScenarioDefect.InvalidExtractRule): String = when (defect.problem) {
    ExtractProblem.NAME -> stringResource(R.string.defect_extract_name, defect.name, EXTRACT_NAME_MAX_LENGTH)
    ExtractProblem.RESERVED_NAME -> stringResource(R.string.defect_extract_reserved, defect.name)
    ExtractProblem.DUPLICATE_NAME -> stringResource(R.string.defect_extract_duplicate, defect.name)
    ExtractProblem.MISSING_UNTIL -> stringResource(R.string.defect_extract_until, defect.name)
    ExtractProblem.ANCHOR_TOO_LONG ->
        stringResource(R.string.defect_extract_anchor_length, defect.name, EXTRACT_MARKER_MAX_LENGTH)
    ExtractProblem.MARKER_TOO_LONG ->
        stringResource(R.string.defect_extract_marker_length, defect.name, EXTRACT_MARKER_MAX_LENGTH)
}
