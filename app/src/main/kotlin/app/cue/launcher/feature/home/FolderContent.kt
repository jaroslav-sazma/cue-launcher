package app.cue.launcher.feature.home

import android.graphics.Rect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.offset
import androidx.compose.ui.zIndex
import app.cue.launcher.R
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.layout.LauncherItem
import app.cue.launcher.designsystem.AppIcon
import app.cue.launcher.designsystem.BadgedIcon
import app.cue.launcher.designsystem.CueMenu
import app.cue.launcher.designsystem.CueMenuDestructiveItem
import app.cue.launcher.designsystem.CueMenuItem
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.MenuAnchor
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.dropTargetOutline
import app.cue.launcher.designsystem.glyph.LocalMdiGlyphs
import app.cue.launcher.designsystem.labelOnZoneStyle
import app.cue.launcher.designsystem.menuAnchorBounds
import app.cue.launcher.engine.QueueEntry
import app.cue.launcher.feature.icons.notificationCountSemantics
import app.cue.launcher.feature.icons.rememberNotificationCount
import app.cue.launcher.feature.icons.rememberShortcut
import app.cue.launcher.feature.lock.LauncherAction
import app.cue.launcher.feature.lock.LocalLayoutLocked
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.toSourceBounds

@Composable
internal fun StaticFolderContent(
    folder: LauncherItem.Folder,
    launcherApps: LauncherAppsRepository,
    onLaunch: (ComponentRef, Rect?) -> Unit,
    onRemove: (ComponentRef) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAdd: () -> Unit,
) {
    val locked = LocalLayoutLocked.current
    val reorder = remember(folder.id) { FolderReorderState() }
    val itemCount = folder.items.size
    val order = reorder.previewOrder(itemCount)
    val addCell = if (LauncherAction.ADD_TO_FOLDER.availableWhen(locked)) 1 else 0
    FolderGrid(cellCount = itemCount + addCell, reorder = reorder) { slot ->
        val index = order.getOrNull(slot)
        val component = index?.let(folder.items::getOrNull)
        if (index == null || component == null) {
            FolderAddCell(onClick = onAdd)
        } else {
            FolderShortcutCell(
                component = component,
                index = index,
                slot = slot,
                itemCount = itemCount,
                reorder = reorder,
                launcherApps = launcherApps,
                onLaunch = onLaunch,
                onRemove = { onRemove(component) },
                onMove = onMove,
            )
        }
    }
}

@Composable
internal fun DynamicFolderContent(
    pages: List<QueueEntry>,
    launcherApps: LauncherAppsRepository,
    actions: TileActions,
) {

    FolderGrid(cellCount = pages.size, reorder = null) { index ->
        val entry = pages[index]
        val item = entry.scenario.templateItem()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (item == null) {

                Spacer(modifier = Modifier.size(Tokens.Size.icon))
            } else {
                TemplateItemIcon(
                    item = item,
                    launcherApps = launcherApps,
                    actions = actions,
                    level = GlassLevel.THICK,
                    glyphTint = templateIconTint(item, entry.payload.semantic, entry.accentedEntities),
                    stateGlyphs = templateStateGlyphs(item, entry.haGlyphs, LocalMdiGlyphs.current),
                    contentDescription = item.label,
                )
            }
            FolderCellLabel(text = item?.label ?: entry.scenario.name)
        }
    }
}

@Composable
private fun FolderGrid(
    cellCount: Int,
    reorder: FolderReorderState?,
    cell: @Composable (Int) -> Unit,
) {
    val columns = Tokens.Folder.COLUMNS
    val liftedRow = reorder?.liftedSlot?.let { it / columns }
    val reach = Tokens.Badge.reach
    Column(
        modifier = Modifier
            .growUpward(reach)
            .heightIn(max = FOLDER_GRID_MAX_HEIGHT + reach)
            .verticalScroll(rememberScrollState())
            .padding(top = reach),
        verticalArrangement = Arrangement.spacedBy(Tokens.Folder.gridRowGap),
    ) {
        val rows = (cellCount + columns - 1) / columns
        repeat(rows) { row ->
            Row(
                modifier = Modifier.zIndex(if (row == liftedRow) LIFTED_ROW_Z else 0f),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Folder.gridColumnGap),
            ) {
                repeat(columns) { column ->
                    val index = row * columns + column
                    val width = Modifier.width(Tokens.Size.icon)
                    Box(
                        modifier = when {
                            reorder != null && index < cellCount -> width.folderReorderCell(reorder, index)
                            else -> width
                        },
                    ) {
                        if (index < cellCount) cell(index)
                    }
                }
            }
        }
    }
}

// The scroll clips to its viewport, which would cut the top row's badges where they reach past the
// icons. The viewport grows upward by [extra] while the grid keeps its size and position, so only
// a scrolled grid shows rows in that band.
private fun Modifier.growUpward(extra: Dp): Modifier = layout { measurable, constraints ->
    val extraPx = extra.roundToPx()
    val placeable = measurable.measure(constraints.offset(vertical = extraPx))
    layout(placeable.width, placeable.height - extraPx) { placeable.place(0, -extraPx) }
}

