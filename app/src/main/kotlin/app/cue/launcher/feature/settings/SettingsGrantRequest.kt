package app.cue.launcher.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cue.launcher.designsystem.Message
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.Tokens

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsGrantRequest(
    action: String,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    message: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        note?.let { Note(text = it) }
        message?.let { Message(text = it) }
        OutlinedButton(
            onClick = onGrant,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding),
        ) {
            Text(text = action)
        }
    }
}
