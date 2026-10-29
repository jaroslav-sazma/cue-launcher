package app.cue.launcher.debug.bridge

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.designsystem.Message
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Tokens

// Debug builds only; release builds get an empty counterpart. Posting needs POST_NOTIFICATIONS,
// asked for at the first press.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BridgeFixtureSection() {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<BridgeFixture?>(null) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        pending?.takeIf { granted }?.let { FixtureNotifications.post(context, it) }
        pending = null
    }

    SectionLabel(title = stringResource(R.string.bridge_fixture_section))
    Note(text = stringResource(R.string.bridge_fixture_note))
    if (denied) Message(text = stringResource(R.string.bridge_fixture_denied))
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Settings.fieldGap),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.fieldGap),
    ) {
        BridgeFixture.entries.forEach { fixture ->
            OutlinedButton(
                onClick = {
                    if (fixture == BridgeFixture.CANCEL || FixtureNotifications.allowed(context)) {
                        denied = false
                        FixtureNotifications.post(context, fixture)
                    } else {
                        pending = fixture
                        request.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(fixture.label))
            }
        }
    }
}
