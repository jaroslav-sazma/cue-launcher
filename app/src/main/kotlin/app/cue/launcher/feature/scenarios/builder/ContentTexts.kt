package app.cue.launcher.feature.scenarios.builder

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.designsystem.glyph.mdiIconReference
import app.cue.launcher.feature.scenarios.AppJoiner
import app.cue.launcher.feature.scenarios.appListText

@Composable
internal fun tileTypeTitle(size: TileSize): String = stringResource(
    when (size) {
        TileSize.HERO_4X2 -> R.string.builder_type_hero
        TileSize.GRID_2X2 -> R.string.builder_type_medium
        TileSize.GRID_1X1 -> R.string.builder_type_small
        TileSize.OVERLAY -> R.string.builder_type_overlay
    },
)

@Composable
internal fun tileTypeDetail(size: TileSize): String = stringResource(
    when (size) {
        TileSize.HERO_4X2 -> R.string.builder_type_hero_detail
        TileSize.GRID_2X2 -> R.string.builder_type_medium_detail
        TileSize.GRID_1X1 -> R.string.builder_type_small_detail
        TileSize.OVERLAY -> R.string.builder_type_overlay_detail
    },
)

@Composable
internal fun tileSizeBadge(size: TileSize): String = stringResource(
    when (size) {
        TileSize.HERO_4X2 -> R.string.tile_size_4x2
        TileSize.GRID_2X2 -> R.string.tile_size_2x2
        TileSize.GRID_1X1 -> R.string.tile_size_1x1
        TileSize.OVERLAY -> R.string.tile_size_overlay
    },
)

@Composable
internal fun contentKindLabel(kind: ContentKind, size: TileSize): String = when (kind) {
    ContentKind.TEMPLATE -> stringResource(
        if (size == TileSize.GRID_1X1) R.string.builder_kind_icon_action else R.string.builder_kind_template,
    )

    ContentKind.APP_WIDGET -> if (size == TileSize.HERO_4X2) {
        stringResource(R.string.builder_kind_app_widget)
    } else {
        stringResource(R.string.builder_kind_widget_sized, tileSizeBadge(size))
    }

    ContentKind.LAUNCHER_WIDGET -> stringResource(R.string.builder_kind_launcher_widget)
}

@Composable
internal fun contentKindDetail(draft: ContentDraft, kind: ContentKind): String? = when (kind) {
    ContentKind.LAUNCHER_WIDGET -> draft.launcherWidget?.let { launcherWidgetDetail(it) }
    ContentKind.APP_WIDGET -> draft.widget?.let {
        stringResource(R.string.builder_summary_value, it.appLabel, it.label)
    }
    ContentKind.TEMPLATE -> null
}

@Composable
internal fun templateLayoutLabel(layout: TemplateLayout): String = stringResource(
    when (layout) {
        TemplateLayout.ICON_TEXT -> R.string.builder_layout_icon_text
        TemplateLayout.TEXT_ONLY -> R.string.builder_layout_text_only
        TemplateLayout.TEXT_TWO_ICONS -> R.string.builder_layout_text_two_icons
        TemplateLayout.FOUR_ICONS -> R.string.builder_layout_four_icons
    },
)

@Composable
internal fun templateItemsSectionTitle(layout: TemplateLayout): String = stringResource(
    if (layout == TemplateLayout.FOUR_ICONS) R.string.builder_items_four else R.string.builder_items_two,
)

@Composable
internal fun actionSummary(action: TapAction?, actionLabel: String? = null): String = when (action) {
    null -> stringResource(R.string.builder_action_empty)
    // An action read from a saved or imported scenario carries no label of its own.
    is TapAction.App -> stringResource(
        R.string.builder_action_app,
        actionLabel ?: appListText(listOf(action.packageName), AppJoiner.OR),
    )

    is TapAction.Shortcut -> stringResource(
        R.string.builder_action_shortcut,
        actionLabel ?: appListText(listOf(action.packageName), AppJoiner.OR),
    )

    is TapAction.DeepLink -> stringResource(R.string.builder_action_deeplink, actionLabel ?: action.uri)
    is TapAction.Capability ->
        stringResource(R.string.builder_action_capability, actionLabel ?: action.capability)

    is TapAction.ActivityLaunch ->
        stringResource(R.string.builder_action_activity, actionLabel ?: action.component)

    is TapAction.SystemAction ->
        stringResource(R.string.builder_action_system, systemActionLabel(action.action))

    is TapAction.HaAction -> stringResource(R.string.builder_action_ha, action.service)
}

@Composable
internal fun iconSummary(item: TemplateItemDraft): String {
    val glyph = previewGlyphName(item)
    return when {
        glyph != null -> mdiIconReference(glyph)
        previewIconPackage(item) != null -> stringResource(R.string.builder_icon_app)
        else -> stringResource(R.string.builder_icon_none)
    }
}

@Composable
internal fun contentFormErrorText(
    error: ContentFormError,
    draft: ContentDraft,
    size: TileSize,
): String = when (error) {
    ContentFormError.NO_WIDGET -> stringResource(R.string.builder_error_no_widget, tileSizeBadge(size))
    ContentFormError.NO_LAUNCHER_WIDGET -> stringResource(R.string.builder_error_no_launcher_widget)
    ContentFormError.NO_LAYOUT -> stringResource(R.string.builder_error_no_layout)
    ContentFormError.MISSING_ACTIONS -> stringResource(
        if ((draft.layout?.itemCount ?: 1) > 1) {
            R.string.builder_error_missing_actions
        } else {
            R.string.builder_error_missing_action
        },
    )

    ContentFormError.MISSING_TEXT -> stringResource(R.string.builder_error_missing_text)
}
