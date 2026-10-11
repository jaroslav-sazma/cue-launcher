package app.cue.launcher.feature.settings.ha

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import app.cue.launcher.R
import app.cue.launcher.data.ha.HaConfigRepository
import app.cue.launcher.data.ha.haEventSecretError
import app.cue.launcher.designsystem.Flash
import app.cue.launcher.designsystem.FlashState
import app.cue.launcher.designsystem.Tokens
import kotlinx.coroutines.launch
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.Message

@Composable
internal fun HaSecretRow(
    configs: HaConfigRepository,
    flash: FlashState,
    onUpload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stored by remember(configs) { configs.eventSecret }.collectAsStateWithLifecycle(initialValue = "")
    var draft by remember(stored) { mutableStateOf(stored) }
    val error = haEventSecretError(draft)
    val changed = draft.trim() != stored

    LaunchedEffect(configs) { configs.ensureEventSecret() }

    // Every automation from the blueprint still carries the old secret until it goes up again, so the
    // flash offers the upload itself rather than only asking for it.
    val upload = stringResource(R.string.ha_blueprint_upload_action)
    val saved = stringResource(R.string.ha_secret_saved)
    val rotated = stringResource(R.string.ha_secret_rotated)
    val askUpload = { text: String -> flash.show(Flash(text, action = upload)) { acted -> if (acted) onUpload() } }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
            label = { Text(text = stringResource(R.string.ha_secret)) },
            isError = error != null,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
            singleLine = true,
        )
        error?.let { Message(text = haSecretErrorText(it)) }
        Note(text = stringResource(R.string.ha_secret_note))
        HaSecretButtons(
            onCopy = { copyToClipboard(context, stored) },
            onSave = if (changed && error == null) {
                {
                    scope.launch {
                        if (configs.saveEventSecret(draft)) {
                            askUpload(saved)
                        } else {
                            flash.show(R.string.ha_secret_invalid, error = true)
                        }
                    }
                }
            } else {
                null
            },
            onRotate = {
                scope.launch {
                    configs.rotateEventSecret()
                    askUpload(rotated)
                }
            },
            copyEnabled = stored.isNotEmpty(),
        )
    }
}

@Composable
private fun HaSecretButtons(
    onCopy: () -> Unit,
    onSave: (() -> Unit)?,
    onRotate: () -> Unit,
    copyEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = Tokens.Settings.surfacePadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        OutlinedButton(onClick = onCopy, enabled = copyEnabled) {
            Text(text = stringResource(R.string.ha_secret_copy))
        }
        onSave?.let { save ->
            OutlinedButton(onClick = save) {
                Text(text = stringResource(R.string.ha_secret_save))
            }
        }
        TextButton(onClick = onRotate) {
            Text(text = stringResource(R.string.ha_secret_rotate))
        }
    }
}

// No flash after copying: the system shows its own clipboard confirmation, and a second one on top
// of it is noise.
private fun copyToClipboard(context: Context, secret: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("", secret).apply {

        // Without the flag the system clipboard preview shows the secret in plain sight.
        description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
}
