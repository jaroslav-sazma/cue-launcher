package app.cue.launcher.data.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.security.MessageDigest

sealed interface HaBlueprintUpload {

    data object Saved : HaBlueprintUpload

    data object NotAdmin : HaBlueprintUpload

    data object Refused : HaBlueprintUpload

    data object Unreachable : HaBlueprintUpload

    // The only address is a cleartext home one and the phone is not at home, so nothing was tried.
    data object Blocked : HaBlueprintUpload

    data object LocalNetworkDenied : HaBlueprintUpload

    data object NotConfigured : HaBlueprintUpload

    data object Failed : HaBlueprintUpload
}

data class HaBlueprintStep(val send: String? = null, val outcome: HaBlueprintUpload? = null)

fun haBlueprintStep(message: JsonObject, token: String, save: String): HaBlueprintStep =
    when (textOf(message[HA_LIVE_TYPE])) {
        HA_LIVE_AUTH_REQUIRED -> HaBlueprintStep(send = haLiveAuth(token))
        HA_LIVE_AUTH_OK -> HaBlueprintStep(send = save)
        HA_LIVE_AUTH_INVALID -> HaBlueprintStep(outcome = HaBlueprintUpload.Refused)
        RESULT -> result(message)
        else -> HaBlueprintStep()
    }

// Everything but the YAML and the phone's code is fixed, so the one file this can ever write is the
// phone's own blueprints/automation/cue/launcher_event_<code>.yaml. Overriding is what lets a changed
// secret or a newer blueprint replace the copy this phone uploaded before.
fun haBlueprintSave(yaml: String, code: String): String = json.encodeToString(
    JsonObject.serializer(),
    buildJsonObject {
        put(HA_LIVE_ID, SAVE_ID)
        put(HA_LIVE_TYPE, BLUEPRINT_SAVE)
        put(DOMAIN, AUTOMATION)
        put(PATH, haBlueprintPath(code))
        put(YAML, yaml)
        put(ALLOW_OVERRIDE, true)
    },
)

// Every phone gets a file of its own: two launchers with different secrets on one instance would
// otherwise keep overwriting each other's default. The code is derived rather than random so that a
// reinstall lands on the same file again, and hashed so the device identifier itself never leaves.
fun haBlueprintCode(deviceId: String): String = MessageDigest.getInstance(SHA_256)
    .digest((CODE_SALT + deviceId).toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
    .take(CODE_LENGTH)

fun haBlueprintPath(code: String): String = "$FOLDER/launcher_event_$code.yaml"

// The secret becomes the input's default, so an automation that leaves the field alone follows the
// blueprint, and uploading it again after a change updates all of them at once. Only a secret that
// passed validation goes in: it then has no whitespace, so it cannot break out of its line. The phone's
// name goes into the title so that two phones' blueprints can be told apart in Home Assistant.
fun haBlueprintFor(blueprint: String, secret: String, phone: String): String? {
    val lines = blueprint.lines()
    val secretAt = lines.indexOf(SECRET_INPUT)
    val nameAt = lines.indexOfFirst { NAME.matches(it) }
    if (secretAt < 0 || nameAt < 0 || haEventSecretError(secret) != null) return null
    val default = "$SECRET_DEFAULT'${secret.trim().replace("'", "''")}'"
    val label = phoneLabel(phone)
    val named = lines.toMutableList().apply {
        if (label.isNotEmpty()) this[nameAt] = this[nameAt].removeSuffix("\"") + " ($label)\""
        add(secretAt + 1, default)
    }
    return named.joinToString("\n")
}

private fun phoneLabel(phone: String): String = phone
    .filterNot(Char::isISOControl)
    .trim()
    .take(PHONE_LABEL_LENGTH)
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

private fun result(message: JsonObject): HaBlueprintStep {
    if ((message[HA_LIVE_ID] as? JsonPrimitive)?.intOrNull != SAVE_ID) return HaBlueprintStep()
    val success = (message[SUCCESS] as? JsonPrimitive)?.booleanOrNull == true
    val code = textOf((message[ERROR] as? JsonObject)?.get(CODE))
    return HaBlueprintStep(
        outcome = when {
            success -> HaBlueprintUpload.Saved
            code == UNAUTHORIZED -> HaBlueprintUpload.NotAdmin
            else -> HaBlueprintUpload.Failed
        },
    )
}

private fun textOf(element: JsonElement?): String? = (element as? JsonPrimitive)?.contentOrNull

private val json = Json { ignoreUnknownKeys = true }

private const val SECRET_INPUT = "    pairing_secret:"

private val NAME = Regex("^  name: \".*\"$")

private const val FOLDER = "cue"

private const val SHA_256 = "SHA-256"

private const val CODE_SALT = "cue-blueprint:"

private const val CODE_LENGTH = 8

private const val PHONE_LABEL_LENGTH = 40

private const val SECRET_DEFAULT = "      default: "

private const val SAVE_ID = 1

private const val DOMAIN = "domain"

private const val PATH = "path"

private const val YAML = "yaml"

private const val ALLOW_OVERRIDE = "allow_override"

private const val SUCCESS = "success"

private const val ERROR = "error"

private const val CODE = "code"

private const val AUTOMATION = "automation"

private const val BLUEPRINT_SAVE = "blueprint/save"

private const val RESULT = "result"

private const val UNAUTHORIZED = "unauthorized"
