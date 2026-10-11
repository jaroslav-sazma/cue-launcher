package app.cue.launcher.di

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.room.Room
import app.cue.launcher.data.CueDatabase
import app.cue.launcher.data.aichat.CachedInventory
import app.cue.launcher.data.drawer.HiddenAppDao
import app.cue.launcher.data.drawer.HiddenAppRepository
import app.cue.launcher.data.ha.HaConfigRepository
import app.cue.launcher.data.ha.HaEventLogDao
import app.cue.launcher.data.ha.HaEventLogRepository
import app.cue.launcher.data.ha.HaSessionTokens
import app.cue.launcher.data.icons.IconOverrideDao
import app.cue.launcher.data.launchlog.AppLaunchDao
import app.cue.launcher.data.launchlog.LaunchLogRepository
import app.cue.launcher.data.layout.FolderMemberDao
import app.cue.launcher.data.layout.LauncherLayoutDao
import app.cue.launcher.data.layout.LauncherLayoutRepository
import app.cue.launcher.data.scenario.ActivationDao
import app.cue.launcher.data.scenario.ActivationRepository
import app.cue.launcher.data.scenario.ScenarioDao
import app.cue.launcher.data.scenario.ScenarioRepository
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.settings.LauncherSettingsSerializer
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.data.widgets.HostedWidgetDao
import app.cue.launcher.data.widgets.HostedWidgetRepository
import app.cue.launcher.data.widgets.PanelColumnRepository
import app.cue.launcher.data.widgets.PanelWidgetDao
import app.cue.launcher.engine.ActivationStore
import app.cue.launcher.engine.AlarmMonitor
import app.cue.launcher.engine.AlarmMemory
import app.cue.launcher.engine.ConditionMonitor
import app.cue.launcher.engine.HaEventLog
import app.cue.launcher.engine.HaStateSource
import app.cue.launcher.engine.LocalClock
import app.cue.launcher.engine.ScenarioEngine
import app.cue.launcher.engine.ScenarioSource
import app.cue.launcher.engine.StateProvider
import app.cue.launcher.engine.SystemStateSource
import app.cue.launcher.engine.WallClock
import app.cue.launcher.system.ActionExecutor
import app.cue.launcher.system.AppActionsRepository
import app.cue.launcher.system.AudioConditions
import app.cue.launcher.system.BluetoothConditions
import app.cue.launcher.system.CalendarConditions
import app.cue.launcher.system.CalendarRepository
import app.cue.launcher.system.media.MediaPlayers
import app.cue.launcher.system.ContactsRepository
import app.cue.launcher.system.ForegroundSession
import app.cue.launcher.system.ConfigBackup
import app.cue.launcher.system.HaConditions
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.MediaConditions
import app.cue.launcher.system.PowerConditions
import app.cue.launcher.system.RingerConditions
import app.cue.launcher.system.SystemActions
import app.cue.launcher.system.SystemConditions
import app.cue.launcher.system.TimeConditions
import app.cue.launcher.system.WifiConditions
import app.cue.launcher.system.WallpaperLuminance
import app.cue.launcher.system.aichat.DeviceInventory
import app.cue.launcher.system.ha.HaAuthFlow
import app.cue.launcher.system.ha.HaBlueprintUploader
import app.cue.launcher.system.ha.HaConnectionTester
import app.cue.launcher.system.ha.HaDiscovery
import app.cue.launcher.system.ha.HaEndpointSelector
import app.cue.launcher.system.ha.HaGateway
import app.cue.launcher.system.ha.HaStateReader
import app.cue.launcher.system.ha.HaTokenClient
import app.cue.launcher.system.iconpack.IconPackRepository
import app.cue.launcher.system.notifications.NotificationListenerLink
import app.cue.launcher.system.UserProfiles
import app.cue.launcher.system.logUnexpected
import app.cue.launcher.system.widget.WidgetCatalog
import app.cue.launcher.system.widget.WidgetHost
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@DependencyGraph(AppScope::class)
interface AppGraph {

    val wallpaperLuminance: WallpaperLuminance

    val launcherAppsRepository: LauncherAppsRepository

    val launcherLayoutRepository: LauncherLayoutRepository

    val launchLogRepository: LaunchLogRepository

    val settingsRepository: SettingsRepository

    val hostedWidgetRepository: HostedWidgetRepository

    val panelColumnRepository: PanelColumnRepository

    val scenarioRepository: ScenarioRepository

    val activationRepository: ActivationRepository

    val haConfigRepository: HaConfigRepository

    val haEventLogRepository: HaEventLogRepository

    val haConnectionTester: HaConnectionTester

    val haEndpointSelector: HaEndpointSelector

    val haGateway: HaGateway

    val haStateReader: HaStateReader

    val haDiscovery: HaDiscovery

    val haAuthFlow: HaAuthFlow

    val haBlueprintUploader: HaBlueprintUploader

