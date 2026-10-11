package app.cue.launcher.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.cue.launcher.R
import app.cue.launcher.data.layout.LauncherItem
import app.cue.launcher.data.layout.LauncherLayoutRepository
import app.cue.launcher.data.layout.StaticLayout
import app.cue.launcher.designsystem.Tokens

@Composable
fun FolderRenameDialog(
    folder: LauncherItem.Folder,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {

    var name by rememberSaveable(folder.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(folder.name, TextRange(folder.name.length)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(R.string.folder_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(name.text.trim()) },
                enabled = name.text.isNotBlank(),
            ) {
                Text(text = stringResource(R.string.folder_rename_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.home_shortcut_add_cancel))
            }
        },
    )
}

@Composable
fun FolderPickerDialog(
    layoutRepository: LauncherLayoutRepository,
    onPick: (LauncherItem.Folder) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout by remember(layoutRepository) { layoutRepository.observeLayout() }
        .collectAsStateWithLifecycle(initialValue = StaticLayout.EMPTY)
    val folders = remember(layout) {
        layout.allItems().filterIsInstance<LauncherItem.Folder>()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(R.string.folder_pick_title)) },
        text = {
            if (folders.isEmpty()) {
                Text(
                    text = stringResource(R.string.folder_pick_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@AlertDialog
            }
            LazyColumn(modifier = Modifier.heightIn(max = Tokens.Size.drawerRow * PICKER_ROWS)) {
                items(items = folders, key = LauncherItem.Folder::id) { folder ->
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(folder) }
                            .padding(vertical = Tokens.Padding.tile),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.home_shortcut_add_cancel))
            }
        },
    )
}

private const val PICKER_ROWS = 8
