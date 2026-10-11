package app.cue.launcher.feature.scenarios.aichat

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.designsystem.ModalSheet
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.SheetHeader
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.codeStyle
import app.cue.launcher.designsystem.rememberExpandedSheetState
import app.cue.launcher.system.aichat.AI_CHAT_CONTEXT_FILE_NAME

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PromptPreviewSheet(message: String, brief: String?, onClose: () -> Unit) {
    ModalSheet(onDismissRequest = onClose, sheetState = rememberExpandedSheetState()) {
        SheetHeader(title = stringResource(R.string.ai_chat_prompt_preview_title), onClose = onClose)
        if (brief == null) {
            AiChatWaiting(text = stringResource(R.string.ai_chat_collecting))
        } else {
            PromptText(message = message, brief = brief)
        }
    }
}

@Composable
private fun PromptText(message: String, brief: String) {
    val chunks = remember(brief) { previewChunks(brief) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Tokens.Settings.sectionSpacing),
    ) {
        item { Note(text = stringResource(R.string.ai_chat_prompt_preview_note)) }
        item { SectionLabel(title = stringResource(R.string.ai_chat_prompt_message)) }
        item { CodeText(text = message) }
        item { SectionLabel(title = stringResource(R.string.ai_chat_prompt_file, AI_CHAT_CONTEXT_FILE_NAME)) }
        items(chunks) { chunk -> CodeText(text = chunk) }
    }
}

@Composable
private fun CodeText(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.screenPadding),
        style = codeStyle,
    )
}

// One Text holding the whole brief would lay out a hundred thousand characters at once. A chunk ends
// with a blank line or after a fixed number of lines, so the inventory's long blocks split too, and
// the chunks joined by line breaks are the brief again.
internal fun previewChunks(text: String, maxLines: Int = PREVIEW_CHUNK_LINES): List<String> {
    val lines = text.lines()
    val chunks = mutableListOf<String>()
    var start = 0
    lines.forEachIndexed { index, line ->
        if (line.isBlank() || index + 1 - start == maxLines) {
            chunks += lines.subList(start, index + 1).joinToString("\n")
            start = index + 1
        }
    }
    if (start < lines.size) chunks += lines.subList(start, lines.size).joinToString("\n")
    return chunks
}

private const val PREVIEW_CHUNK_LINES = 40
