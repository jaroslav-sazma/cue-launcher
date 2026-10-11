package app.cue.launcher.feature.settings.ha

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.scenarios.JitPermission
import app.cue.launcher.feature.scenarios.JitRequirement
import app.cue.launcher.feature.scenarios.builder.rememberRequirementResolver
import app.cue.launcher.system.fineLocationGranted
import app.cue.launcher.system.locationServicesEnabled
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.SectionLabel

@Composable
internal fun HaHomeWifiSection(screen: HaScreenState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val permissions = rememberRequirementResolver { }
    val networks = screen.homeNetworks
    val granted = remember(permissions.tick) { fineLocationGranted(context) }
    val servicesOn = remember(permissions.tick) { locationServicesEnabled(context) }

    LaunchedEffect(permissions.tick) { screen.wifiTick++ }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.textSpacing),
    ) {
        SectionLabel(title = stringResource(R.string.ha_section_home))
        OutlinedTextField(
            value = screen.homeNetworksText,
            onValueChange = { screen.homeNetworksText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
            label = { Text(text = stringResource(R.string.ha_home_wifi)) },
            placeholder = { Text(text = stringResource(R.string.ha_home_wifi_hint)) },
            singleLine = true,
        )
        HaNetworkChips(
            networks = networks,
            current = screen.network.ssid,
            onRemove = screen::removeNetwork,
            onAdd = screen::addNetwork,
        )
        if (screen.homeNetworkMissing) {
            Note(text = stringResource(R.string.ha_home_wifi_required))
        }
        Note(
            text = stringResource(
                if (screen.paired) R.string.ha_home_wifi_paired else R.string.ha_home_wifi_note,
            ),
        )
        if (networks.isNotEmpty() && !granted) {
            HaLocationRow(
                label = R.string.ha_location_note,
                action = R.string.ha_location_grant,
                onResolve = { permissions.resolve(JitRequirement.Grant(JitPermission.LOCATION)) },
            )
        }
        if (networks.isNotEmpty() && granted && !servicesOn) {
            HaLocationRow(
                label = R.string.ha_location_services_note,
                action = R.string.ha_location_services_open,
                onResolve = { permissions.resolve(JitRequirement.LocationServices) },
            )
        }
    }
}

@Composable
private fun HaNetworkChips(
    networks: List<String>,
    current: String?,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val offered = current?.takeIf { ssid -> networks.none { it.equals(ssid, ignoreCase = true) } }
    if (networks.isEmpty() && offered == null) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Settings.surfacePadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Scenarios.chipGap),
    ) {
        networks.forEach { ssid ->
            FilterChip(
                selected = true,
                onClick = { onRemove(ssid) },
                label = { Text(text = ssid) },
            )
        }
        offered?.let { ssid ->
            SuggestionChip(
                onClick = { onAdd(ssid) },
                label = { Text(text = stringResource(R.string.ha_home_wifi_add, ssid)) },
            )
        }
    }
}

@Composable
private fun HaLocationRow(
    label: Int,
    action: Int,
    onResolve: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        Note(text = stringResource(label))
        OutlinedButton(
            onClick = onResolve,
            modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding),
        ) {
            Text(text = stringResource(action))
        }
    }
}
