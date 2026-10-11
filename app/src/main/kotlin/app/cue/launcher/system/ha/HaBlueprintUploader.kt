package app.cue.launcher.system.ha

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.provider.Settings
import android.util.Log
import app.cue.launcher.R
import app.cue.launcher.data.ha.HaBlueprintUpload
import app.cue.launcher.data.ha.HaConfig
import app.cue.launcher.data.ha.HaConfigRepository
import app.cue.launcher.data.ha.HaEndpoint
import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.ha.haBlueprintCode
import app.cue.launcher.data.ha.haBlueprintFor
import app.cue.launcher.data.ha.haBlueprintSave
import app.cue.launcher.data.ha.haBlueprintStep
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.IOException

@SingleIn(AppScope::class)
@Inject
class HaBlueprintUploader(
    private val application: Application,
    private val configs: HaConfigRepository,
    private val selector: HaEndpointSelector,
    private val access: HaAccess,
    private val client: HaClient,
    private val gateway: HaGateway,
) {

    suspend fun upload(): HaBlueprintUpload {
        val config = configs.config().takeIf(HaConfig::isConfigured) ?: return HaBlueprintUpload.NotConfigured
        val endpoints = selector.endpoints(config)
        return when {
            // A configured instance with no address left to try has only a cleartext home address, and
            // the phone is not at home.
            endpoints.isEmpty() -> HaBlueprintUpload.Blocked
            else -> save()?.let { send(config, endpoints, it) } ?: HaBlueprintUpload.Failed
        }
    }

    // Saving with allow_override is idempotent, so trying the next address after one that went quiet
    // mid-way cannot leave anything half done.
    private suspend fun send(config: HaConfig, endpoints: List<HaEndpoint>, save: String): HaBlueprintUpload {
        var outcome: HaBlueprintUpload = HaBlueprintUpload.Unreachable
        var denied = false
        val remaining = endpoints.iterator()
        while (outcome == HaBlueprintUpload.Unreachable && remaining.hasNext()) {
            val endpoint = remaining.next()
            outcome = when (val bearer = access.bearer(config, endpoint)) {
                is HaBearer.Token -> send(endpoint, bearer.value, save)
                HaBearer.Unauthorized -> HaBlueprintUpload.Refused
                HaBearer.Unreachable -> HaBlueprintUpload.Unreachable
                HaBearer.None -> HaBlueprintUpload.NotConfigured
            }
            denied = denied || outcome == HaBlueprintUpload.Unreachable && localNetworkDenied(endpoint, config)
        }
        return if (outcome == HaBlueprintUpload.Unreachable && denied) HaBlueprintUpload.LocalNetworkDenied else outcome
    }

    private fun localNetworkDenied(endpoint: HaEndpoint, config: HaConfig): Boolean =
        gateway.diagnose(endpoint, HaProbe.Network(null), config) == HaProbe.LocalNetworkDenied

    private suspend fun send(endpoint: HaEndpoint, token: String, save: String): HaBlueprintUpload =
        withTimeoutOrNull(UPLOAD_TIMEOUT_MILLIS) {
            client.socket(endpoint, purpose = "Blueprint upload") { session -> exchange(session, token, save) }
        } ?: HaBlueprintUpload.Unreachable

    private suspend fun exchange(session: WebSocketSession, token: String, save: String): HaBlueprintUpload {
        for (frame in session.incoming) {
            val message = (frame as? Frame.Text)?.readText()?.let(::objectOf) ?: continue
            val step = haBlueprintStep(message, token, save)
            step.send?.let { session.send(Frame.Text(it)) }
            step.outcome?.let { return it }
        }
        return HaBlueprintUpload.Unreachable
    }

    private suspend fun save(): String? {
        val secret = configs.ensureEventSecret()
        return withContext(Dispatchers.IO) {
            asset()
                ?.let { haBlueprintFor(it, secret, phoneName()) }
                ?.let { haBlueprintSave(it, haBlueprintCode(deviceId())) }
        }
    }

    private fun asset(): String? = try {
        val name = application.getString(R.string.ha_blueprint_asset)
        application.assets.open(name).bufferedReader().use { it.readText() }
    } catch (cause: IOException) {
        Log.w(TAG, "Could not read the blueprint from assets (${cause::class.java.simpleName})")
        null
    }

    // ANDROID_ID is scoped to the app's signing key: a reinstall keeps it, a build signed with another
    // key does not.
    @SuppressLint("HardwareIds")
    private fun deviceId(): String =
        Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()

    private fun phoneName(): String =
        runCatching { Settings.Global.getString(application.contentResolver, Settings.Global.DEVICE_NAME) }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: Build.MODEL.orEmpty()

    private fun objectOf(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    private companion object {

        val json = Json { ignoreUnknownKeys = true }

        const val UPLOAD_TIMEOUT_MILLIS = 15_000L

        const val TAG = "HaBlueprint"
    }
}
