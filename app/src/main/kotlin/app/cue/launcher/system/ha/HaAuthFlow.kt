package app.cue.launcher.system.ha

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import app.cue.launcher.MainActivity
import app.cue.launcher.data.ha.HA_CALLBACK_CODE
import app.cue.launcher.data.ha.HA_CALLBACK_ERROR
import app.cue.launcher.data.ha.HaConfig
import app.cue.launcher.data.ha.HaEndpoint
import app.cue.launcher.data.ha.HaSignIn
import app.cue.launcher.data.ha.HaTokenResult
import app.cue.launcher.data.ha.haAuthState
import app.cue.launcher.data.ha.haAuthorizeUrl
import app.cue.launcher.data.ha.haClientId
import app.cue.launcher.data.ha.haRedirectUri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Signing in beats pasting a long-lived token: Home Assistant hands out a refresh token, the access
// tokens made from it live 30 minutes, and the user can end the session from their own profile.
//
// The callback is a loopback address because Home Assistant accepts a redirect_uri that shares scheme
// and host with the client_id without checking anything else.
@SingleIn(AppScope::class)
@Inject
class HaAuthFlow(
    private val application: Application,
    private val tokens: HaTokenClient,
    private val selector: HaEndpointSelector,
    private val access: HaAccess,
    private val callback: HaCallbackServer,
) {

    // The tab is opened from the activity on purpose: it then lives in the launcher's own task, which
    // is what lets the callback close it again and hand the screen back exactly as it was left.
    suspend fun signIn(config: HaConfig, from: Context): HaSignIn {
        val endpoint = selector.endpoints(config).firstOrNull() ?: return HaSignIn.Failed(null)
        return withContext(Dispatchers.IO) { run(endpoint, from) }
    }

    // Ending the session in Home Assistant too, so the row in the user's profile goes away with it
    // instead of staying there as a credential nobody uses any more. False when there was a session
    // and no address would end it — it is then still valid on the instance.
    suspend fun signOut(config: HaConfig): Boolean = access.revoke(config, selector.endpoints(config))

    private suspend fun run(endpoint: HaEndpoint, from: Context): HaSignIn {
        val server = callback.open() ?: return HaSignIn.Failed(null)
        val clientId = haClientId(server.localPort)
        val redirectUri = haRedirectUri(server.localPort)
        val state = haAuthState()
        return try {
            val opened = browse(from, haAuthorizeUrl(endpoint.url, clientId, redirectUri, state))
            val answer = if (opened) callback.await(server, state) else null
            if (answer != null) leaveBrowser()
            when {
                !opened -> HaSignIn.Failed(null)
                answer == null -> HaSignIn.Cancelled
                answer[HA_CALLBACK_ERROR] != null -> HaSignIn.Failed(answer[HA_CALLBACK_ERROR])
                else -> exchange(endpoint, answer[HA_CALLBACK_CODE], clientId)
            }
        } finally {
            runCatching { server.close() }
        }
    }

    private suspend fun exchange(endpoint: HaEndpoint, code: String?, clientId: String): HaSignIn {
        if (code.isNullOrBlank()) return HaSignIn.Cancelled
        return when (val result = tokens.exchangeCode(endpoint, code, clientId)) {
            is HaTokenResult.Granted -> when {
                result.tokens.refreshToken.isBlank() -> HaSignIn.Failed(null)
                else -> HaSignIn.Done(result.tokens, clientId)
            }

            HaTokenResult.Refused, HaTokenResult.Unreachable -> HaSignIn.Failed(null)
        }
    }

    private fun browse(from: Context, url: String): Boolean = try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(from, Uri.parse(url))
        true
    } catch (cause: ActivityNotFoundException) {
        Log.w(TAG, "No browser to sign in with (${cause::class.java.simpleName})")
        false
    }

    // Clearing everything above the launcher's own activity is what ends the tab. The action tells the
    // activity this is not the home button, which would close the settings the sign-in started in.
    private fun leaveBrowser() {
        val back = Intent(application, MainActivity::class.java)
            .setAction(HA_SIGN_IN_RETURN)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        runCatching { application.startActivity(back) }
            .onFailure { cause ->
                Log.w(TAG, "The launcher could not come back over the browser (${cause::class.java.simpleName})")
            }
    }

    private companion object {
        const val TAG = "HaAuth"
    }
}

const val HA_SIGN_IN_RETURN = "app.cue.launcher.action.HA_SIGN_IN_RETURN"
