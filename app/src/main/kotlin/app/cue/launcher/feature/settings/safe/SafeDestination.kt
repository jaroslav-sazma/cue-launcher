package app.cue.launcher.feature.settings.safe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// fromHome: opened by a vault action on a tile, so leaving it goes straight back to the home screen.
@Serializable
data class VaultSettings(val fromHome: Boolean = false) : NavKey

// Set by a vault action that needs the user, on its way to the settings, which open on the vault
// screen and clear it. It lives with the activity: the settings replace the composition that tapped.
@Stable
class VaultRequest {
    var pending by mutableStateOf(false)
}

val LocalVaultRequest = staticCompositionLocalOf<VaultRequest> { error("No vault request provided") }

@Composable
fun rememberVaultOpener(openSettings: () -> Unit): () -> Unit {
    val request = LocalVaultRequest.current
    val open by rememberUpdatedState(openSettings)
    return remember(request) {
        {
            request.pending = true
            open()
        }
    }
}

// Opened for the vault, the settings start on its screen rather than showing the list first.
internal fun settingsStartKeys(first: NavKey, vault: Boolean): Array<NavKey> =
    if (vault) arrayOf(first, VaultSettings(fromHome = true)) else arrayOf(first)

internal fun List<NavKey>.leavesForHome(): Boolean = (lastOrNull() as? VaultSettings)?.fromHome == true
