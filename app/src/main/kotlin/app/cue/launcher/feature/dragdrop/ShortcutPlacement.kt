package app.cue.launcher.feature.dragdrop

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.layout.LauncherItem
import app.cue.launcher.data.layout.LauncherLayoutRepository
import kotlinx.coroutines.launch

@Stable
class ShortcutPlacement(
    val dragState: ShortcutDragState,

    val drop: (DragPayload, DropTarget?) -> Unit,

    val placeFirstFree: (ComponentRef) -> Unit,
)

@Composable
fun rememberShortcutPlacement(
    layoutRepository: LauncherLayoutRepository,
    onPlaced: () -> Unit,
): ShortcutPlacement {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPlaced by rememberUpdatedState(onPlaced)
    val folderName = stringResource(R.string.folder_default_name)
    return remember(layoutRepository, context, scope, folderName) {
        val noRoom = {
            Toast.makeText(context, R.string.home_no_free_slot, Toast.LENGTH_SHORT).show()
        }

        val place: (ComponentRef, DropTarget?) -> Unit = { component, target ->
            when (target) {
                null -> noRoom()

                is DropTarget.Free -> {
                    scope.launch {
                        layoutRepository.placeShortcut(
                            container = target.slot.container,
                            position = target.slot.position,
                            component = component,
                        )
                    }
                    currentOnPlaced()
                }

                is DropTarget.Folder -> {
                    scope.launch { layoutRepository.addToFolder(target.folderId, component) }
                    currentOnPlaced()
                }

                is DropTarget.NewFolder -> {
                    scope.launch { layoutRepository.merge(target, component, folderName) }
                    currentOnPlaced()
                }
            }
        }

        val move: (Long, DropTarget?) -> Unit = { itemId, target ->
            if (target != null) scope.launch { layoutRepository.move(itemId, target, folderName) }
        }

        ShortcutPlacement(
            dragState = ShortcutDragState(),
            drop = { payload, target ->
                when (payload) {
                    is DragPayload.NewShortcut -> place(payload.component, target)
                    is DragPayload.MoveItem -> move(payload.itemId, target)
                }
            },
            placeFirstFree = { component ->
                scope.launch {
                    val free = firstFreeSlot(layoutRepository.layout())
                    place(component, free?.let { DropTarget.Free(it) })
                }
            },
        )
    }
}

private suspend fun LauncherLayoutRepository.move(itemId: Long, target: DropTarget, folderName: String) {
    val item = layout().allItems().firstOrNull { it.id == itemId } ?: return
    when (target) {
        is DropTarget.Free -> moveItem(item, target.slot.container, target.slot.position)
        is DropTarget.Folder -> if (item is LauncherItem.Shortcut) {
            addToFolder(target.folderId, item.component)
            removeItem(item)
        }

        is DropTarget.NewFolder -> if (item is LauncherItem.Shortcut) {
            merge(target, item.component, folderName, removing = item)
        }
    }
}

private suspend fun LauncherLayoutRepository.merge(
    target: DropTarget.NewFolder,
    added: ComponentRef,
    folderName: String,
    removing: LauncherItem? = null,
) {
    val onSlot = layout().allItems()
        .filterIsInstance<LauncherItem.Shortcut>()
        .firstOrNull { it.id == target.shortcutId } ?: return
    mergeIntoNewFolder(target = onSlot, added = added, name = folderName, removing = removing)
}
