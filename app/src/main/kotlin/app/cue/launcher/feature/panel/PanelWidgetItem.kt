package app.cue.launcher.feature.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.zIndex
import app.cue.launcher.R
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.widgets.PanelItem
import app.cue.launcher.data.widgets.PanelItemKey
import app.cue.launcher.data.widgets.WIDGET_MOVE_DOWN
import app.cue.launcher.data.widgets.WIDGET_MOVE_UP
import app.cue.launcher.designsystem.CueMenu
import app.cue.launcher.designsystem.CueMenuDestructiveItem
import app.cue.launcher.designsystem.CueMenuItem
import app.cue.launcher.designsystem.MenuAnchor
import app.cue.launcher.designsystem.MenuDecoration
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.menuAnchorBounds
import app.cue.launcher.designsystem.menuLiftSource
import app.cue.launcher.designsystem.rememberMenuLiftLayer
import app.cue.launcher.feature.home.LauncherWidgetContent
import app.cue.launcher.feature.home.LauncherWidgetFrame
import app.cue.launcher.feature.home.SearchbarTile
import app.cue.launcher.feature.home.TileActions
import app.cue.launcher.feature.home.launcherWidgetHasSettings
import app.cue.launcher.feature.home.launcherWidgetLabelRes
import app.cue.launcher.feature.lock.LauncherAction
import app.cue.launcher.feature.lock.LocalLayoutLocked
import app.cue.launcher.feature.widgets.WidgetFrame
import app.cue.launcher.feature.widgets.WidgetHosting

@Composable
@Suppress("LongParameterList")
fun PanelWidgetItem(
    item: PanelItem,
    widgets: WidgetHosting,
    actions: PanelItemActions,
    tileActions: TileActions,
    reorder: PanelReorderState,
    resize: PanelResizeState,
    column: List<PanelItemKey>,
    slot: Int,
    index: Int,
    onConfigureLauncherWidget: (LauncherWidgetType) -> Unit,
    // The card's glass, which goes on the pane alone: the resize handle opens inside this item but
    // is not part of it. See Modifier.panelCardGlass.
    glass: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember(item.key) { mutableStateOf(false) }
    var anchorBounds by remember(item.key) { mutableStateOf(IntRect.Zero) }

    val lift = rememberMenuLiftLayer(menuOpen)
    val name = panelItemName(item, widgets)
    val locked = LocalLayoutLocked.current

    val configure = rememberConfigureAction(item, widgets, onConfigureLauncherWidget)
    val resizable = item is PanelItem.AppWidget && LauncherAction.REORDER_PANEL.availableWhen(locked)
    val hasMenu = !locked || configure != null
    val resizing = resize.isActive(item.key)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { anchorBounds = it.menuAnchorBounds() }
            .zIndex(if (reorder.draggedIndex == index) LIFTED_ITEM_Z else 0f)
            .panelReorderSlot(reorder, slot),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .panelReorderSource(
                        state = reorder,
                        index = index,
                        column = column,
                        onMove = { move -> actions.onMove(move.to - move.from) },
                        onMenu = { menuOpen = true },
                        locked = locked,
                        hasMenu = hasMenu,
                    )
                    .panelItemSemantics(
                        name = name,
                        actions = actions,
                        hasMenu = hasMenu,
                        onResizeBy = resizeAction(item, resize, resizable),
                    ) { menuOpen = true },
            ) {
                Box(Modifier.panelReorderLift(reorder, index).menuLiftSource(lift).then(glass)) {
                    PanelItemContent(item, widgets, tileActions, resize)
                }
                if (resizing) Box(Modifier.matchParentSize().panelResizeBlocker(resize::stop))
            }
            if (resizing && item is PanelItem.AppWidget) PanelItemResizeHandle(item, resize)
        }
        PanelItemMenu(
            expanded = menuOpen,
            bounds = anchorBounds,
            lift = lift,
            actions = actions,
            onConfigure = configure,
            onResize = if (resizable) startResize(item, resize) else null,
            onDismiss = { menuOpen = false },
        )
    }
}

private fun startResize(item: PanelItem, resize: PanelResizeState): () -> Unit =
    { resize.start(item.key) }

@Composable
private fun PanelItemResizeHandle(item: PanelItem.AppWidget, resize: PanelResizeState) {
    PanelResizeHandle(
        onStart = { pointerY -> resize.dragStart(item, pointerY) },
        onDrag = resize::dragBy,
        onFinish = { resize.dragEnd(item.widget.appWidgetId) },
    )
}

@Composable
private fun PanelItemContent(
    item: PanelItem,
    widgets: WidgetHosting,
    tileActions: TileActions,
    resize: PanelResizeState,
) {
    when (item) {
        is PanelItem.AppWidget -> WidgetFrame(
            appWidgetId = item.widget.appWidgetId,
            widgets = widgets,
            height = resize.heightOf(item),
        )

        is PanelItem.LauncherWidget -> LauncherWidgetItem(item.widget, tileActions)
    }
}

private fun resizeAction(
    item: PanelItem,
    resize: PanelResizeState,
    resizable: Boolean,
): ((Int) -> Unit)? {
    if (!resizable || item !is PanelItem.AppWidget) return null
    return { delta -> resize.stepTo(item.widget.appWidgetId, resize.rowsOf(item) + delta) }
}

