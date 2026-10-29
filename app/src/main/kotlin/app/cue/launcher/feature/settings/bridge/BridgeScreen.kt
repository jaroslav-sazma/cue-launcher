package app.cue.launcher.feature.settings.bridge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cue.launcher.BuildConfig
import app.cue.launcher.R
import app.cue.launcher.data.bridge.BridgePairingRecord
import app.cue.launcher.data.bridge.BridgeSettings
import app.cue.launcher.data.bridge.CLIPBOARD_MAX_AGE_MINUTES
import app.cue.launcher.designsystem.DetailPaneScaffold
import app.cue.launcher.designsystem.Message
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.effectsFastSpec
import app.cue.launcher.designsystem.rememberFlashState
import app.cue.launcher.designsystem.rememberReducedMotion
import app.cue.launcher.designsystem.spatialDefaultSpec
import app.cue.launcher.feature.scenarios.JitPermission
import app.cue.launcher.feature.scenarios.JitRequirement
import app.cue.launcher.feature.scenarios.builder.rememberRequirementResolver
import app.cue.launcher.feature.settings.SettingsGrantRequest
import app.cue.launcher.feature.settings.SettingsLinkRow
import app.cue.launcher.feature.settings.SettingsRow
import app.cue.launcher.feature.settings.SettingsSwitchRow
import app.cue.launcher.system.bluetoothConnectGranted
import app.cue.launcher.system.bridge.BridgeLinkState
import app.cue.launcher.system.bridge.MacPresence
import app.cue.launcher.system.notifications.notificationListenerSettingsIntent
import kotlin.math.roundToInt

// The services are built here, on first opening, and not on the way to the home screen.
@Composable
internal fun BridgeScreen(onClose: () -> Unit, onOpenApps: () -> Unit, modifier: Modifier = Modifier) {
    val provide = LocalBridgeServices.current
    val services = remember(provide) { provide() }
    val scope = rememberCoroutineScope()
    val flash = rememberFlashState()
    val state = remember(services, scope, flash) { BridgeScreenState(services, scope, flash) }
    val stored by remember(state) { state.settings }.collectAsStateWithLifecycle(initialValue = null)
    val link by state.link.collectAsStateWithLifecycle()
    val nearby by state.nearby.collectAsStateWithLifecycle()
    val listener by state.notificationListener.collectAsStateWithLifecycle()
    val pairing = stored?.pairing
    val unusable by produceState(false, pairing) { value = pairing != null && state.pairingUnusable() }

    LifecycleStartEffect(state.handle) {
        val running = state.handle
        onStopOrDispose { running?.cancel() }
    }

    DetailPaneScaffold(
        title = stringResource(R.string.bridge_title),
        onBack = onClose,
        modifier = modifier,
        flash = flash,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
        ) {
            stored?.let { settings ->
                BridgeContent(
                    settings = settings,
                    link = link,
                    nearby = nearby,
                    notificationAccess = listener.enabled,
                    unusable = unusable,
                    state = state,
                    onOpenApps = onOpenApps,
                )
            }
        }
    }

    state.confirm?.let { asked ->
        BridgeConfirmDialog(
            confirm = asked,
            onConfirm = { state.confirmed(asked) },
            onDismiss = { state.confirm = null },
        )
    }
}

