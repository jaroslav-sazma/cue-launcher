package app.cue.launcher.feature.scenarios.builder

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileSize

@Composable
internal fun tileSizeGroupTitle(size: TileSize): String = stringResource(
    when (size) {
        TileSize.HERO_4X2 -> R.string.scenarios_group_hero
        TileSize.GRID_2X2 -> R.string.scenarios_group_medium
        TileSize.GRID_1X1 -> R.string.scenarios_group_small
        TileSize.OVERLAY -> R.string.builder_type_overlay
    },
)

@Composable
internal fun contentSummary(draft: ContentDraft, size: TileSize): String {
    val kind = contentKindLabel(draft.kind, size)
    return when (draft.kind) {
        ContentKind.TEMPLATE -> {
            val layout = draft.layout
            if (layout == null || layout == TemplateLayout.ICON_TEXT) {
                kind
            } else {
                stringResource(R.string.builder_summary_value, kind, templateLayoutLabel(layout))
            }
        }

        ContentKind.APP_WIDGET -> draft.widget
            ?.let { stringResource(R.string.builder_summary_value, it.appLabel, it.label) }
            ?: kind

        ContentKind.LAUNCHER_WIDGET -> draft.launcherWidget?.let { launcherWidgetLabel(it) } ?: kind
    }
}

@Composable
internal fun pinSummary(position: Int?): String = if (position == null) {
    stringResource(R.string.builder_summary_position_floating)
} else {
    stringResource(R.string.builder_summary_position_fixed, position + 1)
}

@Composable
internal fun priorityErrorText(error: PriorityFormError): String = stringResource(
    when (error) {
        PriorityFormError.EMPTY -> R.string.builder_error_priority_empty
        PriorityFormError.NOT_A_NUMBER -> R.string.builder_error_priority_number
        PriorityFormError.NEGATIVE -> R.string.builder_error_priority_negative
        PriorityFormError.TOO_LARGE -> R.string.builder_error_priority_large
    },
)
