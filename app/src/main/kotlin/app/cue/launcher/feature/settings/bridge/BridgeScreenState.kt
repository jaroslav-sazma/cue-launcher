package app.cue.launcher.feature.settings.bridge

import android.content.IntentSender
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cue.launcher.R
import app.cue.launcher.data.bridge.BridgeAssociation
import app.cue.launcher.data.bridge.BridgeSettings
import app.cue.launcher.designsystem.FlashState
import app.cue.launcher.system.bridge.AssociationOutcome
import app.cue.launcher.system.bridge.BleSignal
import app.cue.launcher.system.bridge.MacBrowse
import app.cue.launcher.system.bridge.PairingHandle
import app.cue.launcher.system.bridge.PairingTarget
import app.cue.launcher.system.bridge.notificationKeyHash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface MacSearch {

    data object Idle : MacSearch

    data object Searching : MacSearch

    data class Found(val association: BridgeAssociation) : MacSearch

    data class Failed(val cancelled: Boolean) : MacSearch
}

internal sealed interface MacPick {

    data object Idle : MacPick

    data object Browsing : MacPick

    data object None : MacPick

    data class Several(val targets: List<PairingTarget>) : MacPick
}

internal class BrowseActions(val start: () -> Unit, val again: () -> Unit, val stop: () -> Unit)

internal sealed interface BridgeConfirm {
    val macName: String

    data class TurnOff(override val macName: String) : BridgeConfirm

    data class Unpair(override val macName: String) : BridgeConfirm
}

// The pairing runs in the screen's scope: leaving the screen ends it, and so does onStop. Turning off
// and unpairing run in the app's, and finish whatever the screen does.
@Suppress("TooManyFunctions") // one handler per control on the screen
@Stable
internal class BridgeScreenState(
    private val services: BridgeServices,
    private val scope: CoroutineScope,
    private val flash: FlashState,
) {

    var handle by mutableStateOf<PairingHandle?>(null)
        private set

    var confirm by mutableStateOf<BridgeConfirm?>(null)

    var search by mutableStateOf<MacSearch>(MacSearch.Idle)
        private set

    var pick by mutableStateOf<MacPick>(MacPick.Idle)
        private set

    private var browsing: Job? = null

    val link get() = services.link.state

    val nearby get() = services.nearby.state

    val settings get() = services.config.settings

    // Measured again on every start of the home screen, so a grant made in the system settings shows
    // on return.
    val notificationListener get() = services.notificationListener.state

    fun enable() {
        scope.launch {
            services.teardown.settled()
            services.config.enable()
        }
    }

    suspend fun pairingUnusable(): Boolean = services.config.pairingUnusable()

    // An unusable pairing is of no use to keep, so it goes without asking.
    fun pairAgain() = unpair()

    fun setMirrorExcluded(packageName: String, excluded: Boolean) {
        scope.launch { services.config.setMirrorExcluded(packageName, excluded) }
    }

    // Off is teardown, so a pairing that would go with it is confirmed first.
    fun requestOff(settings: BridgeSettings) {
        val pairing = settings.pairing
        if (pairing != null) confirm = BridgeConfirm.TurnOff(pairing.macName) else turnOff()
    }

    fun requestUnpair(macName: String) {
        confirm = BridgeConfirm.Unpair(macName)
    }

    fun confirmed(asked: BridgeConfirm) {
        confirm = null
        if (asked is BridgeConfirm.TurnOff) turnOff() else unpair()
    }

    // The system's companion dialog finds the Mac by the service it advertises. Whatever an earlier,
    // unfinished search left behind is removed first, so only the Mac picked now stays associated.
    fun findMac(showDialog: (IntentSender) -> Unit) {
        stopBrowse()
        search = MacSearch.Searching
        scope.launch {
            services.teardown.settled()
            withContext(Dispatchers.IO) { services.companion.forgetAll() }
            services.companion.associate { outcome ->
                when (outcome) {
                    is AssociationOutcome.Pending -> showDialog(outcome.sender)
                    is AssociationOutcome.Created -> search = MacSearch.Found(outcome.association)
                    is AssociationOutcome.Failed -> if (search == MacSearch.Searching) {
                        search = MacSearch.Failed(cancelled = false)
                    }
                }
            }
        }
    }

    // A dialog left without a pick ends the search; a pick is reported by the association itself.
    fun searchClosed(picked: Boolean) {
        if (!picked && search == MacSearch.Searching) search = MacSearch.Failed(cancelled = true)
    }

    // After the companion dialog the Mac is looked for once more, on the network, by the flag its
    // advertisement carries in pairing mode. A single one is paired with at once.
    fun browseIfIdle() {
        if (pick == MacPick.Idle) browse()
    }

    fun browse() {
        browsing?.cancel()
        pick = MacPick.Browsing
        browsing = scope.launch {
            when (val outcome = services.browser.browse()) {
                MacBrowse.None -> pick = MacPick.None
                is MacBrowse.Several -> pick = MacPick.Several(outcome.targets)
                is MacBrowse.One -> {
                    pick = MacPick.Idle
                    startPairing(outcome.target, devTransport = false)
                }
            }
        }
    }

    // A browse cut short by onStop starts over on the next start.
    fun stopBrowse() {
        if (browsing?.isActive == true) {
            browsing?.cancel()
            pick = MacPick.Idle
        }
        browsing = null
    }

    fun startPairing(target: PairingTarget, devTransport: Boolean) {
        val association = (search as? MacSearch.Found)?.association?.takeUnless { devTransport }
        pick = MacPick.Idle
        handle = services.pairing.start(scope, target, association)
    }

    fun resetPairing() {
        handle = null
        pick = MacPick.Idle
    }

    fun setClipboardFromMac(on: Boolean) {
        scope.launch { services.config.setClipboardFromMac(on) }
    }

    fun setClipboardMaxAge(minutes: Int) {
        scope.launch { services.config.setClipboardMaxAge(minutes) }
    }

    fun saveEndpoint(host: String, port: Int) {
        scope.launch {
            services.config.setDevEndpoint(host, port)
            flash.show(R.string.bridge_debug_saved)
        }
    }

    fun sendTestSignal() {
        scope.launch {
            val sent = services.link.send(BleSignal.NotifPosted(notificationKeyHash(TEST_KEY)))
            flash.show(if (sent) R.string.bridge_debug_signal_sent else R.string.bridge_debug_signal_failed, !sent)
        }
    }

    private fun turnOff() {
        handle?.cancel()
        handle = null
        stopBrowse()
        pick = MacPick.Idle
        search = MacSearch.Idle
        services.teardown.turnOff()
    }

    private fun unpair() {
        handle = null
        stopBrowse()
        pick = MacPick.Idle
        search = MacSearch.Idle
        services.teardown.unpair()
    }

    private companion object {
        const val TEST_KEY = "cue-bridge-debug-signal"
    }
}
