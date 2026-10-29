package app.cue.launcher.feature.settings.ha

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.cue.launcher.R
import app.cue.launcher.data.ha.HaEndpointKind
import app.cue.launcher.data.ha.HaUrlError
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.LeadingIconBox
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.supportingInk
import app.cue.launcher.designsystem.supportingStyle
import app.cue.launcher.designsystem.Message
import app.cue.launcher.feature.scenarios.JitPermission
import app.cue.launcher.feature.scenarios.JitRequirement
import app.cue.launcher.feature.scenarios.builder.ConnectedChoice
import app.cue.launcher.feature.scenarios.builder.ConnectedChoiceRow
import app.cue.launcher.feature.scenarios.builder.rememberRequirementResolver
import app.cue.launcher.feature.settings.SettingsGrantRequest
import app.cue.launcher.system.localNetworkGranted

@Composable
internal fun HaConnectionSection(
    screen: HaScreenState,
    onDiscover: () -> Unit,
    onSignIn: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val permissions = rememberRequirementResolver { }
    val granted = remember(permissions.tick) { localNetworkGranted(context) }
    SideEffect { screen.localNetworkGranted = granted }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.textSpacing),
    ) {
        SectionLabel(title = stringResource(R.string.ha_section_connection))
        HaDiscoverRow(screen = screen, onDiscover = onDiscover)
        HaUrlField(
            value = screen.urlInternal,
            label = R.string.ha_url_internal,
            hint = R.string.ha_url_internal_hint,
            error = screen.internalError,
            probe = haProbeText(screen.probes[HaEndpointKind.HOME], screen.testing),
            onChange = { screen.urlInternal = it },
        )
        HaUrlField(
            value = screen.urlExternal,
            label = R.string.ha_url_external,
            hint = R.string.ha_url_external_hint,
            error = screen.externalError,
            probe = haProbeText(screen.probes[HaEndpointKind.AWAY], screen.testing),
            onChange = { screen.urlExternal = it },
        )
        Note(text = stringResource(R.string.ha_actions_note))

        SectionLabel(title = stringResource(R.string.ha_section_credential))
        HaCredentialChoice(screen = screen)
        when (screen.credentialMode) {
            HaCredentialMode.SIGN_IN -> HaSignInRow(screen = screen, onSignIn = onSignIn)
            HaCredentialMode.TOKEN -> HaTokenField(screen = screen)
        }
        if (screen.localNetworkNeeded) {
            SettingsGrantRequest(
                action = stringResource(R.string.ha_local_network_grant),
                onGrant = { permissions.resolve(JitRequirement.Grant(JitPermission.LOCAL_NETWORK)) },
                note = stringResource(R.string.ha_local_network_note),
            )
        }

        SectionLabel(title = stringResource(R.string.ha_section_test))
        HaTestRow(screen = screen, onTest = onTest)
        Note(text = haNowUsingText(screen.nowUsing))
    }
}

// The picker is the shortest path through the whole screen: it fills both addresses in, and the
// approval it leaves behind is what lets the launcher reach the instance on Android 17 without asking
// for the local network permission at all.
@Composable
private fun HaDiscoverRow(
    screen: HaScreenState,
    onDiscover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = when {
        screen.discovering -> stringResource(R.string.ha_discover_running)
        screen.paired -> stringResource(R.string.ha_discover_paired, screen.instanceName)
        else -> stringResource(R.string.ha_discover_detail)
    }
    TonalRow(onClick = onDiscover.takeUnless { screen.discovering }, modifier = modifier) {
        LeadingIconBox(glyph = GLYPH_DISCOVER)
        RowText(
            title = stringResource(R.string.ha_discover),
            modifier = Modifier.weight(1f),
            detail = detail,
        )
    }
}

// The choice itself, not two paths offered at once: only the selected way in is on screen, so there
// is nothing to wonder about which of them the launcher will use.
@Composable
private fun HaCredentialChoice(screen: HaScreenState, modifier: Modifier = Modifier) {
    ConnectedChoiceRow(
        options = listOf(
            ConnectedChoice(
                label = stringResource(R.string.ha_credential_session),
                selected = screen.credentialMode == HaCredentialMode.SIGN_IN,
                onSelect = { screen.chooseCredential(HaCredentialMode.SIGN_IN) },
            ),
            ConnectedChoice(
                label = stringResource(R.string.ha_credential_token),
                selected = screen.credentialMode == HaCredentialMode.TOKEN,
                onSelect = { screen.chooseCredential(HaCredentialMode.TOKEN) },
            ),
        ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HaSignInRow(
    screen: HaScreenState,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.surfacePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onSignIn,
                shapes = ButtonDefaults.shapes(),
                enabled = screen.canSignIn && !screen.signingIn,
            ) {
                GlyphIcon(
                    name = GLYPH_SIGN_IN,
                    size = ButtonDefaults.IconSize,
                    tint = LocalContentColor.current,
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.ha_signin))
            }
            if (screen.signingIn) {
                Text(
                    text = stringResource(R.string.ha_signin_running),
                    style = supportingStyle,
                    color = supportingInk(),
                )
            }
        }
        Note(
            text = stringResource(
                if (screen.sessionStored) R.string.ha_signin_done else R.string.ha_signin_note,
            ),
        )
    }
}

// The test is an action, not a place to navigate to, so it reads as a button with its answer next
// to it. The per-endpoint results stay under their own field.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HaTestRow(
    screen: HaScreenState,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = when {
        screen.testing -> stringResource(R.string.ha_test_running)
        screen.canTest -> null

        // The hint names the way in the user picked, not both of them.
        screen.credentialMode == HaCredentialMode.SIGN_IN ->
            stringResource(R.string.ha_test_needs_session)

        else -> stringResource(R.string.ha_test_needs_token)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.surfacePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = onTest,
            shapes = ButtonDefaults.shapes(),
            enabled = screen.canTest && !screen.testing,
        ) {
            GlyphIcon(
                name = GLYPH_TEST,
                size = ButtonDefaults.IconSize,
                tint = LocalContentColor.current,
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(text = stringResource(R.string.ha_test))
        }
        status?.let {
            Text(
                text = it,
                style = supportingStyle,
                color = supportingInk(),
            )
        }
    }
}

@Composable
private fun HaUrlField(
    value: String,
    @StringRes label: Int,
    @StringRes hint: Int,
    error: HaUrlError?,
    probe: String?,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
            label = { Text(text = stringResource(label)) },
            placeholder = { Text(text = stringResource(hint)) },
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
        )
        error?.let { Message(text = haUrlErrorText(it)) }
        probe?.let { Note(text = it) }
    }
}

@Composable
private fun HaTokenField(screen: HaScreenState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        OutlinedSecureTextField(
            state = screen.token,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
            label = { Text(text = stringResource(R.string.ha_token)) },
            placeholder = {
                Text(
                    text = stringResource(
                        if (screen.tokenStored) R.string.ha_token_stored else R.string.ha_token_hint,
                    ),
                )
            },
        )
        Note(
            text = stringResource(
                if (screen.tokenStored) R.string.ha_token_saved else R.string.ha_token_note,
            ),
        )
    }
}

private const val GLYPH_TEST = "lan-connect"

private const val GLYPH_DISCOVER = "magnify-scan"

private const val GLYPH_SIGN_IN = "login-variant"
