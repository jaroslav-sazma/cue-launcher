package app.cue.launcher.feature.settings.bridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.cue.launcher.R
import app.cue.launcher.data.bridge.BRIDGE_DEV_HOST
import app.cue.launcher.data.bridge.BridgeDevEndpoint
import app.cue.launcher.debug.bridge.BridgeFixtureSection
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.system.bridge.BridgeLinkState

// Debug builds only: the emulator has no Bluetooth, so DevPresence reaches the Mac at this endpoint.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BridgeDebugSection(
    endpoint: BridgeDevEndpoint?,
    link: BridgeLinkState,
    onSave: (host: String, port: Int) -> Unit,
    onSendTest: () -> Unit,
) {
    var host by rememberSaveable(endpoint) { mutableStateOf(endpoint?.host ?: BRIDGE_DEV_HOST) }
    var port by rememberSaveable(endpoint) { mutableStateOf(endpoint?.port?.toString().orEmpty()) }
    val portNumber = devPortOf(port)

    SectionLabel(title = stringResource(R.string.bridge_debug_section))
    Note(text = stringResource(R.string.bridge_debug_note, BRIDGE_DEV_HOST))
    DevEndpointFields(host = host, port = port, onHost = { host = it }, onPort = { port = it })
    Row(
        modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.fieldGap),
    ) {
        FilledTonalButton(
            onClick = { portNumber?.let { onSave(host, it) } },
            shapes = ButtonDefaults.shapes(),
            enabled = portNumber != null,
        ) {
            Text(text = stringResource(R.string.bridge_debug_save))
        }
        OutlinedButton(
            onClick = onSendTest,
            shapes = ButtonDefaults.shapes(),
            enabled = link == BridgeLinkState.CONNECTED,
        ) {
            Text(text = stringResource(R.string.bridge_debug_signal))
        }
    }
    BridgeFixtureSection()
}

@Composable
internal fun DevEndpointFields(host: String, port: String, onHost: (String) -> Unit, onPort: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.fieldGap),
    ) {
        OutlinedTextField(
            value = host,
            onValueChange = onHost,
            modifier = Modifier.weight(2f),
            label = { Text(text = stringResource(R.string.bridge_debug_host)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
        )
        OutlinedTextField(
            value = port,
            onValueChange = { onPort(it.filter(Char::isDigit)) },
            modifier = Modifier.weight(1f),
            label = { Text(text = stringResource(R.string.bridge_debug_port)) },
            isError = port.isNotEmpty() && devPortOf(port) == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
    }
}

internal fun devPortOf(text: String): Int? = text.toIntOrNull()?.takeIf { it in 1..MAX_PORT }

private const val MAX_PORT = 65_535
