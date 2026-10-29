package app.cue.launcher.feature.settings.bridge

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import app.cue.launcher.data.bridge.BridgeConfigRepository
import app.cue.launcher.system.bridge.BridgeBrowser
import app.cue.launcher.system.bridge.BridgeCompanion
import app.cue.launcher.system.bridge.BridgeNearby
import app.cue.launcher.system.bridge.BridgePairing
import app.cue.launcher.system.bridge.BridgeSessionLink
import app.cue.launcher.system.bridge.BridgeTeardown
import app.cue.launcher.system.notifications.NotificationListenerLink
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Suppress("LongParameterList") // what the screen reaches, resolved in one go when it first opens
@Stable
@SingleIn(AppScope::class)
@Inject
class BridgeServices(
    val config: BridgeConfigRepository,
    val link: BridgeSessionLink,
    val pairing: BridgePairing,
    val browser: BridgeBrowser,
    val notificationListener: NotificationListenerLink,
    val companion: BridgeCompanion,
    val nearby: BridgeNearby,
    val teardown: BridgeTeardown,
)

// A provider, not the services: the graph builds them the first time the bridge screen opens.
val LocalBridgeServices = staticCompositionLocalOf<() -> BridgeServices> { error("No bridge services provided") }
