package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.TileSurface
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.feature.home.LauncherWidgetContent
import app.cue.launcher.feature.home.LocalMediaFeed
import app.cue.launcher.feature.home.inert
import app.cue.launcher.feature.home.LauncherWidgetFrame
import app.cue.launcher.feature.home.TemplateItemIcon
import app.cue.launcher.feature.home.TemplateTileContent
import app.cue.launcher.feature.home.TileActions
import app.cue.launcher.feature.widgets.BentoWidgetFrame
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.feature.widgets.bentoTileRadius
import app.cue.launcher.system.LauncherAppsRepository

@Composable
internal fun TilePreview(
    size: TileSize,
    draft: ContentDraft,
    scenarioName: String,
    launcherApps: LauncherAppsRepository,
    widgets: WidgetHosting,
    modifier: Modifier = Modifier,
) {
    when (size) {
        TileSize.GRID_1X1 -> ChipRowPreview(draft, scenarioName, launcherApps, widgets, modifier)
        TileSize.GRID_2X2 -> MediumPreview(draft, launcherApps, widgets, modifier)
        TileSize.HERO_4X2 -> HeroPreview(draft, launcherApps, widgets, modifier)

        TileSize.OVERLAY -> Unit
    }
}

@Composable
private fun ChipRowPreview(
    draft: ContentDraft,
    scenarioName: String,
    launcherApps: LauncherAppsRepository,
    widgets: WidgetHosting,
    modifier: Modifier = Modifier,
) {
    PreviewZone(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Grid.gutter),
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ChipPreview(draft, scenarioName, launcherApps, widgets)
            }

            repeat(Tokens.Grid.COLUMNS - 1) {
                Spacer(modifier = Modifier.weight(1f).size(Tokens.Size.icon))
            }
        }
    }
}

@Composable
private fun ChipPreview(
    draft: ContentDraft,
    scenarioName: String,
    launcherApps: LauncherAppsRepository,
    widgets: WidgetHosting,
) {
    val item = remember(draft) { draft.items.firstOrNull()?.toItem() }
    val widgetId = draft.widget?.appWidgetId?.takeIf { draft.kind == ContentKind.APP_WIDGET }
    when {
        widgetId != null -> BentoWidgetFrame(
            appWidgetId = widgetId,
            widgets = widgets,
            radius = Tokens.Radius.icon,
            modifier = Modifier.size(Tokens.Size.icon),
        )

        item != null -> TemplateItemIcon(
            item = item,
            launcherApps = launcherApps,
            actions = inertActions,
            level = GlassLevel.REGULAR,
            glyphTint = LocalCueColors.current.textPrimary,
            contentDescription = scenarioName,
        )

        else -> TileSurface(
            modifier = Modifier.size(Tokens.Size.icon),
            radius = Tokens.Radius.icon,
            contentPadding = Tokens.Padding.chip,
            content = {},
        )
    }
}

@Composable
private fun MediumPreview(
    draft: ContentDraft,
    launcherApps: LauncherAppsRepository,
    widgets: WidgetHosting,
    modifier: Modifier = Modifier,
) {
    PreviewZone(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Grid.gutter)) {
            TilePreviewBody(
                size = TileSize.GRID_2X2,
                draft = draft,
                launcherApps = launcherApps,
                widgets = widgets,
                modifier = Modifier.weight(1f).height(Tokens.Grid.RowHeight.tile2x2),
            )
            NeighbourTile(modifier = Modifier.weight(1f).height(Tokens.Grid.RowHeight.tile2x2))
        }
    }
}

@Composable
private fun HeroPreview(
    draft: ContentDraft,
    launcherApps: LauncherAppsRepository,
    widgets: WidgetHosting,
    modifier: Modifier = Modifier,
) {
    PreviewZone(modifier = modifier) {
        TilePreviewBody(
            size = TileSize.HERO_4X2,
            draft = draft,
            launcherApps = launcherApps,
            widgets = widgets,
            modifier = Modifier.fillMaxWidth().height(Tokens.Grid.RowHeight.hero4x2),
        )
    }
}

@Composable
private fun TilePreviewBody(
    size: TileSize,
    draft: ContentDraft,
    launcherApps: LauncherAppsRepository,
    widgets: WidgetHosting,
    modifier: Modifier = Modifier,
) {
    val widgetId = draft.widget?.appWidgetId?.takeIf { draft.kind == ContentKind.APP_WIDGET }
    if (widgetId != null) {
        BentoWidgetFrame(
            appWidgetId = widgetId,
            widgets = widgets,
            radius = bentoTileRadius(size),
            modifier = modifier,
        )
        return
    }
    val template = remember(draft) { previewTemplateOf(draft) }
    val launcherWidget = draft.launcherWidget?.takeIf { draft.kind == ContentKind.LAUNCHER_WIDGET }

    if (launcherWidget != null) {
        LauncherWidgetFrame(
            radius = bentoTileRadius(size),
            modifier = modifier,
        ) {
            val feed = LocalMediaFeed.current
            CompositionLocalProvider(LocalMediaFeed provides remember(feed) { feed.inert() }) {
                LauncherWidgetContent(widget = launcherWidget)
            }
        }
        return
    }
    TileSurface(
        modifier = modifier,
        radius = bentoTileRadius(size),
        contentPadding = Tokens.Padding.tile,
    ) {
        if (template != null) {
            TemplateTileContent(
                template = template,
                launcherApps = launcherApps,
                actions = inertActions,
            )
        } else {
            PreviewCaption(stringResource(R.string.builder_preview_empty))
        }
    }
}

@Composable
private fun NeighbourTile(modifier: Modifier = Modifier) {
    TileSurface(
        modifier = modifier,
        radius = Tokens.Radius.tile2x2,
        contentPadding = Tokens.Padding.tile,
    ) {
        PreviewCaption(stringResource(R.string.builder_preview_neighbour))
    }
}

@Composable
private fun PreviewCaption(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = LocalCueColors.current.textSecondary,
            style = Tokens.Type.tileBody.toTextStyle(),
        )
    }
}

@Composable
private fun PreviewZone(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    TileSurface(
        modifier = modifier.fillMaxWidth(),
        level = GlassLevel.THIN,
        radius = Tokens.Radius.zoneBento,
        contentPadding = Tokens.Padding.zone,
        shadow = Tokens.Elevation.zone,
        content = { content() },
    )
}

@Suppress("ReturnCount")
private fun previewTemplateOf(draft: ContentDraft): TileContent.Template? {
    if (draft.kind != ContentKind.TEMPLATE) return null
    val layout = draft.layout ?: return null
    return TileContent.Template(
        layout = layout,
        text = draft.text.trim().ifEmpty { null },
        text2 = draft.text2.trim().ifEmpty { null },
        items = draft.items.mapNotNull { it.toItem() },
    )
}

private val inertActions = TileActions(perform = { _, _ -> }, close = {})
