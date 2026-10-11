package app.cue.launcher.feature.scenarios.aichat

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.designsystem.ExperimentalLabel
import app.cue.launcher.designsystem.LeadingIconBox
import app.cue.launcher.designsystem.ModalSheet
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.SheetFrame
import app.cue.launcher.designsystem.SheetHeader
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.rememberExpandedSheetState
import app.cue.launcher.designsystem.rememberSheetExit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewScenarioChooser(
    onWizard: () -> Unit,
    onAiChat: () -> Unit,
    onPaste: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
    val pick = rememberSheetExit(sheetState, onDismiss)
    ModalSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetFrame(
            header = { SheetHeader(title = stringResource(R.string.scenarios_new), onClose = { pick {} }) },
            scrollable = false,
        ) {
            ChoiceRow(
                glyph = GLYPH_WIZARD,
                title = stringResource(R.string.ai_chat_choice_wizard),
                detail = stringResource(R.string.ai_chat_choice_wizard_detail),
                experimental = false,
                onClick = { pick(onWizard) },
            )
            ChoiceRow(
                glyph = GLYPH_AI_CHAT,
                title = stringResource(R.string.ai_chat_choice_chat),
                detail = stringResource(R.string.ai_chat_choice_chat_detail),
                experimental = true,
                onClick = { pick(onAiChat) },
            )
            ChoiceRow(
                glyph = GLYPH_PASTE,
                title = stringResource(R.string.ai_chat_choice_paste),
                detail = stringResource(R.string.ai_chat_choice_paste_detail),
                experimental = true,
                onClick = { pick(onPaste) },
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    glyph: String,
    title: String,
    detail: String,
    experimental: Boolean,
    onClick: () -> Unit,
) {
    TonalRow(onClick = onClick) {
        LeadingIconBox(glyph = glyph)
        RowText(
            title = title,
            detail = detail,
            modifier = Modifier.weight(1f),
            badge = if (experimental) {
                { ExperimentalLabel() }
            } else {
                null
            },
        )
    }
}

private const val GLYPH_WIZARD = "format-list-checks"
private const val GLYPH_AI_CHAT = "creation"
private const val GLYPH_PASTE = "content-paste"
