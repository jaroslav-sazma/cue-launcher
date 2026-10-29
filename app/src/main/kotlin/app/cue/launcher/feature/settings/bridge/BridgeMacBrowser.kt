package app.cue.launcher.feature.settings.bridge

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.LifecycleStartEffect
import app.cue.launcher.R
import app.cue.launcher.data.bridge.BRIDGE_DEV_HOST
import app.cue.launcher.designsystem.LeadingIconBox
import app.cue.launcher.designsystem.Message
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.effectsFastSpec
import app.cue.launcher.designsystem.rememberReducedMotion
import app.cue.launcher.designsystem.spatialDefaultSpec
import app.cue.launcher.feature.settings.SettingsRow
import app.cue.launcher.system.bridge.PairingTarget

// The dialog is the system's: it scans for the advertisement on its own and returns the pick, so
// the launcher needs no scan permission. A refused or cancelled dialog leaves the row to try again.
@Composable
internal fun MacFinder(
    search: MacSearch,
    onFind: (showDialog: (IntentSender) -> Unit) -> Unit,
    onSearchClosed: (picked: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val capable = remember(context) { companionCapable(context) }
    val dialog = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        onSearchClosed(result.resultCode == Activity.RESULT_OK)
    }
    val showDialog: (IntentSender) -> Unit = { sender ->
        try {
            dialog.launch(IntentSenderRequest.Builder(sender).build())
        } catch (_: IllegalStateException) {
            onSearchClosed(false)
        }
    }
    BridgeStep(target = search, contentKey = { it.step }, label = "bridge.find") { step ->
        val idle = step == MacSearch.Idle || step is MacSearch.Failed
        if (idle) Note(text = stringResource(R.string.bridge_find_note))
        FindMacRow(search = step, onClick = { onFind(showDialog) }.takeIf { idle && capable })
        when {
            !idle -> Unit
            !capable -> Message(text = stringResource(R.string.bridge_find_unavailable))
            step is MacSearch.Failed && step.cancelled -> Message(text = stringResource(R.string.bridge_find_cancelled))
            step is MacSearch.Failed -> Message(text = stringResource(R.string.bridge_find_failed))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FindMacRow(search: MacSearch, onClick: (() -> Unit)?) {
    val detail = when (search) {
        MacSearch.Searching -> stringResource(R.string.bridge_find_searching)
        is MacSearch.Found -> stringResource(R.string.bridge_find_found)
        else -> null
    }
    TonalRow(onClick = onClick) {
        when (search) {
            MacSearch.Searching -> LoadingIndicator(modifier = Modifier.size(Tokens.Settings.leadingBox))
            is MacSearch.Found -> LeadingIconBox(glyph = GLYPH_FOUND)
            else -> LeadingIconBox(glyph = GLYPH_FIND, enabled = onClick != null)
        }
        RowText(
            title = stringResource(R.string.bridge_find),
            modifier = Modifier.weight(1f),
            detail = detail,
        )
    }
}

private val MacSearch.step: Int
    get() = when (this) {
        MacSearch.Idle, is MacSearch.Failed -> 0
        MacSearch.Searching -> 1
        is MacSearch.Found -> 2
    }

// A step replaces the one before it in place: the old one fades out as the new one fades in, and
// the height follows from one to the other.
@Composable
internal fun <S> BridgeStep(
    target: S,
    contentKey: (S) -> Any?,
    label: String,
    content: @Composable ColumnScope.(S) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val fade = effectsFastSpec()
    val resize = spatialDefaultSpec<IntSize>(reducedMotion)
    AnimatedContent(
        targetState = target,
        modifier = Modifier.fillMaxWidth(),
        contentKey = contentKey,
        transitionSpec = {
            (fadeIn(fade) togetherWith fadeOut(fade)).using(SizeTransform { _, _ -> resize })
        },
        label = label,
    ) { step ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
        ) {
            content(step)
        }
    }
}

// The browse follows the screen's start and stop, like everything else that listens on the network.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MacBrowser(pick: MacPick, actions: BrowseActions, onPick: (PairingTarget) -> Unit) {
    LifecycleStartEffect(actions) {
        actions.start()
        onStopOrDispose { actions.stop() }
    }
    BridgeStep(target = pick, contentKey = { it.step }, label = "bridge.browse") { step ->
        when (step) {
            MacPick.Idle, MacPick.Browsing -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.Settings.surfacePadding),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.fieldGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoadingIndicator()
                Text(
                    text = stringResource(R.string.bridge_browse_searching),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            MacPick.None -> {
                Message(text = stringResource(R.string.bridge_browse_none))
                Row(modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding)) {
                    Button(onClick = actions.again, shapes = ButtonDefaults.shapes()) {
                        Text(text = stringResource(R.string.bridge_browse_again))
                    }
                }
            }
            is MacPick.Several -> {
                Note(text = stringResource(R.string.bridge_browse_several))
                step.targets.forEach { target ->
                    SettingsRow(
                        label = target.name,
                        detail = stringResource(R.string.bridge_paired_address, target.host, target.port),
                        onClick = { onPick(target) },
                    )
                }
            }
        }
    }
}

private val MacPick.step: Int
    get() = when (this) {
        MacPick.Idle, MacPick.Browsing -> 0
        MacPick.None -> 1
        is MacPick.Several -> 2
    }

// Debug builds only: the emulator sees neither the Mac's Bluetooth nor its Bonjour.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DevPairing(enabled: Boolean, onPair: (PairingTarget) -> Unit) {
    var host by rememberSaveable { mutableStateOf(BRIDGE_DEV_HOST) }
    var port by rememberSaveable { mutableStateOf("") }
    val portNumber = devPortOf(port)
    val address = host.trim()
    Note(text = stringResource(R.string.bridge_dev_pair_note, BRIDGE_DEV_HOST))
    DevEndpointFields(host = host, port = port, onHost = { host = it }, onPort = { port = it })
    Row(modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding)) {
        Button(
            onClick = { portNumber?.let { onPair(PairingTarget(address, address, it)) } },
            shapes = ButtonDefaults.shapes(),
            enabled = enabled && portNumber != null && address.isNotEmpty(),
        ) {
            Text(text = stringResource(R.string.bridge_pair))
        }
    }
}

private const val GLYPH_FIND = "laptop"

private const val GLYPH_FOUND = "check-circle"
