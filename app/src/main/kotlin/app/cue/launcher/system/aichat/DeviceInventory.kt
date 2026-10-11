package app.cue.launcher.system.aichat

import android.app.Application
import app.cue.launcher.data.aichat.AiChatShare
import app.cue.launcher.data.aichat.InventoryApp
import app.cue.launcher.data.aichat.InventoryAppAction
import app.cue.launcher.data.aichat.InventoryDevice
import app.cue.launcher.data.aichat.InventorySnapshot
import app.cue.launcher.data.aichat.InventorySource
import app.cue.launcher.data.aichat.InventoryWidget
import app.cue.launcher.data.ha.HaEntity
import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.ha.HaService
import app.cue.launcher.data.ha.HaServiceList
import app.cue.launcher.data.launchlog.LaunchLogRepository
import app.cue.launcher.data.launchlog.LaunchStats
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.system.AppActionEntry
import app.cue.launcher.system.AppActionKind
import app.cue.launcher.system.AppActionsRepository
import app.cue.launcher.system.ConnectedBtDevice
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.SearchableApp
import app.cue.launcher.system.bluetoothConnectGranted
import app.cue.launcher.system.connectedBluetoothDevices
import app.cue.launcher.system.ha.HaGateway
import app.cue.launcher.system.pairedBluetoothDevices
import app.cue.launcher.system.unavailableSystemActions
import app.cue.launcher.system.widget.AvailableWidget
import app.cue.launcher.system.widget.WidgetCatalog
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

// One read per call, nothing observed afterwards: it is asked only while a screen waits for it.
@SingleIn(AppScope::class)
@Inject
class DeviceInventory(
    private val application: Application,
    private val apps: LauncherAppsRepository,
    private val launchLog: LaunchLogRepository,
    private val ha: HaGateway,
    private val widgetCatalog: WidgetCatalog,
    private val appActions: AppActionsRepository,
) : InventorySource {

    override suspend fun snapshot(share: AiChatShare): InventorySnapshot = coroutineScope {
        val installed = async { installedApps() }
        val devices = async { if (share.bluetooth) bluetoothDevices() else null }
        val entities = async { if (share.homeAssistant) haEntities() else null }
        val widgets = async { inventoryWidgets(widgetCatalog.availableWidgets()) }
        InventorySnapshot(
            apps = installed.await(),
            devices = devices.await(),
            haEntities = entities.await(),
            widgets = widgets.await(),
            unavailableSystemActions = unavailableSystemActions(application),
        )
    }

    override suspend fun appActions(packageName: String, shortcuts: Boolean): List<InventoryAppAction>? =
        withTimeoutOrNull(ACTIONS_TIMEOUT) { inventoryActions(appActions.actionsOf(packageName, shortcuts)) }

    override suspend fun haServices(): List<HaService>? =
        offeredServices(withTimeoutOrNull(HA_TIMEOUT) { ha.services() })

    // The searchable list arrives twice, the second time with English labels; those are what an
    // English request names. take(2) ends the collection, and with it the app-list callback and
    // receiver behind the flow, as does a cancellation in the middle of the read.
    private suspend fun installedApps(): List<InventoryApp> {
        val searchable = withTimeoutOrNull(APPS_TIMEOUT) { apps.observeSearchableApps().take(2).last() }
            ?: apps.installedApps().map(::SearchableApp)
        return recentFirst(searchable, launchLog.observeLaunchStats().first())
    }

    private suspend fun bluetoothDevices(): List<InventoryDevice>? {
        if (!bluetoothConnectGranted(application)) return null
        return inventoryDevices(connectedBluetoothDevices(application) + pairedBluetoothDevices(application))
    }

    private suspend fun haEntities(): List<HaEntity>? {
        val list = withTimeoutOrNull(HA_TIMEOUT) { ha.entities() } ?: return null
        return list.entities.takeIf { list.probe is HaProbe.Ok }
    }
}

// A body that did not parse also arrives as Ok, with no services; every instance has some, so an
// empty list is as unknown as a failed read, and grounding must not call every service invented.
internal fun offeredServices(list: HaServiceList?): List<HaService>? =
    list?.services?.takeIf { list.probe is HaProbe.Ok && it.isNotEmpty() }

// The most recently used apps lead; an app with a launcher entry in a work profile as well is listed
// once.
internal fun recentFirst(searchable: List<SearchableApp>, stats: Map<ComponentRef, LaunchStats>): List<InventoryApp> {
    val lastUsed = stats.entries
        .groupBy({ it.key.packageName }, { it.value.lastLaunchedAt })
        .mapValues { (_, times) -> times.max() }
    return searchable
        .sortedByDescending { lastUsed[it.app.component.packageName] ?: 0L }
        .map { InventoryApp(it.app.label, it.app.component.packageName, it.englishLabel) }
        .distinctBy(InventoryApp::packageName)
}

// The picker's list, so a draft names only what the builder can bind; a provider in a work profile
// as well is listed once, as the scenario names it by package and class alone.
internal fun inventoryWidgets(widgets: List<AvailableWidget>): List<InventoryWidget> = widgets
    .distinctBy { it.provider.packageName to it.provider.className }
    .map {
        InventoryWidget(
            label = it.label,
            appLabel = it.appLabel,
            packageName = it.provider.packageName,
            provider = it.provider.className,
            smallestWidthPx = it.size.smallestWidthPx,
            smallestHeightPx = it.size.smallestHeightPx,
        )
    }

// A configurable shortcut needs its app's own setup screen first, which a drafted tile cannot open.
internal fun inventoryActions(entries: List<AppActionEntry>): List<InventoryAppAction> = entries
    .filter { it.kind != AppActionKind.CONFIGURABLE }
    .mapNotNull { entry -> entry.action?.let { InventoryAppAction(entry.label, it) } }
    .distinctBy(InventoryAppAction::action)

internal fun inventoryDevices(devices: List<ConnectedBtDevice>): List<InventoryDevice> =
    devices.distinctBy { it.address }.map { InventoryDevice(name = it.name, mac = it.address) }

private val APPS_TIMEOUT = 3.seconds
private val HA_TIMEOUT = 6.seconds
private val ACTIONS_TIMEOUT = 2.seconds