@Composable
private fun BridgeContent(
    settings: BridgeSettings,
    link: BridgeLinkState,
    nearby: MacPresence,
    notificationAccess: Boolean,
    unusable: Boolean,
    state: BridgeScreenState,
    onOpenApps: () -> Unit,
) {
    SettingsSwitchRow(
        label = stringResource(R.string.bridge_switch),
        detail = stringResource(R.string.bridge_switch_detail),
        checked = settings.enabled,
        onCheckedChange = { on -> if (on) state.enable() else state.requestOff(settings) },
    )
    val pairing = settings.pairing
    when {
        !settings.enabled -> Note(text = stringResource(R.string.bridge_off_note))
        pairing == null -> BridgePairingSection(
            handle = state.handle,
            search = state.search,
            pick = state.pick,
            onFind = state::findMac,
            onSearchClosed = state::searchClosed,
            onBrowse = remember(state) {
                BrowseActions(start = state::browseIfIdle, again = state::browse, stop = state::stopBrowse)
            },
            onStart = state::startPairing,
            onReset = state::resetPairing,
        )
        else -> {
            val companion = pairing.association != null && settings.devEndpoint == null
            BridgePairedSection(
                pairing = pairing,
                link = link,
                nearby = nearby.takeIf { companion },
                endpointMissing = BuildConfig.DEBUG && settings.devEndpoint == null && pairing.association == null,
                unusable = unusable,
                onUnpair = { state.requestUnpair(pairing.macName) },
                onPairAgain = state::pairAgain,
            )
            if (companion) BridgeNearbyAccess()
            BridgeNotificationsSection(excluded = settings.mirrorExcluded.size, onOpenApps = onOpenApps)
            BridgeClipboardSection(
                fromMac = settings.clipboardFromMac,
                maxAgeMinutes = settings.clipboardMaxAgeMinutes,
                onFromMacChange = state::setClipboardFromMac,
                onMaxAgeChange = state::setClipboardMaxAge,
            )
            if (!notificationAccess) BridgeNotificationAccess()
            if (BuildConfig.DEBUG) {
                BridgeDebugSection(
                    endpoint = settings.devEndpoint,
                    link = link,
                    onSave = state::saveEndpoint,
                    onSendTest = state::sendTestSignal,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BridgePairedSection(
    pairing: BridgePairingRecord,
    link: BridgeLinkState,
    nearby: MacPresence?,
    endpointMissing: Boolean,
    unusable: Boolean,
    onUnpair: () -> Unit,
    onPairAgain: () -> Unit,
) {
    SectionLabel(title = stringResource(R.string.bridge_section_paired))
    SettingsRow(
        label = pairing.macName,
        detail = stringResource(R.string.bridge_paired_address, pairing.host, pairing.port),
    )
    SettingsRow(
        label = stringResource(R.string.bridge_paired_certificate),
        detail = pairing.macFingerprint.take(FINGERPRINT_PREFIX),
    )
    if (unusable) {
        UnusablePairing(onPairAgain = onPairAgain, onUnpair = onUnpair)
        return
    }
    if (nearby != null) {
        SettingsRow(
            label = stringResource(R.string.bridge_paired_presence),
            detail = stringResource(
                when (nearby) {
                    MacPresence.ABSENT -> R.string.bridge_presence_absent
                    MacPresence.NEARBY -> R.string.bridge_presence_nearby
                    MacPresence.LINKED -> R.string.bridge_presence_linked
                },
            ),
        )
    }
    SettingsRow(
        label = stringResource(R.string.bridge_paired_connection),
        detail = bridgeLinkText(link, endpointMissing),
    )
    if (link == BridgeLinkState.REJECTED) Message(text = stringResource(R.string.bridge_link_rejected_note))
    Note(text = stringResource(R.string.bridge_unpair_note))
    Row(modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding)) {
        OutlinedButton(onClick = onUnpair, shapes = ButtonDefaults.shapes()) {
            Text(text = stringResource(R.string.bridge_unpair))
        }
    }
}

// The phone refuses a pairing whose sequence state or key is lost, and nothing but a new pairing
// makes it usable again.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UnusablePairing(onPairAgain: () -> Unit, onUnpair: () -> Unit) {
    SettingsRow(
        label = stringResource(R.string.bridge_paired_connection),
        detail = stringResource(R.string.bridge_unusable),
    )
    Message(text = stringResource(R.string.bridge_unusable_note))
    Row(
        modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.fieldGap),
    ) {
        Button(onClick = onPairAgain, shapes = ButtonDefaults.shapes()) {
            Text(text = stringResource(R.string.bridge_pair_again))
        }
        OutlinedButton(onClick = onUnpair, shapes = ButtonDefaults.shapes()) {
            Text(text = stringResource(R.string.bridge_unpair))
        }
    }
}

@Composable
private fun BridgeNotificationsSection(excluded: Int, onOpenApps: () -> Unit) {
    SectionLabel(title = stringResource(R.string.bridge_section_notifications))
    SettingsLinkRow(
        label = stringResource(R.string.bridge_apps),
        detail = stringResource(R.string.bridge_apps_detail),
        value = stringResource(R.string.bridge_apps_count, excluded),
        onClick = onOpenApps,
    )
    Note(text = stringResource(R.string.bridge_private_note))
}

@Composable
private fun BridgeClipboardSection(
    fromMac: Boolean,
    maxAgeMinutes: Int,
    onFromMacChange: (Boolean) -> Unit,
    onMaxAgeChange: (Int) -> Unit,
) {
    SectionLabel(title = stringResource(R.string.bridge_section_clipboard))
    SettingsSwitchRow(
        label = stringResource(R.string.bridge_clipboard_mac),
        detail = stringResource(R.string.bridge_clipboard_mac_detail),
        checked = fromMac,
        onCheckedChange = onFromMacChange,
    )
    val reducedMotion = rememberReducedMotion()
    val fade = effectsFastSpec()
    val resize = spatialDefaultSpec<IntSize>(reducedMotion)
    AnimatedVisibility(
        visible = fromMac,
        enter = fadeIn(fade) + expandVertically(resize),
        exit = fadeOut(fade) + shrinkVertically(resize),
        label = "bridge.clipboard.age",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap)) {
            ClipboardMaxAge(minutes = maxAgeMinutes, onChange = onMaxAgeChange)
            Note(text = stringResource(R.string.bridge_clipboard_unlock_note))
        }
    }
    Note(text = stringResource(R.string.bridge_clipboard_note))
}

