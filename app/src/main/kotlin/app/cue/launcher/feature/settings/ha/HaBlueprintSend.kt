package app.cue.launcher.feature.settings.ha

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.ha.HaBlueprintUpload
import app.cue.launcher.designsystem.FlashState
import app.cue.launcher.designsystem.LeadingIconBox
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.system.ha.HaBlueprintUploader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
internal class HaBlueprintSend(
    private val uploader: HaBlueprintUploader,
    private val scope: CoroutineScope,
    private val flash: FlashState,
) {

    var uploading by mutableStateOf(false)
        private set

    fun start() {
        if (uploading) return
        uploading = true
        scope.launch {
            val outcome = uploader.upload()
            uploading = false
            flash.show(haBlueprintMessage(outcome), error = outcome != HaBlueprintUpload.Saved)
        }
    }
}

@Composable
internal fun rememberHaBlueprintSend(uploader: HaBlueprintUploader, flash: FlashState): HaBlueprintSend {
    val scope = rememberCoroutineScope()
    return remember(uploader, scope, flash) { HaBlueprintSend(uploader, scope, flash) }
}

@Composable
internal fun HaBlueprintRow(send: HaBlueprintSend, modifier: Modifier = Modifier) {
    TonalRow(onClick = send::start, modifier = modifier, enabled = !send.uploading) {
        LeadingIconBox(glyph = GLYPH_BLUEPRINT)
        RowText(
            title = stringResource(R.string.ha_blueprint),
            modifier = Modifier.weight(1f),
            detail = stringResource(
                if (send.uploading) R.string.ha_blueprint_uploading else R.string.ha_blueprint_detail,
            ),
        )
    }
}

@StringRes
private fun haBlueprintMessage(outcome: HaBlueprintUpload): Int = when (outcome) {
    HaBlueprintUpload.Saved -> R.string.ha_blueprint_done
    HaBlueprintUpload.NotAdmin -> R.string.ha_blueprint_not_admin
    HaBlueprintUpload.Refused -> R.string.ha_blueprint_refused
    HaBlueprintUpload.Unreachable -> R.string.ha_blueprint_unreachable
    HaBlueprintUpload.Blocked -> R.string.ha_blueprint_blocked
    HaBlueprintUpload.LocalNetworkDenied -> R.string.ha_blueprint_local_network
    HaBlueprintUpload.NotConfigured -> R.string.ha_blueprint_not_configured
    HaBlueprintUpload.Failed -> R.string.ha_blueprint_failed
}

private const val GLYPH_BLUEPRINT = "cloud-upload"
