package app.cue.launcher.feature.settings.ha

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.ha.HaConfig
import app.cue.launcher.data.ha.HaConfigRepository
import app.cue.launcher.data.ha.HaEventLogRepository
import app.cue.launcher.data.scenario.ScenarioRepository
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.system.ha.HaBlueprintUploader
import app.cue.launcher.system.ha.HaEndpointSelector
import kotlinx.coroutines.launch
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.LeadingIconBox
import app.cue.launcher.designsystem.DetailPaneScaffold
import app.cue.launcher.designsystem.Message
import app.cue.launcher.designsystem.FlashState
import app.cue.launcher.designsystem.rememberFlashState

@Composable
internal fun HomeAssistantScreen(
    configs: HaConfigRepository,
    scenarios: ScenarioRepository,
    log: HaEventLogRepository,
    setup: HaSetup,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screen = remember { HaScreenState() }
    val flash = rememberFlashState()

    val stored by remember(configs) { configs.config }.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    HaScreenEffects(screen = screen, stored = stored, selector = setup.selector)

    var leaving by remember { mutableStateOf(false) }

    // Leaving while a save still ends the old session would cancel it with the screen's scope, and
    // the warning that it could not be ended would never be seen.
    val leave = { if (screen.saving) Unit else if (screen.dirty) leaving = true else onClose() }
    val save = { scope.launch { saveConfig(screen, configs, setup, flash) } }

    BackHandler(enabled = screen.dirty || screen.saving) { leave() }

    DetailPaneScaffold(
        title = stringResource(R.string.ha_title),
        onBack = leave,
        modifier = modifier,
        flash = flash,
        bottomBar = {
            HaSaveBar(
                screen = screen,
                onSave = { save() },
                onClearToken = { scope.launch { forget(screen, configs, setup, flash) } },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
        ) {
            HaSections(
                screen = screen,
                configs = configs,
                scenarios = scenarios,
                log = log,
                uploader = setup.blueprint,
                flash = flash,
                onDiscover = { scope.launch { discover(screen, configs, setup, flash) } },
                onSignIn = { scope.launch { signIn(screen, configs, setup, context, flash) } },
                onTest = { scope.launch { runProbe(screen, setup.tester, stored) } },
            )
        }
    }

    if (leaving) {
        HaLeaveDialog(
            canSave = screen.canSave,
            onSave = {
                leaving = false
                scope.launch { saveConfig(screen, configs, setup, flash, onSaved = onClose) }
            },
            onDiscard = {
                leaving = false
                onClose()
            },
            onDismiss = { leaving = false },
        )
    }
}

@Composable
private fun HaScreenEffects(
    screen: HaScreenState,
    stored: HaConfig?,
    selector: HaEndpointSelector,
) {
    LaunchedEffect(stored) {
        stored?.takeIf { !screen.loaded }?.let(screen::fill)
    }

    LaunchedEffect(stored, screen.homeNetworksText, screen.probes, screen.wifiTick) {
        val saved = stored ?: return@LaunchedEffect
        screen.network = selector.network()
        screen.nowUsing = selector.endpoints(saved).firstOrNull()
    }
}

@Composable
private fun HaSections(
    screen: HaScreenState,
    configs: HaConfigRepository,
    scenarios: ScenarioRepository,
    log: HaEventLogRepository,
    uploader: HaBlueprintUploader,
    flash: FlashState,
    onDiscover: () -> Unit,
    onSignIn: () -> Unit,
    onTest: () -> Unit,
) {
    HaConnectionSection(
        screen = screen,
        onDiscover = onDiscover,
        onSignIn = onSignIn,
        onTest = onTest,
    )
    HaHomeWifiSection(screen = screen)
    HaEventsSection(configs = configs, send = rememberHaBlueprintSend(uploader, flash), flash = flash)
    HaCodesSection(screen = screen, scenarios = scenarios, log = log, flash = flash)
}

// The bar only shows while something is unsaved: a permanent one would read as an action the screen
// is waiting for even when there is nothing to save.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HaSaveBar(
    screen: HaScreenState,
    onSave: () -> Unit,
    onClearToken: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showBar = screen.dirty || screen.saving || screen.tokenStored || screen.sessionStored
    AnimatedVisibility(visible = showBar, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = FloatingToolbarDefaults.ScreenOffset),
            verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
        ) {
            screen.saveBlocker?.takeIf { screen.dirty }?.let {
                Message(text = stringResource(it))
            }
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    .padding(horizontal = FloatingToolbarDefaults.ScreenOffset)
                    .align(Alignment.CenterHorizontally),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap)) {
                    if (screen.dirty || screen.saving) {
                        Button(
                            onClick = onSave,
                            modifier = Modifier.weight(1f),
                            enabled = screen.canSave && !screen.saving,
                        ) {
                            Text(text = stringResource(if (screen.saving) R.string.ha_saving else R.string.ha_save))
                        }
                    }
                    if (screen.tokenStored || screen.sessionStored) {
                        OutlinedButton(onClick = onClearToken, enabled = !screen.saving) {
                            Text(text = stringResource(R.string.ha_token_clear))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HaLeaveDialog(
    canSave: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.ha_leave_title)) },
        text = { Text(text = stringResource(R.string.ha_leave_detail)) },
        confirmButton = {
            if (canSave) {
                TextButton(onClick = onSave) { Text(text = stringResource(R.string.ha_leave_save)) }
            } else {
                TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.ha_leave_stay)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) { Text(text = stringResource(R.string.ha_leave_discard)) }
        },
    )
}

