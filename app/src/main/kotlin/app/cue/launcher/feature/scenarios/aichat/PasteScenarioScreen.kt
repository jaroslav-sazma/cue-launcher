package app.cue.launcher.feature.scenarios.aichat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleStartEffect
import app.cue.launcher.R
import app.cue.launcher.data.aichat.CachedInventory
import app.cue.launcher.data.aichat.CandidateCheck
import app.cue.launcher.data.aichat.MAX_PASTED_CHARS
import app.cue.launcher.data.aichat.bridgeNeededBy
import app.cue.launcher.data.aichat.haNeedsOf
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.placeholderKeysIn
import app.cue.launcher.designsystem.DetailPaneScaffold
import app.cue.launcher.designsystem.MediumOutlinedButton
import app.cue.launcher.designsystem.Message
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.codeStyle
import app.cue.launcher.feature.scenarios.ImportExtractLine
import app.cue.launcher.feature.scenarios.ImportTapLines
import app.cue.launcher.feature.scenarios.extractDisclosuresOf
import app.cue.launcher.feature.scenarios.tapDisclosuresOf
import app.cue.launcher.system.aichat.aiChatCorrection

// Nothing is saved here: a valid scenario only opens in the wizard, where the user saves it. The
// check reads the phone, so it runs while the screen is in front; a check cut short by leaving runs
// again on return, as does one whose result went with the composition on a detour.
@Suppress("LongParameterList")
@Composable
fun PasteScenarioScreen(
    inventory: CachedInventory,
    haConfigured: Boolean,
    onConfigureHa: () -> Unit,
    bridgePaired: Boolean,
    onConfigureBridge: () -> Unit,
    onBack: () -> Unit,
    onOpenDraft: (canonicalJson: String) -> Unit,
    modifier: Modifier = Modifier,
    onDraftOpened: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val opened by rememberUpdatedState(onDraftOpened)
    val state = rememberSaveable(inventory, scope, saver = PasteState.saver(inventory, scope) { opened() }) {
        PasteState.create(inventory, scope, onDraftOpened = { opened() })
    }
    LifecycleStartEffect(state) {
        state.start()
        onStopOrDispose { state.stop() }
    }

    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val resultView = remember { BringIntoViewRequester() }
    var reveal by remember { mutableStateOf(false) }
    RevealResult(reveal = reveal, result = state.result, requester = resultView) { reveal = false }

    val valid = (state.result as? CandidateCheck.Valid)?.scenario
    DetailPaneScaffold(
        title = stringResource(R.string.ai_chat_paste_title),
        subtitle = stringResource(R.string.experimental_label),
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            if (valid == null) {
                val check = {
                    focus.clearFocus()
                    keyboard?.hide()
                    reveal = true
                    state.check()
                }
                AiChatBottomBar(BarButton(stringResource(R.string.ai_chat_check), check, state.canCheck))
            } else {
                val open = BarButton(stringResource(R.string.ai_chat_open_draft), { state.openDraft(onOpenDraft) })
                AiChatBottomBar(open)
            }
        },
    ) {
        AiChatBody {
            Note(text = stringResource(R.string.ai_chat_paste_note))
            PasteField(text = state.text, onEdit = state::edit) { clipboardText(context)?.let(state::edit) }
            if (state.truncated) Message(text = stringResource(R.string.ai_chat_paste_truncated, MAX_PASTED_CHARS))
            when (val current = state.result) {
                null -> if (state.checking) Checking()
                is CandidateCheck.Valid -> ResultSection(resultView) {
                    ValidDraft(current.scenario, haConfigured, onConfigureHa, bridgePaired, onConfigureBridge)
                }
                is CandidateCheck.Invalid -> ResultSection(resultView) {
                    Problems(current) { copyText(context, aiChatCorrection(current.problems)) }
                }
            }
        }
    }
}

// Only a check the user started scrolls: a result computed again on return would otherwise pull the
// screen away from where it was left. The frame waits for the new section to be laid out.
@Composable
private fun RevealResult(
    reveal: Boolean,
    result: CandidateCheck?,
    requester: BringIntoViewRequester,
    onRevealed: () -> Unit,
) {
    LaunchedEffect(reveal, result) {
        if (!reveal || result == null) return@LaunchedEffect
        withFrameNanos {}
        requester.bringIntoView()
        onRevealed()
    }
}

@Composable
private fun ResultSection(requester: BringIntoViewRequester, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.cardGap),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColumnScope.Checking() {
    LoadingIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
}

@Composable
private fun ValidDraft(
    scenario: Scenario,
    haConfigured: Boolean,
    onConfigureHa: () -> Unit,
    bridgePaired: Boolean,
    onConfigureBridge: () -> Unit,
) {
    val widgetKeys = remember(scenario) {
        (scenario.content as? TileContent.AppWidget)?.let { placeholderKeysIn(it.appWidgetId) }.orEmpty().toSet()
    }
    val placeholders = remember(scenario) { placeholderKeysIn(scenario) - widgetKeys }
    val taps = remember(scenario) { tapDisclosuresOf(scenario.content) }
    val extracts = remember(scenario) { extractDisclosuresOf(scenario) }
    SectionLabel(title = stringResource(R.string.ai_chat_preview_title))
    DraftSummary(scenario)
    DraftNotes(
        needs = haNeedsOf(scenario, haConfigured),
        placeholders = placeholders,
        widgetUnbound = widgetKeys.isNotEmpty(),
        bridgeNeeded = bridgeNeededBy(scenario, bridgePaired),
        onConnectHa = onConfigureHa,
        onConfigureBridge = onConfigureBridge,
    )
    if (taps.isNotEmpty() || extracts.isNotEmpty()) {
        SectionLabel(title = stringResource(R.string.ai_chat_runs_title))
        Note(text = stringResource(R.string.ai_chat_runs_note))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
        ) {
            extracts.forEach { condition -> ImportExtractLine(condition) }
            taps.forEach { tap -> ImportTapLines(tap) }
        }
    }
}

@Composable
private fun Problems(invalid: CandidateCheck.Invalid, onCopyFix: () -> Unit) {
    SectionLabel(title = stringResource(R.string.ai_chat_problems_title))
    invalid.problems.forEach { problem ->
        Message(text = stringResource(R.string.ai_chat_problem_item, problemText(problem)))
    }
    Note(text = stringResource(R.string.ai_chat_fix_note))
    MediumOutlinedButton(
        label = stringResource(R.string.ai_chat_copy_fix),
        onClick = onCopyFix,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.screenPadding),
    )
}

@Composable
private fun PasteField(text: String, onEdit: (String) -> Unit, onPaste: () -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        textStyle = codeStyle,
        label = { Text(text = stringResource(R.string.ai_chat_paste_label)) },
        minLines = FIELD_MIN_LINES,
        maxLines = FIELD_MAX_LINES,
    )
    MediumOutlinedButton(
        label = stringResource(R.string.ai_chat_paste_clipboard),
        onClick = onPaste,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.screenPadding),
    )
}

private const val FIELD_MIN_LINES = 6
private const val FIELD_MAX_LINES = 12
