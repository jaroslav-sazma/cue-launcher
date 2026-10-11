package app.cue.launcher.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.designsystem.AppIcon
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.icons.rememberAppIcon
import app.cue.launcher.system.InstalledApp
import app.cue.launcher.system.LauncherAppsRepository

@Composable
fun ShortcutPickerDialog(
    launcherApps: LauncherAppsRepository,
    onPick: (ComponentRef) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val apps by remember(launcherApps) { launcherApps.observeInstalledApps() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(R.string.home_shortcut_add)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = PICKER_MAX_HEIGHT)) {
                items(items = apps, key = InstalledApp::listKey) { app ->
                    PickerRow(
                        app = app,
                        launcherApps = launcherApps,
                        onPick = { onPick(app.component) },
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

@Composable
private fun PickerRow(
    app: InstalledApp,
    launcherApps: LauncherAppsRepository,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = rememberAppIcon(app.component, launcherApps)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(vertical = Tokens.Padding.chip),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Grid.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            icon = icon,
            contentDescription = null,
            size = Tokens.Size.drawerResultIcon,
        )
        Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
    }
}

private val InstalledApp.listKey: String
    get() = "${component.packageName}/${component.className}#${component.userSerial}"

private val PICKER_MAX_HEIGHT = Tokens.Size.drawerRow * 8