@Composable
private fun HaEventsSection(
    configs: HaConfigRepository,
    send: HaBlueprintSend,
    flash: FlashState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.textSpacing),
    ) {
        SectionLabel(title = stringResource(R.string.ha_section_events))
        TonalRow(onClick = null) {
            LeadingIconBox(glyph = GLYPH_COMPANION)
            RowText(
                title = stringResource(R.string.ha_events_row),
                modifier = Modifier.weight(1f),
                detail = stringResource(R.string.ha_events_row_detail),
            )
        }
        HaSecretRow(configs = configs, flash = flash, onUpload = send::start)
        HaBlueprintRow(send = send)
    }
}

// Leaving with Save closes the screen through onSaved, only once the save is through: closing first
// would cancel it with the screen's scope, and a failure would be reported on a screen already gone.
// The same goes for an old session that could not be ended: the screen then stays, with the warning.
//
// A pasted token replaces a signed-in session, which is then ended in Home Assistant too — after the
// save, against the addresses it was made for, so a failed save never leaves a revoked credential.
private suspend fun saveConfig(
    screen: HaScreenState,
    configs: HaConfigRepository,
    setup: HaSetup,
    flash: FlashState,
    onSaved: (() -> Unit)? = null,
) {
    if (screen.saving) return
    screen.saving = true
    try {
        storeConfig(screen, configs, setup, flash, onSaved)
    } finally {
        screen.saving = false
    }
}

private suspend fun storeConfig(
    screen: HaScreenState,
    configs: HaConfigRepository,
    setup: HaSetup,
    flash: FlashState,
    onSaved: (() -> Unit)?,
): Unit = with(screen) {
    val previous = configs.config()
    val replacing = typedToken.isNotBlank()
    val ok = configs.save(
        urlInternal = urlInternal,
        urlExternal = urlExternal,
        token = typedToken,
        homeNetworks = homeNetworks,
    )
    if (ok) {
        setup.selector.invalidate()
        if (replacing) {
            tokenStored = true
            sessionStored = false
        }
        token.clearText()
        probes = emptyMap()
        markSaved()
        val ended = !replacing || setup.authFlow.signOut(previous)
        when {
            !ended -> flash.show(R.string.ha_revoke_failed, error = true)
            onSaved != null -> onSaved()
            else -> flash.show(R.string.ha_saved)
        }
    } else {
        flash.show(R.string.ha_save_failed, error = true)
    }
}

private const val GLYPH_COMPANION = "access-point"
