package app.cue.launcher.data.aichat

import app.cue.launcher.data.ha.HaEntity
import app.cue.launcher.data.ha.HaService
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class InventoryApp(
    val label: String,
    val packageName: String,
    val englishLabel: String? = null,
)

data class InventoryDevice(val name: String?, val mac: String)

// provider is the full class name, as the scenario's app_widget content holds it. The smallest size
// is what the provider can be shrunk to, which decides the tiles it fits.
data class InventoryWidget(
    val label: String,
    val appLabel: String,
    val packageName: String,
    val provider: String,
    val smallestWidthPx: Int = 0,
    val smallestHeightPx: Int = 0,
)

// A shortcut, deep link, capability or activity an app offers; the action is what a tile item runs.
data class InventoryAppAction(val label: String, val action: TapAction)

// Null means unknown rather than empty — no Bluetooth permission, Home Assistant not set up or not
// reachable, widget providers unreadable, or kept out of the brief by the user — and grounding skips
// what it cannot know. appActions holds only the apps they were read for, keyed by package, and
// haServices only when they were asked for: snapshot() reads neither, the brief for the chat reads
// the actions always, without shortcuts when they are kept back, and the services unless Home
// Assistant is kept back.
data class InventorySnapshot(
    val apps: List<InventoryApp> = emptyList(),
    val devices: List<InventoryDevice>? = null,
    val haEntities: List<HaEntity>? = null,
    val widgets: List<InventoryWidget>? = null,
    val appActions: Map<String, List<InventoryAppAction>> = emptyMap(),
    val haServices: List<HaService>? = null,
    val unavailableSystemActions: Set<SystemActionType> = emptySet(),
) {
    companion object {
        val EMPTY = InventorySnapshot()
    }
}

fun interface InventorySource {
    // A part the share keeps back is never asked of the system and comes back null.
    suspend fun snapshot(share: AiChatShare): InventorySnapshot

    // Null when the source cannot tell. Without shortcuts the app's shortcuts are not even read, and
    // neither are its capabilities, which come from them.
    suspend fun appActions(packageName: String, shortcuts: Boolean = true): List<InventoryAppAction>? = null

    // Null when Home Assistant is not set up or its list could not be read.
    suspend fun haServices(): List<HaService>? = null
}

// Reused only within one screen: the brief read again for saving or after another choice, a pasted
// answer checked again. No timer drops it: the screen that reads it clears it when it stops, so the
// brief and the check of its answer never share a read, and a read is reused for at most the lifetime.
class CachedInventory(
    private val source: InventorySource,
    private val now: () -> Long = System::currentTimeMillis,
    private val lifetime: Duration = INVENTORY_LIFETIME,
) : InventorySource {

    private val mutex = Mutex()

    // Keyed by share: a snapshot that kept a part back must never stand in for the whole phone.
    private val cached = ConcurrentHashMap<AiChatShare, Pair<Long, InventorySnapshot>>()

    // Keyed by package and whether shortcuts were read: a read without them must never stand in for the
    // full one grounding needs, nor a full one leak shortcuts into a brief that keeps them back.
    private val actions = ConcurrentHashMap<Pair<String, Boolean>, Pair<Long, List<InventoryAppAction>?>>()

    @Volatile
    private var services: Pair<Long, List<HaService>?>? = null

    override suspend fun snapshot(share: AiChatShare): InventorySnapshot = mutex.withLock {
        cached.values.removeAll { (readAt, _) -> !isFresh(readAt) }
        cached[share]?.second ?: source.snapshot(share).also { cached[share] = now() to it }
    }

    // Outside the lock: the brief reads every app's actions side by side, and one lock would queue them.
    override suspend fun appActions(packageName: String, shortcuts: Boolean): List<InventoryAppAction>? {
        actions.values.removeAll { (readAt, _) -> !isFresh(readAt) }
        val key = packageName to shortcuts
        val fresh = actions[key]
        return if (fresh != null) {
            fresh.second
        } else {
            source.appActions(packageName, shortcuts).also { actions[key] = now() to it }
        }
    }

    override suspend fun haServices(): List<HaService>? = mutex.withLock {
        val fresh = services?.takeIf { (readAt, _) -> isFresh(readAt) }
        if (fresh != null) fresh.second else source.haServices().also { services = now() to it }
    }

    fun clear() {
        cached.clear()
        actions.clear()
        services = null
    }

    private fun isFresh(readAt: Long): Boolean = now() - readAt < lifetime.inWholeMilliseconds
}

private val INVENTORY_LIFETIME = 2.minutes
