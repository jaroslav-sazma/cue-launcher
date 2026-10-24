package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.RingerModeState
import app.cue.launcher.data.scenario.WifiMode
import app.cue.launcher.data.scenario.withNegate
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.engine.SystemState
import app.cue.launcher.feature.scenarios.DaysPhrase
import app.cue.launcher.feature.scenarios.JitRequirement
import app.cue.launcher.feature.scenarios.conditionPhrase
import app.cue.launcher.feature.scenarios.conditionPhraseText
import app.cue.launcher.system.ConnectedBtDevice
import app.cue.launcher.system.bluetoothConnectGranted
import app.cue.launcher.system.connectedBluetoothDevices
import app.cue.launcher.system.pairedBluetoothDevices
import app.cue.launcher.system.pairedNotConnected
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Note

@Suppress("CyclomaticComplexMethod")
@Composable
internal fun ConditionFormBody(
    condition: Condition,
    state: SystemState,
    permissionTick: Int,
    onPickApp: (() -> Unit)?,
    onChange: (Condition) -> Unit,
    modifier: Modifier = Modifier,
    onResolve: (JitRequirement) -> Unit = {},
    ha: HaAccess = HaAccess.NOT_CONFIGURED,
    haRead: HaEntityRead? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        if (condition is Condition.StateCondition) {
            HoldsForm(condition, onChange)
        }
        when (condition) {
            is Condition.TimeWindow -> TimeWindowForm(condition, onChange)
            is Condition.TimeInstant, is Condition.Alarm -> MomentForm(condition, onChange)
            is Condition.Wifi -> WifiForm(condition, state, onChange)
            is Condition.BtConnected ->
                BluetoothForm(condition.mac, permissionTick) { onChange(condition.copy(mac = it)) }

            is Condition.BtDisconnected ->
                BluetoothForm(condition.mac, permissionTick) { onChange(condition.copy(mac = it)) }
            is Condition.RingerMode -> RingerForm(condition, onChange)
            is Condition.HaEvent -> HaEventForm(condition, onChange)
            is Condition.NotificationFrom -> NotificationForm(
                condition = condition,
                onPickApp = onPickApp,
                onChange = onChange,
                access = remember(permissionTick, onResolve) { ExtractAccess(permissionTick, onResolve) },
            )
            is Condition.MediaPlaying -> MediaPlayingForm(condition, onChange)
            is Condition.HaState -> HaStateForm(condition, ha, haRead, onChange)
            is Condition.NotificationEnded -> NotificationEndedForm(condition, onPickApp, onChange)
            is Condition.PlaceSaved -> Note(text = stringResource(R.string.builder_place_saved_note))
            else -> Unit
        }
    }
}

// Only state conditions can be negated — an event either fires or it does not.
@Composable
private fun HoldsForm(
    condition: Condition.StateCondition,
    onChange: (Condition) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.builder_holds_when))
        ConnectedChoiceRow(
            options = listOf(
                ConnectedChoice(
                    label = conditionSegmentLabel(condition.withNegate(false)),
                    selected = !condition.negate,
                    onSelect = { onChange(condition.withNegate(false)) },
                ),
                ConnectedChoice(
                    label = conditionSegmentLabel(condition.withNegate(true)),
                    selected = condition.negate,
                    onSelect = { onChange(condition.withNegate(true)) },
                ),
            ),
        )
    }
}

// The days belong to the form below the buttons, so the label drops them and keeps only the phrase.
// Several apps would not fit a button, and the list below names them anyway.
@Composable
private fun conditionSegmentLabel(condition: Condition): String = when {
    condition is Condition.MediaPlaying && condition.packages.size > 1 -> stringResource(
        if (condition.negate) R.string.builder_media_none_plays else R.string.builder_media_some_plays,
    )

    condition is Condition.HaState -> stringResource(
        if (condition.negate) R.string.builder_ha_state_out else R.string.builder_ha_state_in,
    )

    else -> conditionPhraseText(conditionPhrase(condition).copy(days = DaysPhrase.EveryDay))
}

