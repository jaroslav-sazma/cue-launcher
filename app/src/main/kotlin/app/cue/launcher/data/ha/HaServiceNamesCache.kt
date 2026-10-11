package app.cue.launcher.data.ha

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

// Service names are Home Assistant's own translations and hardly ever change, so one socket per
// instance and language serves every later opening of the action form. A list carrying a domain the
// names were not read with means a new integration, and the names are read again; so they are after a
// day, for whatever an update renamed, and for another instance, address or language. Only a read that
// brought names is kept: a failed one is tried again the next time.
class HaServiceNamesCache(
    private val now: () -> Long = System::currentTimeMillis,
    private val lifetime: Duration = NAMES_LIFETIME,
) {

    private val mutex = Mutex()

    private var cached: Entry? = null

    suspend fun names(
        instance: String,
        language: String,
        domains: Set<String>,
        read: suspend () -> Map<String, String>,
    ): Map<String, String> = mutex.withLock {
        val fresh = cached?.takeIf { it.instance == instance && it.language == language && it.covers(domains) }
        fresh?.names ?: read().also { names ->
            if (names.isNotEmpty()) cached = Entry(instance, language, domains, names, now())
        }
    }

    private fun Entry.covers(domains: Set<String>): Boolean =
        this.domains.containsAll(domains) && now() - readAt in 0 until lifetime.inWholeMilliseconds

    private class Entry(
        val instance: String,
        val language: String,
        val domains: Set<String>,
        val names: Map<String, String>,
        val readAt: Long,
    )
}

fun haInstanceKey(config: HaConfig): String = listOf(config.instanceUuid, config.urlInternal, config.urlExternal)
    .joinToString("|") { it.trim() }

private val NAMES_LIFETIME = 24.hours
