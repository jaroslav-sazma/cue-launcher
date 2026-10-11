package app.cue.launcher.feature.home

import android.graphics.Rect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import app.cue.launcher.R
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.layout.LauncherItem
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.data.layout.folderPlusCount
import app.cue.launcher.data.layout.folderPreviewCount
import app.cue.launcher.designsystem.AppIcon
import app.cue.launcher.designsystem.BadgedIcon
import app.cue.launcher.designsystem.CueMenu
import app.cue.launcher.designsystem.CueMenuDestructiveItem
import app.cue.launcher.designsystem.CueMenuHeader
import app.cue.launcher.designsystem.CueMenuItem
import app.cue.launcher.designsystem.CueMenuRule
import app.cue.launcher.designsystem.DottedIcon
import app.cue.launcher.designsystem.FolderChip
import app.cue.launcher.designsystem.IconLabel
import app.cue.launcher.designsystem.MenuAnchor
import app.cue.launcher.designsystem.MenuDecoration
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.dropTargetOutline
import app.cue.launcher.designsystem.menuAnchorBounds
import app.cue.launcher.designsystem.menuLiftSource
import app.cue.launcher.designsystem.rememberMenuLiftLayer
import app.cue.launcher.designsystem.labelOnWallpaperStyle
import app.cue.launcher.feature.dragdrop.ShortcutDragState
import app.cue.launcher.feature.dragdrop.dragPayload
import app.cue.launcher.feature.dragdrop.dropSlot
import app.cue.launcher.feature.dragdrop.shortcutDragSource
import app.cue.launcher.feature.icons.IconOverrideMenuItem
import app.cue.launcher.feature.icons.notificationCountSemantics
import app.cue.launcher.feature.icons.rememberAppIcon
import app.cue.launcher.feature.icons.rememberNotificationCount
import app.cue.launcher.feature.icons.rememberShortcut
import app.cue.launcher.feature.lock.LauncherAction
import app.cue.launcher.feature.lock.LocalLayoutLocked
import app.cue.launcher.feature.widgets.StaticWidgetFrame
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.system.AppShortcut
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.toSourceBounds

@Composable
internal fun StaticSlotCell(
    slot: StaticSlot,
    launcherApps: LauncherAppsRepository,
    dragState: ShortcutDragState,
    showLabel: Boolean,
    actions: StaticSlotActions,
    modifier: Modifier = Modifier,
    widgets: WidgetHosting? = null,
    outlineWhenEmpty: Boolean = false,
) {
    val item = slot.item
    Column(
        modifier = modifier.dropSlot(
            state = dragState,
            container = slot.container,
            position = slot.position,
            item = item,
        ),

        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (item) {
            is LauncherItem.Shortcut -> ShortcutCell(
                slot = slot,
                shortcut = item,
                launcherApps = launcherApps,
                dragState = dragState,
                showLabel = showLabel,
                actions = actions,
            )

            is LauncherItem.Folder -> FolderCell(
                folder = item,
                launcherApps = launcherApps,
                dragState = dragState,
                showLabel = showLabel,
                actions = actions,
            )

            is LauncherItem.LauncherWidget -> LauncherWidgetCell(
                widget = item,
                dragState = dragState,
                showLabel = showLabel,
                actions = actions,
            )

            is LauncherItem.AppWidget -> AppWidgetCell(
                widget = item,
                widgets = widgets,
                dragState = dragState,
                actions = actions,
            )

            null -> EmptySlotCell(
                slot = slot,
                isDropTarget = dragState.isTarget(slot.container, slot.position),
                showLabel = showLabel,
                actions = actions,
                outlined = outlineWhenEmpty,
            )
        }
    }
}

