package app.cue.launcher.system.bridge

import android.app.Application
import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.IntentSender
import android.os.ParcelUuid
import app.cue.launcher.data.bridge.BridgeAssociation
import app.cue.launcher.system.setGatedComponentEnabled
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

internal sealed interface AssociationOutcome {

    class Pending(val sender: IntentSender) : AssociationOutcome

    class Created(val association: BridgeAssociation) : AssociationOutcome

    class Failed(val code: Int?) : AssociationOutcome
}

// The system's companion-device association with the Mac: the dialog that finds it by the GATT
// service it advertises, and the presence observation that binds BridgeCompanionService while it
// is in range. The launcher has no other association, so wiping means all of its own.
@SingleIn(AppScope::class)
@Inject
class BridgeCompanion(private val application: Application) {

    private val manager: CompanionDeviceManager?
        get() = application.getSystemService(CompanionDeviceManager::class.java)

    val available: Boolean get() = manager != null

    internal fun associate(onOutcome: (AssociationOutcome) -> Unit) {
        val manager = manager ?: return onOutcome(AssociationOutcome.Failed(null))
        val callback = object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                onOutcome(AssociationOutcome.Pending(intentSender))
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                BridgeLog.info(
                    "association created id=${associationInfo.id} " +
                        "extraPermissions=${associationInfo.extraPermissions.sorted()}",
                )
                onOutcome(AssociationOutcome.Created(associationOf(associationInfo)))
            }

            // The system calls both onFailure overloads; only the one with the code answers.
            override fun onFailure(error: CharSequence?) = Unit

            override fun onFailure(errorCode: Int, error: CharSequence?) {
                BridgeLog.info("association failed code=$errorCode")
                onOutcome(AssociationOutcome.Failed(errorCode))
            }
        }
        try {
            manager.associate(associationRequest(), application.mainExecutor, callback)
        } catch (failure: IllegalStateException) {
            BridgeLog.warn("association could not start", failure)
            onOutcome(AssociationOutcome.Failed(null))
        }
    }

    // Called right after the pairing is saved, so the service is switched on here rather than a moment
    // later by BridgeComponentGate: the system may look it up as soon as observation starts.
    fun observe(associationId: Int): Boolean {
        val manager = manager ?: return false
        application.setGatedComponentEnabled(BridgeCompanionService::class.java, true)
        val failure = try {
            manager.startObservingDevicePresence(presenceRequest(associationId))
            null
        } catch (refused: IllegalArgumentException) {
            refused
        } catch (refused: IllegalStateException) {
            refused
        } catch (refused: SecurityException) {
            refused
        }
        if (failure == null) {
            BridgeLog.info("presence observed for association $associationId")
        } else {
            BridgeLog.warn("presence observation could not start", failure)
        }
        return failure == null
    }

    // Unpairing, turning the bridge off and every new search start from no association at all, so an
    // association a cancelled pairing left behind never outlives the next attempt.
    fun forgetAll() {
        val manager = manager ?: return
        val associations = runCatching { manager.myAssociations }.getOrDefault(emptyList())
        associations.forEach { info ->
            runCatching { manager.stopObservingDevicePresence(presenceRequest(info.id)) }
            runCatching { manager.disassociate(info.id) }
                .onSuccess { BridgeLog.info("association ${info.id} removed") }
                .onFailure { BridgeLog.info("association ${info.id} could not be removed: ${it.javaClass.simpleName}") }
        }
    }

    private fun presenceRequest(associationId: Int) =
        ObservingDevicePresenceRequest.Builder().setAssociationId(associationId).build()

    // The ScanResult of the dialog's pick carries the address type; the plain MAC address of the
    // association does not, and a Mac advertises from a random address.
    private fun associationOf(info: AssociationInfo): BridgeAssociation {
        val device = info.associatedDevice?.bleDevice?.device
        return BridgeAssociation(
            id = info.id,
            address = device?.address ?: info.deviceMacAddress?.toString()?.uppercase(),
            addressType = device?.addressType,
        )
    }
}

// The nearby group is the one extra permission the association dialog may grant along with it; a
// framework that refuses the set gets the request without it, and the pairing screen asks for
// BLUETOOTH_CONNECT on its own whenever it is still missing afterwards.
internal fun associationRequest(): AssociationRequest {
    val filter = BluetoothLeDeviceFilter.Builder()
        .setScanFilter(ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BRIDGE_GATT_SERVICE)).build())
        .build()
    val builder = AssociationRequest.Builder()
        .addDeviceFilter(filter)
        .setSingleDevice(true)
    try {
        builder.setExtraPermissions(setOf(AssociationRequest.PERMISSION_GROUP_NEARBY))
    } catch (failure: IllegalArgumentException) {
        BridgeLog.info("association without extra permissions: ${failure.javaClass.simpleName}")
    }
    return builder.build()
}
