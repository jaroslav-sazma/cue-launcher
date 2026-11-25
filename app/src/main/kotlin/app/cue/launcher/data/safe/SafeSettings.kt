package app.cue.launcher.data.safe

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

// A file of its own rather than a part of settings.json: nothing reads it until the vault is touched,
// and forgetting the vault leaves no key material behind in the general settings.
@Serializable
data class SafeSettings(
    val container: SafeContainer? = null,
    val key: SafeStoredKey? = null,
    val lockAfterMinutes: Int = SAFE_LOCK_AFTER_DEFAULT_MINUTES,
    val openInFiles: Boolean = true,
)

@Serializable
data class SafeContainer(
    val uri: String,
    val displayName: String,
    // The header's written_at at the last unlock, in Unix seconds.
    val writtenAt: Long? = null,
)

// The ids in hex, the CEK sealed under the Keystore key, its GCM IV and ciphertext in Base64.
@Serializable
data class SafeStoredKey(
    val vaultId: String,
    val keyId: String,
    val iv: String,
    val sealedCek: String,
)

const val SAFE_LOCK_AFTER_DEFAULT_MINUTES = 15

val SAFE_LOCK_AFTER_MINUTES = 1..60

object SafeSettingsSerializer : Serializer<SafeSettings> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    override val defaultValue: SafeSettings = SafeSettings()

    override suspend fun readFrom(input: InputStream): SafeSettings =
        try {
            json.decodeFromString<SafeSettings>(input.readBytes().decodeToString())
        } catch (cause: SerializationException) {
            throw CorruptionException("Could not read the vault settings", cause)
        }

    override suspend fun writeTo(t: SafeSettings, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}