    val calendarRepository: CalendarRepository

    val mediaPlayers: MediaPlayers

    val contactsRepository: ContactsRepository

    val hiddenAppRepository: HiddenAppRepository

    val iconPackRepository: IconPackRepository

    val notificationListener: NotificationListenerLink

    val userProfiles: UserProfiles

    val scenarioEngine: ScenarioEngine

    val conditionMonitor: ConditionMonitor

    val alarmMonitor: AlarmMonitor

    val systemStateSource: SystemStateSource

    val actionExecutor: ActionExecutor

    val systemActions: SystemActions

    val appActionsRepository: AppActionsRepository

    val widgetHost: WidgetHost

    val widgetCatalog: WidgetCatalog

    val configBackup: ConfigBackup

    val foregroundSession: ForegroundSession

    val processScope: CoroutineScope

    val aiChatInventory: CachedInventory

    @Provides
    @SingleIn(AppScope::class)
    fun provideProcessScope(): CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, failure ->
            logUnexpected(PROCESS_SCOPE_TAG, "A background job failed", failure)
        },
    )

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(application: Application): CueDatabase =
        Room.databaseBuilder(application, CueDatabase::class.java, CueDatabase.NAME).build()

    @Provides
    fun provideLauncherLayoutDao(database: CueDatabase): LauncherLayoutDao =
        database.launcherLayoutDao()

    @Provides
    fun provideFolderMemberDao(database: CueDatabase): FolderMemberDao = database.folderMemberDao()

    @Provides
    fun provideAppLaunchDao(database: CueDatabase): AppLaunchDao = database.appLaunchDao()

    @Provides
    fun provideHostedWidgetDao(database: CueDatabase): HostedWidgetDao = database.hostedWidgetDao()

    @Provides
    fun providePanelWidgetDao(database: CueDatabase): PanelWidgetDao = database.panelWidgetDao()

    @Provides
    fun provideScenarioDao(database: CueDatabase): ScenarioDao = database.scenarioDao()

    @Provides
    fun provideActivationDao(database: CueDatabase): ActivationDao = database.activationDao()

    @Provides
    fun provideHaEventLogDao(database: CueDatabase): HaEventLogDao = database.haEventLogDao()

    @Provides
    fun provideIconOverrideDao(database: CueDatabase): IconOverrideDao = database.iconOverrideDao()

    @Provides
    fun provideHiddenAppDao(database: CueDatabase): HiddenAppDao = database.hiddenAppDao()

    @Binds
    val HaTokenClient.bindHaSessionTokens: HaSessionTokens

    @Binds
    val ScenarioRepository.bindScenarioSource: ScenarioSource

    @Binds
    val ActivationRepository.bindActivationStore: ActivationStore

    @Binds
    val SettingsRepository.bindAlarmMemory: AlarmMemory

    @Binds
    val SystemConditions.bindSystemStateSource: SystemStateSource

    @Binds
    @IntoSet
    val TimeConditions.bindTimeStateProvider: StateProvider

    @Binds
    @IntoSet
    val PowerConditions.bindPowerStateProvider: StateProvider

    @Binds
    @IntoSet
    val AudioConditions.bindAudioStateProvider: StateProvider

    @Binds
    @IntoSet
    val BluetoothConditions.bindBluetoothStateProvider: StateProvider

    @Binds
    @IntoSet
    val WifiConditions.bindWifiStateProvider: StateProvider

    @Binds
    @IntoSet
    val RingerConditions.bindRingerStateProvider: StateProvider

    @Binds
    @IntoSet
    val CalendarConditions.bindCalendarStateProvider: StateProvider

    @Binds
    @IntoSet
    val MediaConditions.bindMediaStateProvider: StateProvider

    @Binds
    @IntoSet
    val HaConditions.bindHaStateProvider: StateProvider

    @Binds
    val HaEventLogRepository.bindHaEventLog: HaEventLog

    @Binds
    val HaStateReader.bindHaStateSource: HaStateSource

    @Provides
    @SingleIn(AppScope::class)
    fun provideCachedInventory(device: DeviceInventory): CachedInventory = CachedInventory(device)

    @Provides
    fun provideWallClock(): WallClock = WallClock.SYSTEM

    @Provides
    fun provideLocalClock(): LocalClock = LocalClock.SYSTEM

    @Provides
    @SingleIn(AppScope::class)
    fun provideSettingsDataStore(application: Application): DataStore<LauncherSettings> =
        DataStoreFactory.create(
            serializer = LauncherSettingsSerializer,
            // Without it an unreadable file fails every read, and the home screen with it, for good.
            corruptionHandler = ReplaceFileCorruptionHandler { LauncherSettingsSerializer.defaultValue },
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { application.dataStoreFile(SETTINGS_FILE) },
        )

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}

private const val SETTINGS_FILE = "settings.json"

private const val PROCESS_SCOPE_TAG = "ProcessScope"
