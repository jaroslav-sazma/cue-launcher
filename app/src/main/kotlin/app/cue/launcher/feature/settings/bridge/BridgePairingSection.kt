package app.cue.launcher.feature.settings.bridge

import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cue.launcher.BuildConfig
import app.cue.launcher.R
import app.cue.launcher.designsystem.Message
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.scenarios.JitPermission
import app.cue.launcher.feature.scenarios.JitRequirement
import app.cue.launcher.feature.scenarios.builder.rememberRequirementResolver
import app.cue.launcher.feature.settings.SettingsGrantRequest
import app.cue.launcher.feature.settings.SettingsSwitchRow
import app.cue.launcher.system.bluetoothConnectGranted
import app.cue.launcher.system.bridge.PairingHandle
import app.cue.launcher.system.bridge.PairingProgress
import app.cue.launcher.system.bridge.PairingTarget
import app.cue.launcher.system.localNetworkGranted

// The Mac is found first, in the system's companion dialog, then once more on the network by the
// flag its advertisement carries in pairing mode. Local network access gates the pairing and nothing
// else; nearby devices access is asked for after the dialog, which may have granted it already, and
// refusing it only keeps the Bluetooth link off. Either way the rest of the launcher stays as it
// was, and the notes say what each is for.
@Composable
internal fun BridgePairingSection(
    handle: PairingHandle?,
    search: MacSearch,
    pick: MacPick,
    onFind: (showDialog: (IntentSender) -> Unit) -> Unit,
    onSearchClosed: (picked: Boolean) -> Unit,
    onBrowse: BrowseActions,
    onStart: (PairingTarget, devTransport: Boolean) -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val permissions = rememberRequirementResolver { if (it == JitPermission.LOCAL_NETWORK) denied = true }
    val granted = remember(permissions.tick) { localNetworkGranted(context) }
    val progress = handle?.let { it.progress.collectAsStateWithLifecycle().value }
    val localNetworkRequest: @Composable () -> Unit = {
        if (!granted) {
            LocalNetworkRequest(denied = denied) {
                permissions.resolve(JitRequirement.Grant(JitPermission.LOCAL_NETWORK))
            }
        }
    }

    SectionLabel(title = stringResource(R.string.bridge_section_pair))
    BridgeStep(target = progress, contentKey = { it?.javaClass }, label = "bridge.pairing") { step ->
        when (step) {
            null -> PairingStart(
                search = search,
                pick = pick,
                localNetwork = granted,
                onFind = onFind,
                onSearchClosed = onSearchClosed,
                onBrowse = onBrowse,
                onStart = onStart,
                localNetworkRequest = localNetworkRequest,
            )
            else -> {
                localNetworkRequest()
                PairingStepContent(progress = step, handle = handle, onReset = onReset)
            }
        }
    }
}

@Composable
private fun PairingStepContent(progress: PairingProgress, handle: PairingHandle?, onReset: () -> Unit) {
    val cancel: () -> Unit = { handle?.cancel() }
    when (progress) {
        PairingProgress.Connecting -> PairingWaiting(R.string.bridge_pair_connecting, onCancel = cancel)
        PairingProgress.Requested -> PairingWaiting(R.string.bridge_pair_requested, onCancel = cancel)
        is PairingProgress.Confirming -> SasPanel(sas = progress.sas, onCancel = cancel)
        is PairingProgress.Committed -> Note(text = stringResource(R.string.bridge_pair_committed, progress.macName))
        is PairingProgress.Aborted -> PairingFailed(text = pairingAbortText(progress.reason), onRetry = onReset)
    }
}

// Debug builds may skip both searches and pair over an address typed in, as the emulator must.
@Composable
private fun PairingStart(
    search: MacSearch,
    pick: MacPick,
    localNetwork: Boolean,
    onFind: (showDialog: (IntentSender) -> Unit) -> Unit,
    onSearchClosed: (picked: Boolean) -> Unit,
    onBrowse: BrowseActions,
    onStart: (PairingTarget, devTransport: Boolean) -> Unit,
    localNetworkRequest: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var nearbyDenied by remember { mutableStateOf(false) }
    val permissions = rememberRequirementResolver { if (it == JitPermission.BLUETOOTH) nearbyDenied = true }
    val nearbyGranted = remember(permissions.tick, search) { bluetoothConnectGranted(context) }
    var devTransport by rememberSaveable { mutableStateOf(BuildConfig.DEBUG && !companionCapable(context)) }

    if (BuildConfig.DEBUG) {
        SettingsSwitchRow(
            label = stringResource(R.string.bridge_dev_transport),
            detail = stringResource(R.string.bridge_dev_transport_detail),
            checked = devTransport,
            onCheckedChange = { devTransport = it },
        )
    }
    if (!devTransport) {
        MacFinder(search = search, onFind = onFind, onSearchClosed = onSearchClosed)
        if (search is MacSearch.Found && !nearbyGranted) {
            NearbyRequest(denied = nearbyDenied) {
                permissions.resolve(JitRequirement.Grant(JitPermission.BLUETOOTH))
            }
        }
    }
    localNetworkRequest()
    when {
        devTransport -> DevPairing(enabled = localNetwork, onPair = { onStart(it, true) })
        search is MacSearch.Found && localNetwork ->
            MacBrowser(pick = pick, actions = onBrowse, onPick = { onStart(it, false) })
    }
}

@Composable
internal fun NearbyRequest(denied: Boolean, onGrant: () -> Unit) {
    SettingsGrantRequest(
        action = stringResource(R.string.bridge_nearby_grant),
        onGrant = onGrant,
        note = stringResource(R.string.bridge_nearby_note),
        message = if (denied) stringResource(R.string.bridge_nearby_denied) else null,
    )
}

@Composable
private fun LocalNetworkRequest(denied: Boolean, onGrant: () -> Unit) {
    SettingsGrantRequest(
        action = stringResource(R.string.bridge_local_network_grant),
        onGrant = onGrant,
        note = stringResource(R.string.bridge_local_network_note),
        message = if (denied) stringResource(R.string.bridge_local_network_denied) else null,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PairingWaiting(@StringRes text: Int, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.fieldGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoadingIndicator()
        Text(
            text = stringResource(text),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) {
            Text(text = stringResource(R.string.bridge_cancel))
        }
    }
}

// Two groups of three are what both screens show; the description spells the digits out so a
// screen reader does not read them as two numbers.
@Composable
private fun SasPanel(sas: String, onCancel: () -> Unit) {
    val spoken = sas.toList().joinToString(" ")
    Note(text = stringResource(R.string.bridge_sas_note))
    Text(
        text = "${sas.take(SAS_GROUP)} ${sas.drop(SAS_GROUP)}",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.screenPadding)
            .semantics { contentDescription = spoken },
        style = MaterialTheme.typography.displayMediumEmphasized,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
    PairingWaiting(R.string.bridge_sas_waiting, onCancel = onCancel)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PairingFailed(text: String, onRetry: () -> Unit) {
    Message(text = text)
    Note(text = stringResource(R.string.bridge_pair_retry_note))
    Row(modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding)) {
        OutlinedButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
            Text(text = stringResource(R.string.bridge_pair_retry))
        }
    }
}

// The emulator has neither, so a debug build there starts with the pairing over the dev endpoint.
internal fun companionCapable(context: Context): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP) &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

private const val SAS_GROUP = 3