@Composable
private fun rememberConfigureAction(
    item: PanelItem,
    widgets: WidgetHosting,
    onConfigureLauncherWidget: (LauncherWidgetType) -> Unit,
): (() -> Unit)? {
    val allowed = LauncherAction.CONFIGURE_WIDGET.availableWhen(LocalLayoutLocked.current)
    val appWidgetId = (item as? PanelItem.AppWidget)?.widget?.appWidgetId
    val launcherWidget = (item as? PanelItem.LauncherWidget)?.widget
        ?.takeIf(::launcherWidgetHasSettings)
    return remember(item.key, widgets, allowed, onConfigureLauncherWidget) {
        when {
            !allowed -> null
            launcherWidget != null -> ({ onConfigureLauncherWidget(launcherWidget) })
            appWidgetId != null && widgets.canConfigure(appWidgetId) -> ({ widgets.configure(appWidgetId) })
            else -> null
        }
    }
}

@Composable
private fun panelItemName(item: PanelItem, widgets: WidgetHosting): String = when (item) {
    is PanelItem.AppWidget -> {
        val label by produceState(initialValue = null as String?, item.widget, widgets) {
            value = widgets.labelOf(item.widget)
        }
        label ?: stringResource(R.string.panel_widget_unknown, item.widget.appWidgetId)
    }

    is PanelItem.LauncherWidget -> stringResource(launcherWidgetLabelRes(item.widget))
}

@Composable
private fun LauncherWidgetItem(widget: LauncherWidgetType, tileActions: TileActions) {
    if (widget == LauncherWidgetType.SEARCHBAR) {
        SearchbarTile(onClick = tileActions.openSearch, modifier = Modifier.fillMaxWidth())
    } else {
        LauncherWidgetFrame(
            radius = Tokens.Radius.tile4x2,
            modifier = Modifier
                .fillMaxWidth()
                .height(Tokens.Grid.RowHeight.hero4x2)

                .clickable {
                    when (widget) {
                        LauncherWidgetType.CALENDAR -> tileActions.openCalendar(null)
                        LauncherWidgetType.CLOCK -> tileActions.openAlarms(null)
                        LauncherWidgetType.MEDIA -> tileActions.openMedia(null)
                        LauncherWidgetType.SEARCHBAR -> Unit
                    }
                },
        ) {
            LauncherWidgetContent(widget = widget)
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun Modifier.panelItemSemantics(
    name: String,
    actions: PanelItemActions,

    hasMenu: Boolean,
    onResizeBy: ((Int) -> Unit)?,
    onMenu: () -> Unit,
): Modifier {
    val description = stringResource(R.string.panel_widget_description, name)
    val menu = stringResource(R.string.panel_widget_menu)
    val moveUp = stringResource(R.string.panel_widget_move_up)
    val moveDown = stringResource(R.string.panel_widget_move_down)
    val remove = stringResource(R.string.panel_widget_remove)
    val taller = stringResource(R.string.panel_widget_taller)
    val shorter = stringResource(R.string.panel_widget_shorter)
    val manageable = LauncherAction.REORDER_PANEL.availableWhen(LocalLayoutLocked.current)
    return semantics {
        contentDescription = description
        if (hasMenu) {
            onLongClick(label = menu) {
                onMenu()
                true
            }
        }
        customActions = buildList {
            if (!manageable) return@buildList
            if (actions.canMoveUp) {
                add(CustomAccessibilityAction(moveUp) { actions.onMove(WIDGET_MOVE_UP); true })
            }
            if (actions.canMoveDown) {
                add(CustomAccessibilityAction(moveDown) { actions.onMove(WIDGET_MOVE_DOWN); true })
            }
            if (onResizeBy != null) {
                add(CustomAccessibilityAction(taller) { onResizeBy(1); true })
                add(CustomAccessibilityAction(shorter) { onResizeBy(-1); true })
            }
            add(CustomAccessibilityAction(remove) { actions.onRemove(); true })
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun PanelItemMenu(
    expanded: Boolean,
    bounds: IntRect,
    lift: GraphicsLayer?,
    actions: PanelItemActions,
    onConfigure: (() -> Unit)?,
    onResize: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    CueMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        anchor = MenuAnchor(
            bounds = bounds,
            decoration = lift?.let { MenuDecoration.Capture(layer = it) },
        ),
    ) {
        val manageable = LauncherAction.REORDER_PANEL.availableWhen(LocalLayoutLocked.current)
        if (manageable) {
            CueMenuItem(
                label = stringResource(R.string.panel_widget_move_up),
                glyph = GLYPH_MOVE_UP,
                enabled = actions.canMoveUp,
                onClick = {
                    onDismiss()
                    actions.onMove(WIDGET_MOVE_UP)
                },
            )
            CueMenuItem(
                label = stringResource(R.string.panel_widget_move_down),
                glyph = GLYPH_MOVE_DOWN,
                enabled = actions.canMoveDown,
                onClick = {
                    onDismiss()
                    actions.onMove(WIDGET_MOVE_DOWN)
                },
            )
        }
        if (onResize != null) {
            CueMenuItem(
                label = stringResource(R.string.panel_widget_resize),
                glyph = GLYPH_RESIZE,
                onClick = {
                    onDismiss()
                    onResize()
                },
            )
        }
        if (onConfigure != null) {
            CueMenuItem(
                label = stringResource(R.string.home_widget_configure),
                glyph = GLYPH_CONFIGURE,
                onClick = {
                    onDismiss()
                    onConfigure()
                },
            )
        }
        if (manageable) {
            CueMenuDestructiveItem(label = stringResource(R.string.panel_widget_remove)) {
                onDismiss()
                actions.onRemove()
            }
        }
    }
}

private const val LIFTED_ITEM_Z = 1f

private const val GLYPH_MOVE_UP = "arrow-up"
private const val GLYPH_MOVE_DOWN = "arrow-down"
private const val GLYPH_CONFIGURE = "cog-outline"
private const val GLYPH_RESIZE = "arrow-expand-vertical"
