package app.cue.launcher.feature.scenarios.aichat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleStartEffect
import app.cue.launcher.R
import app.cue.launcher.data.aichat.AiChatOffer
import app.cue.launcher.data.aichat.AiChatShare
import app.cue.launcher.data.aichat.CachedInventory
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.designsystem.DetailPaneScaffold
import app.cue.launcher.designsystem.FlashState
import app.cue.launcher.designsystem.MediumOutlinedButton
import app.cue.launcher.designsystem.Message
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.rememberFlashState
import app.cue.launcher.feature.scenarios.builder.tileTypeAvailable
import app.cue.launcher.feature.widgets.rememberBentoSlotPx
import app.cue.launcher.system.DocumentStore
import app.cue.launcher.system.bluetoothConnectGranted
import app.cue.launcher.system.launchOrElse
import app.cue.launcher.system.aichat.AI_CHAT_CONTEXT_FILE_NAME
import app.cue.launcher.system.aichat.AI_CHAT_FILE_PROMPT
import app.cue.launcher.system.aichat.AiChatHandoff
import app.cue.launcher.system.aichat.HandoffOutcome
import app.cue.launcher.system.aichat.WidgetSlots
import app.cue.launcher.system.aichat.collectAiChatContext
import app.cue.launcher.system.aichat.pasteRoute
import app.cue.launcher.system.widget.WidgetBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// The brief is read only on the user's tap and while this screen is in front: leaving mid-read
// cancels it, so the chat never opens behind the user's back. The choice of what goes along is the
// stored one, so the caller composes this screen only once the settings have loaded. Whether
// Bluetooth may be read is asked anew on every start; nothing here asks for the permission.
@Suppress("LongParameterList")
@Composable
fun AiChatHandoffScreen(
    inventory: CachedInventory,
    share: AiChatShare,
    haConfigured: Boolean,
    onShareChange: (AiChatShare) -> Unit,
    onSent: () -> Unit,
    onBack: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val flash = rememberFlashState()
    val handoff = remember(context) { AiChatHandoff(context) }
    val route = pasteRoute(context::getString)
    val latestRoute by rememberUpdatedState(route)
    val slots by rememberUpdatedState(rememberWidgetSlots())
    val sent by rememberUpdatedState(onSent)
    val persist by rememberUpdatedState(onShareChange)
    val available = { AiChatOffer(bluetooth = bluetoothConnectGranted(context), homeAssistant = haConfigured) }
    val create = { outcome: HandoffOutcome? ->
        HandoffState(
            readBrief = { chosen, offer -> collectAiChatContext(inventory, locale, latestRoute, chosen, offer, slots) },
            sendBrief = handoff::send,
            clear = inventory::clear,
            scope = scope,
            outcome = outcome,
            share = share,
            offer = available(),
            persistShare = { persist(it) },
            onSent = { sent() },
        )
    }
    val state = rememberSaveable(inventory, scope, saver = HandoffState.saver(create)) { create(null) }
    LifecycleStartEffect(state, haConfigured) {
        state.start(available())
        onStopOrDispose { state.stop() }
    }
    val saveBrief = rememberBriefSaving(state, flash, scope)

    DetailPaneScaffold(
        title = stringResource(R.string.ai_chat_handoff_title),
        subtitle = stringResource(R.string.experimental_label),
        onBack = onBack,
        modifier = modifier,
        flash = flash,
        bottomBar = { if (!state.sending) HandoffBottomBar(state.outcome, onSend = state::send, onPaste = onPaste) },
    ) {
        if (state.sending) {
            AiChatWaiting(text = stringResource(R.string.ai_chat_collecting))
        } else {
            AiChatBody {
                HandoffStatus(
                    state = state,
                    onSave = saveBrief,
                    onCopy = { copyText(context, AI_CHAT_FILE_PROMPT) },
                )
                ShareChoice(share = state.share, offer = state.offer, onChange = state::choose)
                BodyButtons { PreviewButton(onClick = state::preview) }
            }
        }
    }
    if (state.previewing) {
        PromptPreviewSheet(message = AI_CHAT_FILE_PROMPT, brief = state.brief, onClose = state::closePreview)
    }
}

