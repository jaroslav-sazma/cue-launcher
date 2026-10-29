package app.cue.launcher

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.cue.launcher.data.scenario.AccentSource
import app.cue.launcher.data.scenario.accentRgbOf
import app.cue.launcher.data.settings.AppearanceSettings
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.data.settings.effectiveAccent
import app.cue.launcher.designsystem.CueTheme
import app.cue.launcher.designsystem.ThemeAccent
import app.cue.launcher.di.AppGraph
import app.cue.launcher.feature.home.CalendarFeed
import app.cue.launcher.feature.home.CalendarSources
import app.cue.launcher.feature.home.LocalCalendarFeed
import app.cue.launcher.feature.home.LocalCalendarSources
import app.cue.launcher.feature.home.LocalMediaFeed
import app.cue.launcher.feature.home.MediaFeed
import app.cue.launcher.feature.home.MediaWidgetState
import app.cue.launcher.feature.icons.IconAppearanceHost
import app.cue.launcher.feature.icons.LocalNotificationCounts
import app.cue.launcher.feature.icons.NotificationCounts
import app.cue.launcher.feature.scenarios.AppLabels
import app.cue.launcher.feature.scenarios.LocalAppLabels
import app.cue.launcher.feature.scenarios.builder.activeNotificationsOf
import app.cue.launcher.feature.scenarios.builder.LocalActiveNotifications
import app.cue.launcher.feature.settings.bridge.BridgeServices
import app.cue.launcher.feature.settings.bridge.LocalBridgeServices
import app.cue.launcher.feature.settings.ha.HaServices
import app.cue.launcher.feature.settings.ha.HaSetup
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.feature.scenarios.aichat.LocalAiChatInventory
import app.cue.launcher.system.ActionExecutor
import app.cue.launcher.system.ActionOutcome
import app.cue.launcher.system.CalendarRepository
import app.cue.launcher.system.HomeRole
import app.cue.launcher.system.HomeRoleStatus
import app.cue.launcher.system.InstalledApp
import app.cue.launcher.system.ha.HA_SIGN_IN_RETURN
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.WallpaperLuminance
import app.cue.launcher.system.launchSystemScreen
import app.cue.launcher.system.media.MediaPlayers
import app.cue.launcher.system.media.playerChoices
import app.cue.launcher.system.widget.WidgetBinder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val homeRoleStatus = mutableStateOf(HomeRoleStatus.UNAVAILABLE)

    private val homeResetSignal = mutableIntStateOf(0)

    private val importUri = mutableStateOf<Uri?>(null)

    private lateinit var graph: AppGraph

    private lateinit var widgetBinder: WidgetBinder

    private lateinit var widgets: WidgetHosting

    private lateinit var ha: HaServices

    private val bridgeServices: () -> BridgeServices = { graph.bridgeServices }

    private val homeRoleRequest = registerForActivityResult(StartActivityForResult()) {

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        graph = (application as CueApplication).graph
        graph.foregroundSession.prepare()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { graph.foregroundSession.run() }
        }
        ha = haServicesOf(graph)
        bindWidgetHosting(graph)
        homeRoleStatus.value = HomeRole.status(this)
        importUri.value = scenarioDocumentOf(intent)
        setContent { LauncherContent() }
    }

    @Composable
    private fun LauncherContent() {
        val launcherApps = graph.launcherAppsRepository
        val settingsRepository = graph.settingsRepository
        val stored by remember { settingsRepository.settings }
            .collectAsStateWithLifecycle(initialValue = LauncherSettings())
        LauncherTheme(graph.wallpaperLuminance, stored.appearance) {

            IconAppearanceHost(launcherApps) {

                val notificationCounts = graph.notificationListener.badges.collectAsStateWithLifecycle()
                val badgesOn = stored.appearance.notificationBadges
                val counts = remember(notificationCounts, badgesOn) {
                    NotificationCounts(notificationCounts).takeIf { badgesOn }
                }
                CompositionLocalProvider(
                    LocalNotificationCounts provides counts,
                    LocalCalendarFeed provides rememberCalendarFeed(graph.calendarRepository, settingsRepository),
                    LocalCalendarSources provides remember { CalendarSources { graph.calendarRepository.calendars() } },
                    LocalAppLabels provides remember { AppLabels(launcherApps::labelOf) },
                    LocalActiveNotifications provides remember { activeNotificationsOf(graph.notificationListener) },
                    LocalAiChatInventory provides graph.aiChatInventory,
                    LocalBridgeServices provides bridgeServices,
                    LocalMediaFeed provides rememberMediaFeed(
                        players = graph.mediaPlayers,
                        settings = settingsRepository,
                        launcherApps = launcherApps,
                        executor = graph.actionExecutor,
                    ),
                ) {
                    CueApp(
                        roleStatus = homeRoleStatus.value,
                        launcherApps = launcherApps,
                        appActions = graph.appActionsRepository,
                        layoutRepository = graph.launcherLayoutRepository,
                        settingsRepository = settingsRepository,
                        launchLog = graph.launchLogRepository,
                        contacts = graph.contactsRepository,
                        hiddenApps = graph.hiddenAppRepository,
                        scenarios = graph.scenarioRepository,
                        activations = graph.activationRepository,
                        systemState = graph.systemStateSource,
                        engine = graph.scenarioEngine,
                        actionExecutor = graph.actionExecutor,
                        widgets = widgets,
                        iconPacks = graph.iconPackRepository,
                        backup = graph.configBackup,
                        ha = ha,
                        onRequestHomeRole = ::requestHomeRole,
                        homeResetSignal = homeResetSignal.intValue,
                        importUri = importUri.value,
                        onImportConsumed = ::consumeImportDocument,
                    )
                }
            }
        }
    }

    private fun bindWidgetHosting(graph: AppGraph) {
        widgetBinder = WidgetBinder(
            activity = this,
            host = graph.widgetHost,
            widgets = graph.hostedWidgetRepository,
            scope = graph.processScope,
        )
        widgets = WidgetHosting(
            catalog = graph.widgetCatalog,
            binder = widgetBinder,
            host = graph.widgetHost,
            panel = graph.panelColumnRepository,
        )
    }

    @Deprecated("IntentSender results never arrive anywhere but onActivityResult")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (widgetBinder.onConfigureResult(requestCode, granted = resultCode == RESULT_OK)) return
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val document = scenarioDocumentOf(intent)
        if (document != null) {

            importUri.value = document
            return
        }
        // Coming back from the sign-in tab is not the home button: resetting would close the settings
        // the sign-in was started from, and the result would land on a screen nobody can see.
        if (intent.action == HA_SIGN_IN_RETURN) return
        homeResetSignal.intValue++
    }

    private fun consumeImportDocument() {
        importUri.value = null
        setIntent(Intent(Intent.ACTION_MAIN))
    }

    override fun onStart() {
        super.onStart()
        graph.widgetHost.startListening()
    }

    override fun onStop() {
        graph.widgetHost.stopListening()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()

        homeRoleStatus.value = HomeRole.status(this)
    }

    private fun requestHomeRole() {
        homeRoleRequest.launchSystemScreen(HomeRole.requestIntent(this), this)
    }
}