@Composable
private fun ShortcutCell(
    slot: StaticSlot,
    shortcut: LauncherItem.Shortcut,
    launcherApps: LauncherAppsRepository,
    dragState: ShortcutDragState,
    showLabel: Boolean,
    actions: StaticSlotActions,
) {
    val component = shortcut.component
    val resolved = rememberShortcut(component, launcherApps)
    val label = resolved?.app?.label
    val locked = LocalLayoutLocked.current
    var iconBounds by remember(component) { mutableStateOf<Rect?>(null) }
    var anchorBounds by remember(component) { mutableStateOf(IntRect.Zero) }

    val shortcuts = rememberAppShortcuts(component, launcherApps, locked || dragState.menuItemId == shortcut.id)

    val hasLockedMenu = locked && !shortcuts.isNullOrEmpty()
    val notificationCount = rememberNotificationCount(component)

    BadgedIcon(count = notificationCount) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Tokens.Radius.icon))
                .dropTargetOutline(visible = dragState.isTarget(slot.container, slot.position))
                .shortcutDragSource(
                    payload = shortcut.dragPayload(),
                    icon = resolved?.icon,
                    label = label.orEmpty(),
                    onLockedLongPress = { dragState.menuItemId = shortcut.id }.takeIf { hasLockedMenu },
                )
                .clickable(onClickLabel = null) { actions.launch(component, iconBounds) }
                .notificationCountSemantics(notificationCount)
                .onGloballyPositioned { coordinates ->
                    iconBounds = coordinates.boundsInWindow().toSourceBounds()
                    anchorBounds = coordinates.menuAnchorBounds()
                },
        ) {
            AppIcon(icon = resolved?.icon, contentDescription = slot.accessibilityLabel(label))
        }
    }
    if (showLabel && label != null) IconLabel(text = label, style = labelOnWallpaperStyle())
    ItemMenu(
        item = shortcut,
        dragState = dragState,
        bounds = anchorBounds,

        width = Tokens.Menu.widthWithHeader,
        lift = { AppIcon(icon = resolved?.icon, contentDescription = null) },
        ready = shortcuts != null,
    ) { dismiss ->
        ShortcutMenuItems(
            shortcut = shortcut,
            label = label,
            icon = resolved?.icon,
            iconBounds = iconBounds,
            shortcuts = shortcuts.orEmpty(),
            launcherApps = launcherApps,
            actions = actions,
            dismiss = dismiss,
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun ColumnScope.ShortcutMenuItems(
    shortcut: LauncherItem.Shortcut,
    label: String?,
    icon: ImageBitmap?,
    iconBounds: Rect?,
    shortcuts: List<AppShortcut>,
    launcherApps: LauncherAppsRepository,
    actions: StaticSlotActions,
    dismiss: () -> Unit,
) {
    if (label != null) {
        CueMenuHeader(icon = icon, title = label)
        CueMenuRule()
    }

    val locked = LocalLayoutLocked.current
    val canMove = LauncherAction.ADD_TO_FOLDER.availableWhen(locked)
    AppShortcutMenuItems(
        shortcuts = shortcuts,
        launcherApps = launcherApps,
        sourceBounds = iconBounds,
        onDone = dismiss,
        trailingRule = canMove,
    )
    if (canMove) {
        CueMenuItem(
            label = stringResource(R.string.folder_move_to),
            glyph = GLYPH_MOVE_TO_FOLDER,
            onClick = {
                dismiss()
                actions.folders.moveInto(shortcut)
            },
        )
    }

    IconOverrideMenuItem(
        component = shortcut.component,
        launcherApps = launcherApps,
        onDone = dismiss,
    )
    if (LauncherAction.REMOVE_ITEM.availableWhen(locked)) {
        CueMenuDestructiveItem(label = stringResource(R.string.home_shortcut_remove)) {
            dismiss()
            actions.remove(shortcut)
        }
    }
}

@Composable
private fun FolderCell(
    folder: LauncherItem.Folder,
    launcherApps: LauncherAppsRepository,
    dragState: ShortcutDragState,
    showLabel: Boolean,
    actions: StaticSlotActions,
) {
    val isDropTarget = dragState.isTarget(folder.container, folder.position)
    val description = folderChipDescription(folder.name, folder.items.size)
    var anchorBounds by remember(folder.id) { mutableStateOf(IntRect.Zero) }

    val chip: @Composable (Modifier, () -> Unit) -> Unit = { modifier, onClick ->
        FolderChip(
            fieldCount = folderPreviewCount(folder.items.size),
            plusCount = folderPlusCount(folder.items.size),
            contentDescription = description,
            onClick = onClick,
            modifier = modifier,
        ) { index ->
            FolderChipIcon(component = folder.items[index], launcherApps = launcherApps)
        }
    }

    val notificationCount = rememberNotificationCount(folder.items)
    DottedIcon(count = notificationCount) {
        chip(
            Modifier
                .dropTargetOutline(visible = isDropTarget)
                .shortcutDragSource(
                    payload = folder.dragPayload(),
                    icon = null,
                    label = folder.name,

                    onLockedLongPress = null,
                )
                .notificationCountSemantics(notificationCount)
                .onGloballyPositioned { anchorBounds = it.menuAnchorBounds() },
        ) { actions.folders.open(folder) }
    }
    if (showLabel) IconLabel(text = folder.name, style = labelOnWallpaperStyle())
    ItemMenu(
        item = folder,
        dragState = dragState,
        bounds = anchorBounds,
        lift = { chip(Modifier) { } },
    ) { dismiss ->
        CueMenuItem(
            label = stringResource(R.string.folder_rename),
            glyph = GLYPH_RENAME,
            onClick = {
                dismiss()
                actions.folders.rename(folder)
            },
        )
        CueMenuDestructiveItem(label = stringResource(R.string.home_shortcut_remove)) {
            dismiss()
            actions.remove(folder)
        }
    }
}

@Composable
private fun LauncherWidgetCell(
    widget: LauncherItem.LauncherWidget,
    dragState: ShortcutDragState,
    showLabel: Boolean,
    actions: StaticSlotActions,
) {
    val label = stringResource(launcherWidgetLabelRes(widget.widget))
    var anchorBounds by remember(widget.id) { mutableStateOf(IntRect.Zero) }

    val lift = rememberMenuLiftLayer(dragState.menuItemId == widget.id)
    val locked = LocalLayoutLocked.current
    val configurable = launcherWidgetHasSettings(widget.widget) &&
        LauncherAction.CONFIGURE_WIDGET.availableWhen(locked)
    val drag = Modifier
        .shortcutDragSource(
            payload = widget.dragPayload(),
            icon = null,
            label = label,

            onLockedLongPress = { dragState.menuItemId = widget.id }.takeIf { configurable },
        )
        .menuLiftSource(lift)
        .onGloballyPositioned { anchorBounds = it.menuAnchorBounds() }
    LauncherWidgetCellContent(
        widget = widget.widget,
        // A dock is a blurring pane of its own already.
        blurRegionKey = "launcher_widget.${widget.id}".takeIf { widget.container == LayoutContainer.GRID },
        label = label,
        showLabel = showLabel,
        drag = drag,
        actions = actions,
    )

    ItemMenu(
        item = widget,
        dragState = dragState,
        bounds = anchorBounds,
        capture = lift?.let { MenuDecoration.Capture(layer = it) },
    ) { dismiss ->
        if (configurable) {
            CueMenuItem(
                label = stringResource(R.string.home_widget_configure),
                glyph = GLYPH_CONFIGURE,
                onClick = {
                    dismiss()
                    actions.configureLauncherWidget(widget.widget)
                },
            )
        }
        if (LauncherAction.REMOVE_ITEM.availableWhen(locked)) {
            CueMenuDestructiveItem(
                label = stringResource(R.string.home_shortcut_remove),
                separated = configurable,
            ) {
                dismiss()
                actions.remove(widget)
            }
        }
    }
}

@Composable
private fun AppWidgetCell(
    widget: LauncherItem.AppWidget,
    widgets: WidgetHosting?,
    dragState: ShortcutDragState,
    actions: StaticSlotActions,
) {
    var anchorBounds by remember(widget.id) { mutableStateOf(IntRect.Zero) }

    val lift = rememberMenuLiftLayer(dragState.menuItemId == widget.id)
    val locked = LocalLayoutLocked.current

    val configurable = remember(widgets, widget.appWidgetId, locked) {
        LauncherAction.CONFIGURE_WIDGET.availableWhen(locked) &&
            widgets?.canConfigure(widget.appWidgetId) == true
    }
    if (widgets != null) {
        StaticWidgetFrame(
            appWidgetId = widget.appWidgetId,
            widgets = widgets,
            modifier = Modifier
                .fillMaxSize()
                .shortcutDragSource(
                    payload = widget.dragPayload(),
                    icon = null,
                    label = stringResource(R.string.home_widget_menu),
                    onLockedLongPress = { dragState.menuItemId = widget.id }.takeIf { configurable },
                )
                .menuLiftSource(lift)
                .onGloballyPositioned { anchorBounds = it.menuAnchorBounds() },
        )
    }
    ItemMenu(
        item = widget,
        dragState = dragState,
        bounds = anchorBounds,
        capture = lift?.let { MenuDecoration.Capture(layer = it) },
    ) { dismiss ->
        if (configurable) {
            CueMenuItem(
                label = stringResource(R.string.home_widget_configure),
                glyph = GLYPH_CONFIGURE,
                onClick = {
                    dismiss()
                    actions.configureWidget(widget)
                },
            )
        }
        if (LauncherAction.REMOVE_ITEM.availableWhen(locked)) {
            CueMenuDestructiveItem(
                label = stringResource(R.string.home_shortcut_remove),
                separated = configurable,
            ) {
                dismiss()
                actions.remove(widget)
            }
        }
    }
}

@Composable
private fun FolderChipIcon(component: ComponentRef, launcherApps: LauncherAppsRepository) {
    val icon = rememberAppIcon(component, launcherApps)
    AppIcon(
        icon = icon,
        contentDescription = null,
        size = Tokens.Folder.glyphInField + Tokens.Folder.fieldGap,
    )
}

@Composable
private fun EmptySlotCell(
    slot: StaticSlot,
    isDropTarget: Boolean,
    showLabel: Boolean,
    actions: StaticSlotActions,
    outlined: Boolean,
) {
    val description = slot.accessibilityLabel(label = null)
    var menuOpen by rememberSaveable(slot.container, slot.position) { mutableStateOf(false) }
    var anchorBounds by remember(slot.container, slot.position) { mutableStateOf(IntRect.Zero) }
    val openSurfaceMenu = actions.openSurfaceMenu

    val addAllowed = LauncherAction.ADD_SHORTCUT.availableWhen(LocalLayoutLocked.current)
    val hasMenu = openSurfaceMenu != null || addAllowed
    Spacer(
        modifier = Modifier
            .size(Tokens.Size.icon)
            .dropTargetOutline(visible = isDropTarget, hinted = outlined && addAllowed)
            .combinedClickable(
                interactionSource = null,
                indication = null,
                enabled = hasMenu,
                onLongClickLabel = stringResource(R.string.home_slot_menu),
                onLongClick = { openSurfaceMenu?.invoke(slot) ?: run { menuOpen = true } },
                onClick = {},
            )
            .onGloballyPositioned { anchorBounds = it.menuAnchorBounds() }
            .semantics {
                contentDescription = description

                if (hasMenu) role = Role.Button
            },
    )
    if (showLabel) StaticLabelReserve()

    if (openSurfaceMenu == null && addAllowed) {
        CueMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },

            anchor = MenuAnchor(bounds = anchorBounds, decoration = MenuDecoration.Press),
        ) {
            CueMenuItem(
                label = stringResource(R.string.home_shortcut_add),
                glyph = GLYPH_ADD_APP,
                onClick = {
                    menuOpen = false
                    actions.addShortcut(slot.container, slot.position)
                },
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun ItemMenu(
    item: LauncherItem,
    dragState: ShortcutDragState,
    bounds: IntRect,
    width: Dp = Tokens.Menu.width,

    lift: (@Composable () -> Unit)? = null,

    capture: MenuDecoration.Capture? = null,

    ready: Boolean = true,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val dismiss = { dragState.menuItemId = null }
    CueMenu(
        expanded = dragState.menuItemId == item.id,
        onDismissRequest = dismiss,
        anchor = MenuAnchor(
            bounds = bounds,
            width = width,
            decoration = capture ?: lift?.let { MenuDecoration.Lift(content = it) },
        ),
        ready = ready,
    ) {
        content(dismiss)
    }
}

private const val GLYPH_MOVE_TO_FOLDER = "folder-move-outline"
private const val GLYPH_RENAME = "pencil-outline"
private const val GLYPH_CONFIGURE = "cog-outline"
private const val GLYPH_ADD_APP = "apps"

@Composable
private fun StaticSlot.accessibilityLabel(label: String?): String = when (container) {
    LayoutContainer.GRID -> when (label) {
        null -> stringResource(R.string.home_grid_position_empty, row + 1, column + 1)
        else -> stringResource(R.string.home_grid_position, label, row + 1, column + 1)
    }

    LayoutContainer.DOCK -> when (label) {
        null -> stringResource(R.string.home_dock_position_empty, column + 1)
        else -> stringResource(R.string.home_dock_position, label, column + 1)
    }

    LayoutContainer.PANEL_DOCK -> when (label) {
        null -> stringResource(R.string.panel_dock_position_empty, column + 1)
        else -> stringResource(R.string.panel_dock_position, label, column + 1)
    }
}
