package app.cue.launcher.data.ha

import androidx.datastore.core.DataStore
import app.cue.launcher.data.settings.LauncherSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
@Inject
class HaConfigRepository(private val dataStore: DataStore<LauncherSettings>) {

    val config: Flow<HaConfig> = dataStore.data.map { it.ha.toConfig(HaTokenCipher::open) }

    // Which instance is set up, if any, without opening the credential: the home screen asks on every
    // restart of its queues, and the Keystore need not be woken for that.
    val iconInstance: Flow<String?> = dataStore.data
        .map { settings -> settings.ha.toConfig { SEALED }.takeIf(HaConfig::isConfigured)?.let(::haIconInstance) }
        .distinctUntilChanged()

    val eventSecret: Flow<String> = dataStore.data.map { it.ha.eventSecret.orEmpty() }

    suspend fun config(): HaConfig = config.first()

    suspend fun save(
        urlInternal: String,
        urlExternal: String,
        token: String,
        homeNetworks: List<String>,
    ): Boolean {
        val sealed = token.takeIf(String::isNotBlank)?.let(HaTokenCipher::seal)
        if (token.isNotBlank() && sealed == null) return false
        dataStore.updateData { settings ->
            settings.copy(
                ha = settings.ha.copy(
                    urlInternal = normalizeHaUrl(urlInternal),
                    urlExternal = normalizeHaUrl(urlExternal),
                    token = sealed ?: settings.ha.token,
                    homeNetworks = homeNetworks,
                    // A pasted long-lived token replaces a signed-in session: two credentials for
                    // one instance would leave the user guessing which one is in use.
                    refreshToken = if (sealed != null) null else settings.ha.refreshToken,
                    oauthClientId = if (sealed != null) null else settings.ha.oauthClientId,
                ),
            )
        }
        return true
    }

    suspend fun saveSession(refreshToken: String, clientId: String): Boolean {
        val sealed = HaTokenCipher.seal(refreshToken) ?: return false
        dataStore.updateData { settings ->
            settings.copy(
                ha = settings.ha.copy(
                    refreshToken = sealed,
                    oauthClientId = clientId,
                    token = null,
                ),
            )
        }
        return true
    }

    suspend fun saveInstance(uuid: String, name: String) {
        dataStore.updateData { settings ->
            settings.copy(
                ha = settings.ha.copy(
                    instanceUuid = uuid.takeIf(String::isNotBlank),
                    instanceName = name.takeIf(String::isNotBlank),
                ),
            )
        }
    }

    suspend fun eventSecret(): String = eventSecret.first()

    suspend fun ensureEventSecret(): String = dataStore.updateData { settings ->
        val secret = settings.ha.eventSecret
        if (!secret.isNullOrBlank()) {
            settings
        } else {
            settings.copy(ha = settings.ha.copy(eventSecret = haGenerateEventSecret()))
        }
    }.ha.eventSecret.orEmpty()

    suspend fun rotateEventSecret(): String = dataStore.updateData { settings ->
        settings.copy(ha = settings.ha.copy(eventSecret = haGenerateEventSecret()))
    }.ha.eventSecret.orEmpty()

    suspend fun saveEventSecret(secret: String): Boolean {
        val trimmed = secret.trim()
        if (haEventSecretError(trimmed) != null) return false
        dataStore.updateData { settings -> settings.copy(ha = settings.ha.copy(eventSecret = trimmed)) }
        return true
    }

    suspend fun clearToken() {
        dataStore.updateData { settings ->
            settings.copy(
                ha = settings.ha.copy(
                    token = null,
                    refreshToken = null,
                    oauthClientId = null,
                ),
            )
        }
    }
}

private const val SEALED = "sealed"