// The stored value changes only when the drag ends, so the settings file is written once per drag.
@Composable
private fun ClipboardMaxAge(minutes: Int, onChange: (Int) -> Unit) {
    val slider = rememberSliderState(
        value = minutes.toFloat(),
        trackRange = CLIPBOARD_MAX_AGE_MINUTES.first.toFloat()..CLIPBOARD_MAX_AGE_MINUTES.last.toFloat(),
    )
    LaunchedEffect(minutes) { if (!slider.isDragging) slider.value = minutes.toFloat() }
    val dragged = slider.value.roundToInt()
    val label = pluralStringResource(R.plurals.bridge_clipboard_max_age, dragged, dragged)
    Column(
        modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.textSpacing),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Slider(
            state = slider,
            onValueChange = { slider.value = it },
            onValueChangeFinished = { onChange(slider.value.roundToInt()) },
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

// Refused at pairing or revoked since: the bridge still works, and this says what is missing.
@Composable
private fun BridgeNearbyAccess() {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val permissions = rememberRequirementResolver { if (it == JitPermission.BLUETOOTH) denied = true }
    val granted = remember(permissions.tick) { bluetoothConnectGranted(context) }
    if (granted) return
    SectionLabel(title = stringResource(R.string.permission_bluetooth_label))
    NearbyRequest(denied = denied) { permissions.resolve(JitRequirement.Grant(JitPermission.BLUETOOTH)) }
}

// Special access has no request dialog, so the grant is the system screen the scenarios open too.
@Composable
private fun BridgeNotificationAccess() {
    val context = LocalContext.current
    SectionLabel(title = stringResource(R.string.permission_notifications_label))
    SettingsGrantRequest(
        action = stringResource(R.string.permission_notifications_open),
        onGrant = { context.startActivity(notificationListenerSettingsIntent(context)) },
        message = stringResource(R.string.bridge_notification_access_note),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BridgeConfirmDialog(confirm: BridgeConfirm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val turnOff = confirm is BridgeConfirm.TurnOff
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (turnOff) {
                    stringResource(R.string.bridge_off_title)
                } else {
                    stringResource(R.string.bridge_unpair_title, confirm.macName)
                },
            )
        },
        text = {
            Text(
                text = stringResource(
                    if (turnOff) R.string.bridge_off_detail else R.string.bridge_unpair_detail,
                    confirm.macName,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(if (turnOff) R.string.bridge_off_confirm else R.string.bridge_unpair))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.bridge_cancel))
            }
        },
    )
}

private const val FINGERPRINT_PREFIX = 8
