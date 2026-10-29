package app.cue.launcher.system.bridge

import android.companion.ActionRequest
import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import app.cue.launcher.CueApplication

// Bound by the system while the paired Mac's advertisement is in range, and only while the bridge is
// on and paired: the manifest declares it disabled and BridgeComponentGate enables it. The window is
// built here, so a process the system starts for this service builds nothing else of the bridge.
class BridgeCompanionService : CompanionDeviceService() {

    private var window: BridgeWindow? = null

    override fun onCreate() {
        super.onCreate()
        window = (applicationContext as? CueApplication)?.graph?.bridgeWindow?.also { it.open() }
    }

    override fun onDestroy() {
        window?.close()
        window = null
        super.onDestroy()
    }

    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        window?.presence(event.associationId, event.event)
    }

    override fun onActionRequested(associationInfo: AssociationInfo, request: ActionRequest) {
        window?.action(associationInfo.id, request.action, request.operation)
    }
}
