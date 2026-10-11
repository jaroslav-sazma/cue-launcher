package app.cue.launcher.feature.scenarios.aichat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.aichat.HaNeeds
import app.cue.launcher.designsystem.MediumOutlinedButton
import app.cue.launcher.designsystem.Message
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Tokens

@Composable
internal fun DraftNotes(
    needs: HaNeeds,
    placeholders: Set<String>,
    widgetUnbound: Boolean,
    onConnectHa: () -> Unit,
) {
    if (!needs.any && placeholders.isEmpty() && !widgetUnbound) return
    SectionLabel(title = stringResource(R.string.ai_chat_notes_title))
    if (needs.connection) {
        Message(text = stringResource(R.string.ai_chat_ha_connect_text))
        MediumOutlinedButton(
            label = stringResource(R.string.ai_chat_ha_connect),
            onClick = onConnectHa,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.screenPadding),
        )
    }
    if (widgetUnbound) Message(text = stringResource(R.string.ai_chat_widget_unbound_text))
    if (placeholders.isNotEmpty()) {
        Message(text = stringResource(R.string.ai_chat_placeholders_text, placeholders.joinToString()))
    }
    if (needs.blueprint) {
        Message(text = stringResource(R.string.ai_chat_ha_event_text))
        Note(text = stringResource(R.string.builder_ha_note))
    }
}
