package app.cue.launcher.feature.home

import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import app.cue.launcher.R
import app.cue.launcher.data.layout.folderPlusCount
import app.cue.launcher.data.layout.folderPreviewCount
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.designsystem.AppIcon
import app.cue.launcher.designsystem.FolderChip
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.IconLabel
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.LocalTintedIcons
import app.cue.launcher.designsystem.TileSurface
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.designsystem.labelOnZoneStyle
import app.cue.launcher.designsystem.menuAnchorBounds
import app.cue.launcher.designsystem.wallpaperBlurRegion
import app.cue.launcher.engine.BentoLayout
import app.cue.launcher.engine.BentoSlot
import app.cue.launcher.engine.QueueEntry
import app.cue.launcher.feature.icons.rememberTintedPackageIcon
import app.cue.launcher.feature.widgets.BentoWidgetFrame
import app.cue.launcher.feature.widgets.bentoTileRadius
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.toSourceBounds

@Composable
fun BentoZone(
    layout: BentoLayout,
    widgets: WidgetHosting,
    launcherApps: LauncherAppsRepository,
    actions: TileActions,
    overlay: HomeOverlayState,
    modifier: Modifier = Modifier,
    bands: HomeBands = LocalHomeBands.current,
    stackHint: StackHint = StackHint.NONE,
) {
    val hintSlot = remember(layout) { (layout.hero + layout.medium).firstOrNull { it.hasOverflow } }
    val hintFor = { slot: BentoSlot -> if (slot == hintSlot) stackHint else stackHint.hidden() }
    TileSurface(
        modifier = modifier
            .fillMaxWidth()

            .padding(horizontal = Tokens.Grid.ZoneBleed.start)

            .wallpaperBlurRegion(key = "bento", cornerRadius = Tokens.Radius.zoneBento),
        level = GlassLevel.THIN,
        radius = Tokens.Radius.zoneBento,
        contentPadding = Tokens.Padding.zone,
        shadow = Tokens.Elevation.zone,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Grid.gutter)) {
            layout.hero.forEach { slot ->
                BentoTile(
                    slot = slot,
                    widgets = widgets,
                    launcherApps = launcherApps,
                    actions = actions,
                    overlay = overlay,
                    hint = hintFor(slot),
                    modifier = Modifier.fillMaxWidth().height(bands.heroHeight),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Grid.gutter)) {
                layout.medium.forEach { slot ->
                    BentoTile(
                        slot = slot,
                        widgets = widgets,
                        launcherApps = launcherApps,
                        actions = actions,
                        overlay = overlay,
                        hint = hintFor(slot),
                        modifier = Modifier.weight(1f).height(bands.mediumHeight),
                    )
                }
            }
            Row(
                modifier = Modifier.height(Tokens.Grid.RowHeight.static),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Grid.gutter),
            ) {
                layout.small.forEach { slot ->
                    BentoChip(
                        slot = slot,
                        widgets = widgets,
                        launcherApps = launcherApps,
                        actions = actions,
                        overlay = overlay,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BentoTile(
    slot: BentoSlot,
    widgets: WidgetHosting,
    launcherApps: LauncherAppsRepository,
    actions: TileActions,
    overlay: HomeOverlayState,
    hint: StackHint,
    modifier: Modifier = Modifier,
) {
    val pages = slot.stackPages
    val context = LocalContext.current
    when {

        pages.isEmpty() -> Box(modifier = modifier)

        pages.size == 1 -> BentoTileContent(
            entry = pages.single(),
            size = slot.size,
            widgets = widgets,
            launcherApps = launcherApps,
            actions = actions,
            overlay = overlay,
            modifier = modifier,
        )

        else -> TileStack(
            pages = pages,
            hint = hint,
            modifier = modifier,

            // What the stack's swipe has to get past when the page it shows is a widget.
            hostedWidget = { entry -> hostedWidgetOf(entry, context, widgets) },
        ) { entry ->
            BentoTileContent(
                entry = entry,
                size = slot.size,
                widgets = widgets,
                launcherApps = launcherApps,
                actions = actions,
                overlay = overlay,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BentoTileContent(
    entry: QueueEntry,
    size: TileSize,
    widgets: WidgetHosting,
    launcherApps: LauncherAppsRepository,
    actions: TileActions,
    overlay: HomeOverlayState,
    modifier: Modifier = Modifier,
) {
    val scenario = entry.scenario
    val radius = bentoTileRadius(size)
    val widgetId = (scenario.content as? TileContent.AppWidget)?.boundAppWidgetId
    if (widgetId != null) {

        BentoWidgetFrame(appWidgetId = widgetId, widgets = widgets, radius = radius, modifier = modifier)
        return
    }
    val template = scenario.content as? TileContent.Template
    val launcherWidget = (scenario.content as? TileContent.LauncherWidget)?.widget
    val tileActions = template?.wholeTileActions.orEmpty()
    var bounds by remember(scenario.id) { mutableStateOf<Rect?>(null) }
    var anchorBounds by remember(scenario.id) { mutableStateOf(IntRect.Zero) }
    var menuOpen by rememberSaveable(scenario.id) { mutableStateOf(false) }
    var replyOpen by rememberSaveable(scenario.id) { mutableStateOf(false) }
    val reply = entry.payload.reply
    val configure = rememberLauncherWidgetConfigure(launcherWidget, overlay)
    BentoTileSurface(
        launcherWidget = launcherWidget,
        radius = radius,
        modifier = modifier
            .onGloballyPositioned {
                bounds = it.boundsInWindow().toSourceBounds()
                anchorBounds = it.menuAnchorBounds()
            }
            .tileGestures(
                onClick = actions.tileClick(launcherWidget, tileActions) { bounds },

                onLongClick = { menuOpen = true }
                    .takeIf { entry.isClosable || reply != null || configure != null },
                longClickLabel = stringResource(R.string.tile_menu),
            ),
    ) {
        when {
            template != null -> TemplateTileContent(
                template = template,
                launcherApps = launcherApps,
                actions = actions,

                payload = entry.payload,
                accented = entry.accentedEntities,
                onReply = { replyOpen = true }.takeIf { reply != null },
            )

            launcherWidget != null -> LauncherWidgetContent(widget = launcherWidget)

            else -> TileContentPlaceholder(scenario)
        }
        TileMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            bounds = anchorBounds,
            onClose = { actions.close(scenario.id) }.takeIf { entry.isClosable },
            onReply = { replyOpen = true }.takeIf { reply != null },
            onConfigureWidget = configure,
        )
    }
    TileReplyDialog(entry = entry, actions = actions, open = replyOpen, onOpenChange = { replyOpen = it })
}

@Composable
private fun BentoTileSurface(
    launcherWidget: LauncherWidgetType?,
    radius: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (launcherWidget == null) {
        TileSurface(
            modifier = modifier,
            radius = radius,
            contentPadding = Tokens.Padding.tile,
            content = content,
        )
    } else {
        LauncherWidgetFrame(
            radius = radius,
            modifier = modifier,
            content = content,
        )
    }
}

private fun Modifier.tileGestures(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    longClickLabel: String,
): Modifier = if (onClick == null && onLongClick == null) {
    this
} else {
    this.combinedClickable(
        onClick = onClick ?: {},
        onLongClick = onLongClick,
        onLongClickLabel = longClickLabel.takeIf { onLongClick != null },
    )
}

@Composable
private fun BentoChip(
    slot: BentoSlot,
    widgets: WidgetHosting,
    launcherApps: LauncherAppsRepository,
    actions: TileActions,
    overlay: HomeOverlayState,
    modifier: Modifier = Modifier,
) {
    val entry = slot.entry
    var anchorBounds by remember(slot.entry?.scenario?.id) { mutableStateOf(IntRect.Zero) }
    Column(
        modifier = modifier.onGloballyPositioned { anchorBounds = it.menuAnchorBounds() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (entry == null) {

            Spacer(modifier = Modifier.size(Tokens.Size.icon))
            return@Column
        }
        if (slot.hasOverflow) {
            DynamicFolderChip(
                pages = slot.stackPages,
                launcherApps = launcherApps,
                overlay = overlay,
            )
            return@Column
        }
        var menuOpen by rememberSaveable(entry.scenario.id) { mutableStateOf(false) }
        var replyOpen by rememberSaveable(entry.scenario.id) { mutableStateOf(false) }
        val reply = entry.payload.reply
        BentoChipIcon(
            entry = entry,
            widgets = widgets,
            launcherApps = launcherApps,
            actions = actions,

            onLongClick = if (entry.isClosable || reply != null) ({ menuOpen = true }) else null,
        )
        IconLabel(text = entry.scenario.tileLabel(), style = labelOnZoneStyle())
        TileMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            bounds = anchorBounds,
            onClose = { actions.close(entry.scenario.id) }.takeIf { entry.isClosable },
            onReply = { replyOpen = true }.takeIf { reply != null },
        )
        TileReplyDialog(entry = entry, actions = actions, open = replyOpen, onOpenChange = { replyOpen = it })
    }
}

@Composable
private fun BentoChipIcon(
    entry: QueueEntry,
    widgets: WidgetHosting,
    launcherApps: LauncherAppsRepository,
    actions: TileActions,
    onLongClick: (() -> Unit)?,
) {
    val widgetId = (entry.scenario.content as? TileContent.AppWidget)?.boundAppWidgetId
    val item = entry.scenario.templateItem()
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
            actions = actions,
            level = GlassLevel.REGULAR,

            glyphTint = templateIconTint(item, entry.payload.semantic, entry.accentedEntities),
            contentDescription = entry.scenario.tileLabel(),
            onLongClick = onLongClick,
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
private fun DynamicFolderChip(
    pages: List<QueueEntry>,
    launcherApps: LauncherAppsRepository,
    overlay: HomeOverlayState,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.folder_dynamic_title)
    val open = { overlay.open(HomeOverlayTarget.DynamicFolder(pages)) }
    LaunchedEffect(pages, overlay) {
        if (overlay.target is HomeOverlayTarget.DynamicFolder) open()
    }
    FolderChip(
        fieldCount = folderPreviewCount(pages.size),
        plusCount = folderPlusCount(pages.size),
        contentDescription = folderChipDescription(title, pages.size),
        onClick = open,
        modifier = modifier,
    ) { index ->
        FolderPreviewIcon(entry = pages[index], launcherApps = launcherApps)
    }
    IconLabel(text = title, style = labelOnZoneStyle())
}

@Composable
private fun FolderPreviewIcon(entry: QueueEntry, launcherApps: LauncherAppsRepository) {
    val item = entry.scenario.templateItem()
    val iconApp = remember(item) { item?.let(::templateIconApp) }
    val glyphTint = item
        ?.let { templateIconTint(it, entry.payload.semantic, entry.accentedEntities) }
        ?: LocalCueColors.current.textPrimary
    val tint = bentoIconTint(glyphTint)
    val icon = rememberTintedPackageIcon(iconApp, launcherApps, tint)
    if (icon != null) {
        CompositionLocalProvider(LocalTintedIcons provides true) {
            AppIcon(
                icon = icon,
                contentDescription = null,
                size = Tokens.Folder.glyphInField,
            )
        }
        return
    }
    GlyphIcon(
        name = item?.let(::templateGlyphName),
        size = Tokens.Folder.glyphInField,
        tint = glyphTint,
    )
}

@Composable
private fun TileContentPlaceholder(scenario: Scenario, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = scenario.name,
            color = LocalCueColors.current.textPrimary,
            style = Tokens.Type.tileTitle.toTextStyle(),
        )
    }
}
