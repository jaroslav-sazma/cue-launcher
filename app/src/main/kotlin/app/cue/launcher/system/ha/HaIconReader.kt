package app.cue.launcher.system.ha

import android.app.Application
import android.util.Log
import app.cue.launcher.data.ha.HaConfigRepository
import app.cue.launcher.data.ha.HaIconTable
import app.cue.launcher.engine.WallClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

// Home Assistant's default icons, kept on disk. They are read only while a tile on screen has a Home
// Assistant icon nobody chose, and again only for a domain the table was not read for or once a
// handshake of this instance announces another version. A failed read waits before the next, unless
// a handshake has come through since — a token put right, a network back.
@SingleIn(AppScope::class)
class HaIconReader internal constructor(
    private val instance: Flow<String?>,
    private val handshakes: StateFlow<HaHandshake?>,
    private val fetch: suspend (Set<String>) -> HaIconTable?,
    private val load: suspend () -> HaIconTable?,
    private val save: suspend (HaIconTable) -> Unit,
    private val clock: WallClock,
) {

    @Inject
    constructor(
        configs: HaConfigRepository,
        version: HaInstanceVersion,
        gateway: HaGateway,
        application: Application,
        clock: WallClock,
    ) : this(
        instance = configs.iconInstance,
        handshakes = version.seen,
        fetch = gateway::icons,
        load = { loadTable(application) },
        save = { saveTable(application, it) },
        clock = clock,
    )

    private val mutex = Mutex()

    private var table: HaIconTable? = null

    private var loaded = false

    private var failure: Failure? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(domains: Set<String>): Flow<HaIconTable?> {
        if (domains.isEmpty()) return flowOf(null)
        return combine(instance.distinctUntilChanged(), handshakes, ::Pair)
            .transformLatest { (instance, handshake) ->
                if (instance == null) {
                    emit(null)
                    return@transformLatest
                }
                val known = stored()?.takeIf { it.instance == instance }
                emit(known)
                if (known == null || known.isOutdated(handshake.versionOf(instance)) || !known.covers(domains)) {
                    refreshed(instance, handshake, domains)?.let { emit(it) }
                }
            }
            .distinctUntilChanged()
    }

    private suspend fun stored(): HaIconTable? = mutex.withLock {
        if (!loaded) {
            table = load()
            loaded = true
        }
        table
    }

    // A table still current for the version only lacks domains, and keeps the ones it has.
    private suspend fun refreshed(instance: String, handshake: HaHandshake?, domains: Set<String>): HaIconTable? =
        mutex.withLock {
            val current = table?.takeIf { it.instance == instance && !it.isOutdated(handshake.versionOf(instance)) }
            if (current != null && current.covers(domains)) return current
            val now = clock.nowMillis()
            if (failure?.holds(instance, handshake, now) == true) return null
            val read = fetch(domains - current?.asked.orEmpty())?.takeIf { it.instance == instance }
            failure = if (read == null) Failure(instance, handshake?.count, now) else null
            read?.let { current?.with(it) ?: it }?.also {
                table = it
                save(it)
            }
        }

    private data class Failure(val instance: String, val handshake: Int?, val at: Long) {

        fun holds(instance: String, handshake: HaHandshake?, now: Long): Boolean =
            instance == this.instance && handshake?.count == this.handshake && now - at in 0 until RETRY_MILLIS
    }

    private companion object {

        const val RETRY_MILLIS = 15 * 60_000L

        const val FILE = "ha_icons.json"

        const val TAG = "HaIcons"

        val json = Json { ignoreUnknownKeys = true }

        // Outside the backups: it is Home Assistant's to give, and a restored copy would only be
        // read again.
        suspend fun loadTable(application: Application): HaIconTable? = withContext(Dispatchers.IO) {
            val file = File(application.noBackupFilesDir, FILE)
            if (!file.exists()) return@withContext null
            runCatching { json.decodeFromString(HaIconTable.serializer(), file.readText()) }
                .onFailure { Log.w(TAG, "The icon table cannot be read (${it::class.java.simpleName})") }
                .getOrNull()
        }

        suspend fun saveTable(application: Application, table: HaIconTable) = withContext(Dispatchers.IO) {
            val file = File(application.noBackupFilesDir, FILE)
            val partial = File(file.path + ".tmp")
            try {
                partial.writeText(json.encodeToString(HaIconTable.serializer(), table))
                if (!partial.renameTo(file)) partial.delete()
            } catch (failure: IOException) {
                Log.w(TAG, "The icon table cannot be stored", failure)
            }
        }
    }
}

// The version a handshake of another instance announced says nothing about this one.
private fun HaHandshake?.versionOf(instance: String): String? = this?.takeIf { it.instance == instance }?.version
