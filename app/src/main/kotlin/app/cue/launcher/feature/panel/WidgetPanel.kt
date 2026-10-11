package app.cue.launcher.feature.panel

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateBounds
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.cue.launcher.R
import app.cue.launcher.data.layout.LauncherLayoutRepository
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.data.layout.StaticLayout
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.widgets.PanelItem
import app.cue.launcher.data.widgets.WidgetPlacement
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.PanelScrollbar
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.designsystem.bentoCardEnter
import app.cue.launcher.designsystem.panelCardGlass
import app.cue.launcher.designsystem.panelSurface
import app.cue.launcher.designsystem.rememberGlassOverscroll
import app.cue.launcher.designsystem.rememberReducedMotion
import app.cue.launcher.designsystem.spatialFastSpec
import app.cue.launcher.feature.dragdrop.ShortcutDragState
import app.cue.launcher.feature.home.HomeOverlayState
import app.cue.launcher.feature.home.HomeOverlayTarget
import app.cue.launcher.feature.home.ShortcutSlotRow
import app.cue.launcher.feature.home.StaticSlotActions
import app.cue.launcher.feature.home.TileActions
import app.cue.launcher.feature.home.rememberStaticSlotActions
import app.cue.launcher.feature.home.staticSlotRows
import app.cue.launcher.feature.lock.LauncherAction
import app.cue.launcher.feature.lock.LocalLayoutLocked
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.feature.widgets.WidgetPick
import app.cue.launcher.feature.widgets.WidgetPickerSheet
import app.cue.launcher.feature.widgets.rememberWidgetPick
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.widget.WidgetBindOutcome
import kotlinx.coroutines.launch

@Composable
private fun rememberPanelDockActions(
    launcherApps: LauncherAppsRepository,
    layoutRepository: LauncherLayoutRepository,
    overlay: HomeOverlayState,
    tileActions: TileActions,
): StaticSlotActions = rememberStaticSlotActions(
    launcherApps = launcherApps,
    layoutRepository = layoutRepository,
    overlay = overlay,
    onOpenSearch = tileActions.openSearch,
    onOpenCalendar = { tileActions.openCalendar(null) },
    onOpenAlarms = { tileActions.openAlarms(null) },
    onOpenMedia = { tileActions.openMedia(null) },
)