@Composable
private fun PreviewButton(onClick: () -> Unit) {
    MediumOutlinedButton(
        label = stringResource(R.string.ai_chat_prompt_preview),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

// The slots the builder's widget picker measures, so the sizes the brief lists for a widget are the
// ones the wizard will offer it for.
@Composable
private fun rememberWidgetSlots(): WidgetSlots = TileSize.entries.filter(::tileTypeAvailable).associateWith { size ->
    rememberBentoSlotPx(size).let { WidgetBox(widthPx = it.width, heightPx = it.height) }
}

@Composable
private fun rememberBriefSaving(state: HandoffState, flash: FlashState, scope: CoroutineScope): () -> Unit {
    val context = LocalContext.current
    val saveDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_MARKDOWN),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val saved = DocumentStore.writeText(context, uri, state.briefForSaving())
                flash.show(if (saved) R.string.ai_chat_saved else R.string.ai_chat_save_failed, error = !saved)
            }
        }
    }
    return {
        saveDocument.launchOrElse(AI_CHAT_CONTEXT_FILE_NAME) {
            flash.show(R.string.document_picker_missing, error = true)
        }
    }
}

// The floating toolbar carries the one next step; a second action would squeeze both labels onto two
// lines, so it stands in the body under the text that explains it.
@Composable
private fun HandoffBottomBar(outcome: HandoffOutcome?, onSend: () -> Unit, onPaste: () -> Unit) {
    if (outcome == null) {
        AiChatBottomBar(BarButton(stringResource(R.string.ai_chat_open_chat), onSend))
    } else {
        AiChatBottomBar(BarButton(stringResource(R.string.ai_chat_paste_answer), onPaste))
    }
}

@Composable
private fun ColumnScope.HandoffStatus(state: HandoffState, onSave: () -> Unit, onCopy: () -> Unit) {
    when (val outcome = state.outcome) {
        null -> Message(text = stringResource(R.string.ai_chat_intro_text))
        HandoffOutcome.SENT -> {
            Message(text = stringResource(R.string.ai_chat_sent_text))
            Note(text = stringResource(R.string.ai_chat_sent_note))
            BodyButtons { SendAgainButton(onClick = state::send) }
        }

        HandoffOutcome.NO_CHAT_APP, HandoffOutcome.BRIEF_UNWRITABLE -> {
            val unwritable = outcome == HandoffOutcome.BRIEF_UNWRITABLE
            val text = if (unwritable) R.string.ai_chat_unwritable_text else R.string.ai_chat_no_app_text
            Message(text = stringResource(text))
            Note(text = stringResource(R.string.ai_chat_no_app_note))
            BodyButtons {
                MediumOutlinedButton(
                    label = stringResource(R.string.ai_chat_save_brief),
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.brief != null,
                )
                MediumOutlinedButton(
                    label = stringResource(R.string.ai_chat_copy_prompt),
                    onClick = onCopy,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (unwritable) SendAgainButton(onClick = state::send)
            }
        }
    }
}

@Composable
private fun SendAgainButton(onClick: () -> Unit) {
    MediumOutlinedButton(
        label = stringResource(R.string.ai_chat_send_again),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BodyButtons(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.cardGap),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShareChoice(share: AiChatShare, offer: AiChatOffer, onChange: (AiChatShare) -> Unit) {
    val parts = listOfNotNull(
        SharePart(R.string.ai_chat_share_bluetooth, R.string.ai_chat_share_bluetooth_detail, share.bluetooth) {
            share.copy(bluetooth = it)
        }.takeIf { offer.bluetooth },
        SharePart(R.string.ai_chat_share_ha, R.string.ai_chat_share_ha_detail, share.homeAssistant) {
            share.copy(homeAssistant = it)
        }.takeIf { offer.homeAssistant },
        SharePart(R.string.ai_chat_share_shortcuts, R.string.ai_chat_share_shortcuts_detail, share.shortcuts) {
            share.copy(shortcuts = it)
        },
    )
    SectionLabel(title = stringResource(R.string.ai_chat_share_title))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.rowMargin),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        parts.forEachIndexed { index, part ->
            SegmentedListItem(
                checked = part.checked,
                onCheckedChange = { onChange(part.with(it)) },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = parts.size),
                leadingContent = { Checkbox(checked = part.checked, onCheckedChange = null) },
                supportingContent = { Text(text = stringResource(part.detail)) },
                content = { Text(text = stringResource(part.title)) },
            )
        }
    }
    Note(text = stringResource(R.string.ai_chat_share_always))
}

private class SharePart(
    @param:StringRes val title: Int,
    @param:StringRes val detail: Int,
    val checked: Boolean,
    val with: (Boolean) -> AiChatShare,
)

private const val MIME_MARKDOWN = "text/markdown"
