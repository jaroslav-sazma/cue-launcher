package app.cue.launcher.data.bridge

import androidx.datastore.core.DataStore
import app.cue.launcher.data.settings.LauncherSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class OpenedPairing(
    val macName: String,
    val host: String,
    val port: Int,
    val macFingerprint: String,
    val bleKey: ByteArray,
) {
    val keyTag: String = bridgeKeyTag(bleKey)
}

enum class CommitOutcome { SAVED, NOT_SEALED, BRIDGE_OFF }

class PairingCommit(
    val macName: String,
    val host: String,
    val port: Int,
    val macFingerprint: String,
    val bleKey: ByteArray,
    val association: BridgeAssociation?,
)

@Suppress("TooManyFunctions") // one transaction per change of the stored record
@SingleIn(AppScope::class)
@Inject
class BridgeConfigRepository(
    private val dataStore: DataStore<LauncherSettings>,
    private val sealer: BridgeKeySealer,
    private val sequences: BridgeSequences,
) {

    val settings: Flow<BridgeSettings> = dataStore.data.map { it.bridge }.distinctUntilChanged()

    suspend fun settings(): BridgeSettings = settings.first()

    suspend fun enable() {
        dataStore.updateData { it.copy(bridge = it.bridge.copy(enabled = true)) }
    }

    // Off is teardown, not a pause: the pin, K_ble, the sequence state and the endpoints all go; the
    // caller deletes the TLS identity as well.
    suspend fun disable() {
        dataStore.updateData { it.copy(bridge = BridgeSettings()) }
        sequences.wipe()
        withContext(Dispatchers.IO) { sealer.destroy() }
    }

    // The clipboard choices and the apps kept off the Mac are the user's and not the pairing's, so they
    // outlive an unpair.
    suspend fun unpair() {
        dataStore.updateData { stored ->
            val bridge = stored.bridge
            stored.copy(
                bridge = BridgeSettings(
                    enabled = bridge.enabled,
                    clipboardFromMac = bridge.clipboardFromMac,
                    clipboardMaxAgeMinutes = bridge.clipboardMaxAgeMinutes,
                    mirrorExcluded = bridge.mirrorExcluded,
                ),
            )
        }
        sequences.wipe()
        withContext(Dispatchers.IO) { sealer.destroy() }
    }

    suspend fun setMirrorExcluded(packageName: String, excluded: Boolean) {
        dataStore.updateData { stored ->
            val current = stored.bridge.mirrorExcluded
            val next = if (excluded) current + packageName else current - packageName
            stored.copy(bridge = stored.bridge.copy(mirrorExcluded = next))
        }
    }

    suspend fun setClipboardFromMac(on: Boolean) {
        dataStore.updateData { it.copy(bridge = it.bridge.copy(clipboardFromMac = on)) }
    }

    suspend fun setClipboardMaxAge(minutes: Int) {
        val clamped = minutes.coerceIn(CLIPBOARD_MAX_AGE_MINUTES)
        dataStore.updateData { it.copy(bridge = it.bridge.copy(clipboardMaxAgeMinutes = clamped)) }
    }

    // A re-pairing replaces K_ble, so both sequences start again from zero. The fresh sequence state
    // is written first and belongs to the new key only, so a commit cut short leaves the old pairing
    // unusable rather than its key with counters at zero. The switch is read in the same transaction
    // that writes the pairing, so a ceremony that outlives turning the bridge off cannot store
    // anything.
    suspend fun commitPairing(commit: PairingCommit, devEndpoint: Boolean): CommitOutcome {
        val sealed = withContext(Dispatchers.IO) { sealer.seal(commit.bleKey) } ?: return CommitOutcome.NOT_SEALED
        sequences.start(commit.macFingerprint, bridgeKeyTag(commit.bleKey))
        val record = BridgePairingRecord(
            macName = commit.macName,
            host = commit.host,
            port = commit.port,
            macFingerprint = commit.macFingerprint,
            bleKey = sealed,
            association = commit.association,
        )
        var outcome = CommitOutcome.BRIDGE_OFF
        dataStore.updateData { stored ->
            if (!stored.bridge.enabled) {
                outcome = CommitOutcome.BRIDGE_OFF
                stored
            } else {
                outcome = CommitOutcome.SAVED
                stored.copy(
                    bridge = stored.bridge.copy(
                        pairing = record,
                        devEndpoint = if (devEndpoint) BridgeDevEndpoint(commit.host, commit.port) else null,
                    ),
                )
            }
        }
        if (outcome == CommitOutcome.BRIDGE_OFF) {
            sequences.wipe()
            withContext(Dispatchers.IO) { sealer.destroy() }
        }
        return outcome
    }

    // A key without its own sequence state is unusable: its counters are unknown, and starting them
    // at zero again would reuse nonces. Only a new pairing makes it usable.
    suspend fun openPairing(): OpenedPairing? {
        val record = settings().pairing ?: return null
        return withContext(Dispatchers.IO) { sealer.open(record.bleKey) }
            ?.let { key ->
                OpenedPairing(
                    macName = record.macName,
                    host = record.host,
                    port = record.port,
                    macFingerprint = record.macFingerprint,
                    bleKey = key,
                )
            }
            ?.takeIf { sequences.matches(it.macFingerprint, it.keyTag) }
    }

    // A stored pairing that openPairing refuses: its key or its sequence state is lost, and only a new
    // pairing helps.
    suspend fun pairingUnusable(): Boolean {
        val opened = openPairing() ?: return settings().pairing != null
        opened.bleKey.fill(0)
        return false
    }

    suspend fun reserveOutgoingSeq(pairing: OpenedPairing): Long? =
        sequences.reserveOutgoing(pairing.macFingerprint, pairing.keyTag)

    suspend fun incomingWindow(pairing: OpenedPairing): BridgeReplayWindow? =
        sequences.incomingWindow(pairing.macFingerprint, pairing.keyTag)

    suspend fun recordIncomingSeq(pairing: OpenedPairing, seq: Long): Boolean =
        sequences.recordIncoming(pairing.macFingerprint, pairing.keyTag, seq)

    // The same Mac at a new address: the pin is what says it is the same one.
    suspend fun moveEndpoint(macFingerprint: String, host: String, port: Int): Boolean {
        var moved = false
        dataStore.updateData { stored ->
            val record = stored.bridge.pairing
                ?.takeIf { it.macFingerprint == macFingerprint && (it.host != host || it.port != port) }
            moved = record != null
            record?.let { stored.withPairing(it.copy(host = host, port = port)) } ?: stored
        }
        return moved
    }

    suspend fun setDevEndpoint(host: String, port: Int) {
        dataStore.updateData { stored ->
            if (stored.bridge.pairing == null) {
                stored
            } else {
                val endpoint = BridgeDevEndpoint(host.trim().ifEmpty { BRIDGE_DEV_HOST }, port)
                stored.copy(bridge = stored.bridge.copy(devEndpoint = endpoint))
            }
        }
    }

    private fun LauncherSettings.withPairing(record: BridgePairingRecord) =
        copy(bridge = bridge.copy(pairing = record))
}
