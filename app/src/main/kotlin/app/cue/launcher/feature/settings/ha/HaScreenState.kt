package app.cue.launcher.feature.settings.ha

import androidx.annotation.StringRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cue.launcher.R
import app.cue.launcher.data.ha.HaConfig
import app.cue.launcher.data.ha.HaEndpoint
import app.cue.launcher.data.ha.HaEndpointKind
import app.cue.launcher.data.ha.HaInstance
import app.cue.launcher.data.ha.HaNetwork
import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.ha.haHomeNetworkMissing
import app.cue.launcher.data.ha.haHomeNetworksOf
import app.cue.launcher.data.ha.haUrlError
import app.cue.launcher.data.ha.haUrlIsLocal

// Two ways to hold the connection, never both at once: showing a sign-in button and a token field
// under each other asks the user to guess which one counts.
internal enum class HaCredentialMode { SIGN_IN, TOKEN }

@Stable
internal class HaScreenState {

    var loaded by mutableStateOf(false)

    var urlInternal by mutableStateOf("")

    var urlExternal by mutableStateOf("")

    val token = TextFieldState()

    var credentialMode by mutableStateOf(HaCredentialMode.SIGN_IN)

    private val tokenText: String get() = token.text.toString()

    // What the screen would actually save: text in the token field counts only while the token is
    // the chosen way in.
    val typedToken: String get() = if (credentialMode == HaCredentialMode.TOKEN) tokenText else ""

    fun chooseCredential(mode: HaCredentialMode) {
        if (mode == credentialMode) return
        credentialMode = mode
        token.clearText()
    }

    var tokenStored by mutableStateOf(false)

    var sessionStored by mutableStateOf(false)

    var homeNetworksText by mutableStateOf("")

    var testing by mutableStateOf(false)

    var discovering by mutableStateOf(false)

    var signingIn by mutableStateOf(false)

    var saving by mutableStateOf(false)

    var instanceName by mutableStateOf("")

    var instanceUuid by mutableStateOf("")

    var localNetworkGranted by mutableStateOf(false)

    var probes by mutableStateOf<Map<HaEndpointKind, HaProbe>>(emptyMap())

    var network by mutableStateOf(HaNetwork())

    var wifiTick by mutableIntStateOf(0)

    var nowUsing by mutableStateOf<HaEndpoint?>(null)

    var logVisible by mutableStateOf(false)

    private var savedUrlInternal by mutableStateOf("")

    private var savedUrlExternal by mutableStateOf("")

    private var savedNetworksText by mutableStateOf("")

    // The connection fields only reach the repository through Save, so leaving the screen with any
    // of them edited silently drops the edit. The save bar and the exit warning both hang off this.
    val dirty: Boolean
        get() = urlInternal != savedUrlInternal ||
            urlExternal != savedUrlExternal ||
            homeNetworksText != savedNetworksText ||
            typedToken.isNotBlank()

    val homeNetworks: List<String> get() = haHomeNetworksOf(homeNetworksText)

    val internalError get() = haUrlError(urlInternal, external = false)

    val externalError get() = haUrlError(urlExternal, external = true)

    val hasUrl: Boolean get() = urlInternal.isNotBlank() || urlExternal.isNotBlank()

    val paired: Boolean get() = instanceUuid.isNotBlank()

    val homeNetworkMissing: Boolean get() = haHomeNetworkMissing(urlInternal, homeNetworks, paired)

    val hasCredential: Boolean
        get() = when (credentialMode) {
            HaCredentialMode.SIGN_IN -> sessionStored
            HaCredentialMode.TOKEN -> typedToken.isNotBlank() || tokenStored
        }

    // Only a local address needs it, and only when the instance was not approved in the picker: that
    // approval is access to one service, which is less than the permission grants.
    val localNetworkNeeded: Boolean
        get() = !localNetworkGranted && !paired && (haUrlIsLocal(urlInternal) || haUrlIsLocal(urlExternal))

    val canSave: Boolean
        get() = internalError == null && externalError == null && hasUrl && !homeNetworkMissing

    // A greyed-out Save button whose reason sits a screenful below it reads as a dead button, so
    // the bar repeats every reason that blocks saving — the field keeps its own copy for context.
    @get:StringRes
    val saveBlocker: Int?
        get() {
            val urlError = internalError ?: externalError
            return when {
                !hasUrl -> R.string.ha_url_error_none
                urlError != null -> haUrlErrorRes(urlError)
                homeNetworkMissing -> R.string.ha_save_needs_wifi
                else -> null
            }
        }

    val canTest: Boolean get() = canSave && hasCredential

    // Signing in stores the address along with the session, so it has to pass everything saving does —
    // a cleartext home address with nothing to recognise home by must not get in through this door.
    val canSignIn: Boolean get() = canSave

    fun fill(config: HaConfig) {
        urlInternal = config.urlInternal
        urlExternal = config.urlExternal
        homeNetworksText = config.homeNetworks.joinToString(", ")
        tokenStored = config.token.isNotBlank()
        sessionStored = config.refreshToken.isNotBlank()
        credentialMode = if (tokenStored && !sessionStored) HaCredentialMode.TOKEN else HaCredentialMode.SIGN_IN
        instanceUuid = config.instanceUuid
        instanceName = config.instanceName
        loaded = true
        markSaved()
    }

    fun markSaved() {
        savedUrlInternal = urlInternal
        savedUrlExternal = urlExternal
        savedNetworksText = homeNetworksText
    }

    fun draft(stored: HaConfig): HaConfig = HaConfig(
        urlInternal = urlInternal,
        urlExternal = urlExternal,
        token = typedToken.ifBlank { stored.token },
        homeNetworks = homeNetworks,
        refreshToken = if (typedToken.isBlank()) stored.refreshToken else "",
        oauthClientId = if (typedToken.isBlank()) stored.oauthClientId else "",
        instanceUuid = instanceUuid,
        instanceName = instanceName,
    )

    // What the picker found replaces both addresses only where it actually knows better: an instance
    // that has no external_url must not wipe a working away address.
    fun apply(instance: HaInstance) {
        instanceUuid = instance.uuid
        instanceName = instance.label
        instance.homeUrl.takeIf(String::isNotEmpty)?.let { urlInternal = it }
        instance.awayUrl.takeIf(String::isNotEmpty)?.let { urlExternal = it }
    }

    fun addNetwork(ssid: String) {
        homeNetworksText = (homeNetworks + ssid).joinToString(", ")
    }

    fun removeNetwork(ssid: String) {
        homeNetworksText = homeNetworks.filterNot { it.equals(ssid, ignoreCase = true) }
            .joinToString(", ")
    }
}