// The widget shows only the calendars picked in its own settings, so the feed follows that choice.
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun rememberCalendarFeed(
    calendar: CalendarRepository,
    settings: SettingsRepository,
): CalendarFeed = remember(calendar, settings) {
    CalendarFeed {
        settings.settings
            .map { it.calendarWidget.calendars }
            .distinctUntilChanged()
            .flatMapLatest { calendars -> calendar.observeEvents(calendarIds = calendars, replayLastShown = true) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun rememberMediaFeed(
    players: MediaPlayers,
    settings: SettingsRepository,
    launcherApps: LauncherAppsRepository,
    executor: ActionExecutor,
): MediaFeed = remember(players, settings, launcherApps, executor) {
    val chosen = settings.settings.map { it.mediaWidget.app }.distinctUntilChanged()
    object : MediaFeed {
        override fun state(): Flow<MediaWidgetState> = chosen.flatMapLatest { packageName ->
            flow {
                val label = launcherApps.labelOf(packageName)
                emitAll(players.observe(packageName).map { read -> MediaWidgetState(label, read) })
            }
        }

        override suspend fun open(sourceBounds: Rect?): ActionOutcome =
            executor.openMedia(chosen.first(), sourceBounds)

        override suspend fun players(include: Set<String>): List<InstalledApp> =
            playerChoices(launcherApps.installedApps(), players.playerPackages() + chosen.first() + include)
    }
}

@Composable
private fun LauncherTheme(
    wallpaper: WallpaperLuminance,
    appearance: AppearanceSettings,
    content: @Composable () -> Unit,
) {
    val initial = remember { wallpaper.current() }
    val luminance by remember { wallpaper.changes() }.collectAsStateWithLifecycle(initialValue = initial)
    CueTheme(
        wallpaperLuminance = luminance,
        reducedTransparency = appearance.reducedTransparency,
        accent = themeAccentOf(appearance),
        content = content,
    )
}

private fun haServicesOf(graph: AppGraph): HaServices = HaServices(
    configs = graph.haConfigRepository,
    log = graph.haEventLogRepository,
    gateway = graph.haGateway,
    states = graph.haStateReader,
    setup = HaSetup(
        tester = graph.haConnectionTester,
        selector = graph.haEndpointSelector,
        discovery = graph.haDiscovery,
        authFlow = graph.haAuthFlow,
        blueprint = graph.haBlueprintUploader,
    ),
)

// The intent comes from any app, and an explicit one bypasses the manifest filters: unparcelling
// EXTRA_STREAM throws on a class this process does not know, and a file: URI would make the launcher
// read with its own rights instead of a grant the sender holds.
private fun scenarioDocumentOf(intent: Intent): Uri? = runCatching {
    when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }
}.getOrNull()?.takeIf { isImportableScheme(it.scheme) }

internal fun isImportableScheme(scheme: String?): Boolean = scheme == ContentResolver.SCHEME_CONTENT

internal fun themeAccentOf(appearance: AppearanceSettings): ThemeAccent = when (appearance.effectiveAccent) {
    AccentSource.PRIMARY -> ThemeAccent.Primary
    AccentSource.TERTIARY -> ThemeAccent.Tertiary
    AccentSource.CUSTOM -> appearance.customAccent?.let { ThemeAccent.Custom(accentRgbOf(it)) } ?: ThemeAccent.Primary
}