@Composable
private fun WifiForm(
    condition: Condition.Wifi,
    state: SystemState,
    onChange: (Condition.Wifi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = state.wifi?.ssid
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.builder_wifi_which))
        RadioRow(
            title = stringResource(R.string.builder_wifi_any),
            detail = stringResource(R.string.builder_wifi_any_detail),
            selected = condition.mode == WifiMode.ANY,
            onSelect = { onChange(condition.copy(mode = WifiMode.ANY)) },
        )
        RadioRow(
            title = stringResource(R.string.builder_wifi_ssid),
            detail = stringResource(R.string.builder_wifi_ssid_detail),
            selected = condition.mode == WifiMode.SSID,
            onSelect = { onChange(condition.copy(mode = WifiMode.SSID)) },
        )
        if (condition.mode == WifiMode.SSID) {
            if (current != null) {
                TonalRow(onClick = { onChange(condition.copy(ssid = current)) }) {
                    RowText(
                        title = stringResource(R.string.builder_wifi_use_current, current),
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Note(text = stringResource(R.string.builder_wifi_no_current))
            }
            OutlinedTextField(
                value = condition.ssid.orEmpty(),
                onValueChange = { onChange(condition.copy(ssid = it)) },
                modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
                label = { Text(text = stringResource(R.string.builder_wifi_ssid_field)) },
                singleLine = true,
            )
        }
    }
}

@Composable
internal fun BluetoothForm(
    mac: String?,
    permissionTick: Int,
    modifier: Modifier = Modifier,
    onChange: (String?) -> Unit,
) {
    val context = LocalContext.current
    var connected by remember { mutableStateOf<List<ConnectedBtDevice>>(emptyList()) }
    var paired by remember { mutableStateOf<List<ConnectedBtDevice>>(emptyList()) }

    LaunchedEffect(context, permissionTick) {
        val now = connectedBluetoothDevices(context)
        connected = now
        paired = pairedNotConnected(now, pairedBluetoothDevices(context))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.builder_bt_which))
        RadioRow(
            title = stringResource(R.string.builder_bt_any),
            selected = mac == null,
            onSelect = { onChange(null) },
        )
        if (connected.isNotEmpty()) SectionLabel(title = stringResource(R.string.builder_bt_connected_now))
        connected.forEach { device -> BluetoothDeviceRow(device, mac, onChange) }
        if (paired.isNotEmpty()) SectionLabel(title = stringResource(R.string.builder_bt_paired))
        paired.forEach { device -> BluetoothDeviceRow(device, mac, onChange) }

        if (mac != null && (connected + paired).none { it.address.equals(mac, ignoreCase = true) }) {
            RadioRow(
                title = stringResource(R.string.builder_bt_unnamed, mac),
                detail = stringResource(R.string.builder_bt_not_paired),
                selected = true,
                onSelect = { },
            )
        }
        if (connected.isEmpty() && paired.isEmpty() && bluetoothConnectGranted(context)) {
            Note(text = stringResource(R.string.builder_bt_none_paired))
        }
    }
}

@Composable
private fun BluetoothDeviceRow(device: ConnectedBtDevice, mac: String?, onChange: (String?) -> Unit) {
    RadioRow(
        title = device.name ?: stringResource(R.string.builder_bt_unnamed, device.address),
        detail = device.address,
        selected = mac.equals(device.address, ignoreCase = true),
        onSelect = { onChange(device.address) },
    )
}

@Composable
private fun RingerForm(
    condition: Condition.RingerMode,
    onChange: (Condition.RingerMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.builder_ringer_which))
        RingerModeState.entries.forEach { mode ->
            RadioRow(

                title = conditionPhraseText(conditionPhrase(Condition.RingerMode(mode))),
                selected = condition.mode == mode,
                onSelect = { onChange(condition.copy(mode = mode)) },
            )
        }
    }
}

@Composable
internal fun RadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    TonalRow(onClick = onSelect, modifier = modifier, selected = selected) {
        RadioButton(selected = selected, onClick = onSelect)
        RowText(title = title, modifier = Modifier.weight(1f), detail = detail)
    }
}