@Composable
private fun FolderShortcutCell(
    component: ComponentRef,
    index: Int,
    slot: Int,
    itemCount: Int,
    reorder: FolderReorderState,
    launcherApps: LauncherAppsRepository,
    onLaunch: (ComponentRef, Rect?) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val resolved = rememberShortcut(component, launcherApps)
    var menuOpen by remember(component) { mutableStateOf(false) }
    var bounds by remember(component) { mutableStateOf<Rect?>(null) }
    var anchorBounds by remember(component) { mutableStateOf(IntRect.Zero) }
    val label = resolved?.app?.label
    val menuLabel = stringResource(R.string.folder_item_menu)

    val managed = LauncherAction.REORDER_FOLDER.availableWhen(LocalLayoutLocked.current)
    val notificationCount = rememberNotificationCount(component)

    Column(
        modifier = Modifier
            .folderSlotSwap(reorder, index, slot)
            .folderReorderLift(reorder, index),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BadgedIcon(count = notificationCount) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Tokens.Radius.icon))
                    .clickable(onClickLabel = null) { onLaunch(component, bounds) }
                    .then(
                        if (!managed) {
                            Modifier
                        } else {
                            Modifier.folderItemManagement(
                                reorder = reorder,
                                index = index,
                                itemCount = itemCount,
                                menuLabel = menuLabel,
                                onMove = onMove,
                                onMenu = { menuOpen = true },
                            )
                        },
                    )
                    .notificationCountSemantics(notificationCount)
                    .onGloballyPositioned {
                        bounds = it.boundsInWindow().toSourceBounds()
                        anchorBounds = it.menuAnchorBounds()
                    },
            ) {
                AppIcon(icon = resolved?.icon, contentDescription = label)
            }
        }
        FolderCellLabel(text = label.orEmpty())
        if (managed) {
            FolderItemMenu(
                expanded = menuOpen,
                bounds = anchorBounds,
                index = index,
                itemCount = itemCount,
                onDismiss = { menuOpen = false },
                onRemove = onRemove,
                onMove = onMove,
            )
        }
    }
}

@Composable
private fun Modifier.folderItemManagement(
    reorder: FolderReorderState,
    index: Int,
    itemCount: Int,
    menuLabel: String,
    onMove: (Int, Int) -> Unit,
    onMenu: () -> Unit,
): Modifier = folderReorderSource(
    state = reorder,
    index = index,
    itemCount = itemCount,
    onMove = { move -> onMove(move.from, move.to) },
    onMenu = onMenu,
).semantics {
    onLongClick(label = menuLabel) {
        onMenu()
        true
    }
}

@Composable
private fun FolderItemMenu(
    expanded: Boolean,
    bounds: IntRect,
    index: Int,
    itemCount: Int,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int, Int) -> Unit,
) {

    val movable = index > 0 || index < itemCount - 1
    CueMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        anchor = MenuAnchor(bounds = bounds),
    ) {
        if (index > 0) {
            CueMenuItem(
                label = stringResource(R.string.folder_move_left),
                glyph = GLYPH_MOVE_LEFT,
                onClick = {
                    onDismiss()
                    onMove(index, index - 1)
                },
            )
        }
        if (index < itemCount - 1) {
            CueMenuItem(
                label = stringResource(R.string.folder_move_right),
                glyph = GLYPH_MOVE_RIGHT,
                onClick = {
                    onDismiss()
                    onMove(index, index + 1)
                },
            )
        }
        CueMenuDestructiveItem(
            label = stringResource(R.string.folder_item_remove),
            separated = movable,
        ) {
            onDismiss()
            onRemove()
        }
    }
}

@Composable
private fun FolderAddCell(onClick: () -> Unit) {
    val description = stringResource(R.string.folder_add)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(Tokens.Size.icon)
                .dropTargetOutline(visible = true)
                .clip(RoundedCornerShape(Tokens.Radius.icon))
                .pointerInput(onClick) { detectTapGestures { onClick() } }
                .semantics {
                    role = Role.Button
                    contentDescription = description
                },
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                name = ADD_GLYPH,
                size = Tokens.Folder.addGlyph,
                tint = LocalCueColors.current.accent,
            )
        }
        FolderCellLabel(text = description)
    }
}

@Composable
private fun FolderCellLabel(text: String) {
    Spacer(modifier = Modifier.height(Tokens.Type.labelGapBelowIcon))
    Text(
        text = text,
        style = labelOnZoneStyle(),
        maxLines = Tokens.Type.LABEL_LINES,
        overflow = Tokens.Type.labelOverflow,

        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun folderChipDescription(name: String, itemCount: Int): String =
    pluralStringResource(R.plurals.folder_items_count, itemCount, name, itemCount)

private const val ADD_GLYPH = "plus"

private const val GLYPH_MOVE_LEFT = "arrow-left"
private const val GLYPH_MOVE_RIGHT = "arrow-right"

private val FOLDER_GRID_MAX_HEIGHT = Tokens.Grid.RowHeight.static * 4

private const val LIFTED_ROW_Z = 1f