@Composable
fun WidgetPanel(
    widgets: WidgetHosting,
    launcherApps: LauncherAppsRepository,
    layoutRepository: LauncherLayoutRepository,
    dragState: ShortcutDragState,
    overlay: HomeOverlayState,
    tileActions: TileActions,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val column by remember(widgets) { widgets.observeColumn() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val layout by remember(layoutRepository) { layoutRepository.observeLayout() }
        .collectAsStateWithLifecycle(initialValue = StaticLayout.EMPTY)
    val actions = rememberPanelDockActions(launcherApps, layoutRepository, overlay, tileActions)
    val pick = rememberWidgetPick(widgets)
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    PanelBindOutcomes(widgets)

    Box(
        modifier = modifier
            .fillMaxSize()
            .panelSurface(
                shape = RoundedCornerShape(Tokens.Panel.surfaceCorner),
                cornerRadius = Tokens.Panel.surfaceCorner,
            ),
    ) {
        PanelContent(
            column = column,
            widgets = widgets,
            tileActions = tileActions,
            progress = progress,
            onAddWidget = { pickerOpen = true },
            onConfigureLauncherWidget = { widget ->
                overlay.open(HomeOverlayTarget.WidgetSettings(widget))
            },
            modifier = Modifier.padding(
                start = Tokens.Padding.zone,
                end = Tokens.Padding.zone,
                bottom = panelBarStripHeight(navigationInset),
            ),
        )
        PanelBar(
            slots = staticSlotRows(layout, LayoutContainer.PANEL_DOCK).flatten(),
            launcherApps = launcherApps,
            dragState = dragState,
            actions = actions,
            navigationBarInset = navigationInset,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (pickerOpen) {
        PanelWidgetPicker(widgets = widgets, pick = pick, onClose = { pickerOpen = false })
    }
}

@Composable
private fun PanelWidgetPicker(widgets: WidgetHosting, pick: WidgetPick, onClose: () -> Unit) {
    WidgetPickerSheet(
        widgets = widgets,
        onPick = { widget ->
            onClose()
            pick.app(widget)
        },
        onPickLauncherWidget = { widget ->
            onClose()
            pick.launcher(widget)
        },
        onDismiss = onClose,
    )
}

@Composable
private fun PanelBindOutcomes(widgets: WidgetHosting) {
    val context = LocalContext.current
    LaunchedEffect(widgets) {
        widgets.outcomes.collect { result ->
            if (result.placement == WidgetPlacement.PANEL && result.outcome == WidgetBindOutcome.FAILED) {
                Toast.makeText(context, R.string.widget_bind_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun PanelContent(
    column: List<PanelItem>,
    widgets: WidgetHosting,
    tileActions: TileActions,
    progress: () -> Float,
    onAddWidget: () -> Unit,
    onConfigureLauncherWidget: (LauncherWidgetType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val autoScroller = rememberPanelAutoScroller(scrollState)
    val reorder = rememberPanelReorderState(autoScroller)
    val overscroll = rememberGlassOverscroll(Orientation.Vertical)
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            ),
    ) {
        val resize = rememberPanelResizeState(
            autoScroller = autoScroller,
            viewportHeightPx = constraints.maxHeight.toFloat(),
        ) { appWidgetId, rows ->
            scope.launch { widgets.resize(appWidgetId, rows) }
        }
        BackHandler(enabled = resize.running, onBack = resize::stop)

        // Reserve the scrollbar gutter only once the content overflows, otherwise the column sits
        // off-centre. Panel item heights do not depend on width, so the gutter cannot feed back
        // into scrollability.
        val scrollable = scrollState.viewportSize > 0 && scrollState.maxValue > 0

        WidgetColumn(
            column = column,
            widgets = widgets,
            tileActions = tileActions,
            reorder = reorder,
            resize = resize,
            progress = progress,
            onAddWidget = onAddWidget,
            onConfigureLauncherWidget = onConfigureLauncherWidget,
            modifier = Modifier
                .fillMaxSize()

                .panelAutoScrollViewport(autoScroller)
                .panelResizeDismiss(resize)
                .verticalScroll(scrollState, overscrollEffect = overscroll)
                .then(
                    if (scrollable) {
                        Modifier.padding(end = Tokens.Panel.Scrollbar.gutter)
                    } else {
                        Modifier
                    },
                )
                .padding(bottom = Tokens.Panel.contentBottomPadding),
        )
        PanelScrollbar(
            state = scrollState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .padding(
                    end = Tokens.Panel.Scrollbar.marginEnd,
                    top = Tokens.Panel.Scrollbar.marginVertical,
                    bottom = Tokens.Panel.Scrollbar.marginVertical,
                ),
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun WidgetColumn(
    column: List<PanelItem>,
    widgets: WidgetHosting,
    tileActions: TileActions,
    reorder: PanelReorderState,
    resize: PanelResizeState,
    progress: () -> Float,
    onAddWidget: () -> Unit,
    onConfigureLauncherWidget: (LauncherWidgetType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCueColors.current
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    LookaheadScope {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Tokens.Panel.widgetGap),
        ) {
            if (column.isEmpty()) {
                Text(
                    text = stringResource(R.string.panel_widgets_empty),
                    color = colors.textSecondary,
                    style = Tokens.Type.tileBody.toTextStyle(),
                    textAlign = TextAlign.Center,
                )
            }

            val keys = column.map(PanelItem::key)
            reorder.previewOrder(keys).forEachIndexed { slot, index ->
                val item = column.getOrNull(index) ?: return@forEachIndexed

                key(item.key) {
                    PanelWidgetItem(
                        item = item,
                        widgets = widgets,
                        tileActions = tileActions,
                        reorder = reorder,
                        resize = resize,
                        column = keys,
                        slot = slot,
                        index = index,
                        glass = Modifier
                            .panelCardGlass(item.key.regionKey(), slot, keys.size, reducedMotion, progress),
                        modifier = Modifier
                            .bentoCardEnter(reducedMotion, slot, keys.size, progress)
                            .panelSwapAnimation(this@LookaheadScope, reorder.draggedIndex == index),
                        onConfigureLauncherWidget = onConfigureLauncherWidget,
                        actions = PanelItemActions(
                            canMoveUp = index > 0,
                            canMoveDown = index < column.lastIndex,
                            onMove = { delta -> scope.launch { widgets.move(item.key, delta) } },
                            onRemove = { scope.launch { widgets.remove(item.key) } },
                        ),
                    )
                }
            }

            if (LauncherAction.ADD_PANEL_WIDGET.availableWhen(LocalLayoutLocked.current)) {
                AddWidgetRow(onAddWidget = onAddWidget)
            }
        }
    }
}

@Composable
private fun AddWidgetRow(onAddWidget: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.panel_widget_add),
        color = LocalCueColors.current.accent,
        style = Tokens.Type.tileBody.toTextStyle(),
        modifier = modifier
            .clip(RoundedCornerShape(Tokens.Panel.surfaceCorner))
            .clickable(onClick = onAddWidget)
            .padding(Tokens.Padding.tile),
    )
}

// The lifted item must not animate its slot move: panelReorderLift compensates from the
// drag-start snapshot and assumes the layout position is already the preview slot.
@Composable
private fun Modifier.panelSwapAnimation(lookahead: LookaheadScope, dragged: Boolean): Modifier {
    val spec = spatialFastSpec<Rect>(rememberReducedMotion())
    return if (dragged) this else animateBounds(lookahead, boundsTransform = { _, _ -> spec })
}
