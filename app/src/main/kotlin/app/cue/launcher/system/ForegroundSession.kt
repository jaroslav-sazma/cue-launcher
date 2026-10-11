package app.cue.launcher.system

import android.app.Application
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.engine.AlarmMonitor
import app.cue.launcher.engine.ConditionMonitor
import app.cue.launcher.engine.ScenarioEngine
import app.cue.launcher.engine.ScenarioSource
import app.cue.launcher.engine.needsNotificationPosts
import app.cue.launcher.system.iconpack.IconPackRepository
import app.cue.launcher.system.notifications.NotificationListenerLink
import app.cue.launcher.system.widget.WidgetHostMaintenance
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

// The one owner of the work the launcher does on its own. A process woken by a receiver or by the
// system starts none of it: the state the home screen needs is prepared when it is first created,
// and what follows the system — callbacks, timers, polling — lives in run(), which exists only
// while the home screen is started (the widget host is switched by the activity itself). What
// happened in between is caught up when run() begins.
@Suppress("LongParameterList")
@SingleIn(AppScope::class)
@Inject
class ForegroundSession(
    private val application: Application,
    private val processScope: CoroutineScope,
    private val layoutMaintenance: LayoutMaintenance,
    private val contentSeed: ContentSeed,
    private val widgetHostMaintenance: WidgetHostMaintenance,
    private val iconPacks: IconPackRepository,
    private val iconAppearance: IconAppearance,
    private val bluetoothReceiverGate: BluetoothReceiverGate,
    private val alarmReceiverGate: AlarmReceiverGate,
    private val alarmMonitor: AlarmMonitor,
    private val engine: ScenarioEngine,
    private val conditionMonitor: ConditionMonitor,
    private val notificationListener: NotificationListenerLink,
    private val scenarios: ScenarioSource,
    private val settings: SettingsRepository,
) {

    private val prepared = AtomicBoolean(false)

    // These hold state for the home screen and then wait on flows only the launcher's own screens
    // write to, so once started they cost nothing for the rest of the process.
    fun prepare() {
        if (!prepared.compareAndSet(false, true)) return
        iconPacks.start(processScope)
        iconAppearance.start(processScope)
        bluetoothReceiverGate.start(processScope)
        alarmReceiverGate.start(processScope)
        contentSeed.start(processScope)
        widgetHostMaintenance.start(processScope)
        processScope.launch { layoutMaintenance.seedDock() }
        processScope.launch { followNotificationNeed() }
    }

    suspend fun run(): Unit = withContext(Dispatchers.Default) {
        // Catches a change the receiver never saw, e.g. the app was updated over the alarm.
        isolated("Alarm catch-up") { alarmMonitor.onNextAlarm { nextAlarmMillis(application) } }
        isolated("Scenario engine") { engine.runForeground() }
        isolated("Condition monitor") { conditionMonitor.runForeground() }
        isolated("Layout maintenance") { layoutMaintenance.runForeground() }
        isolated("Notification listener hold") { notificationListener.holdSession() }
    }

    // A failure would otherwise cancel its siblings and reach the activity's lifecycleScope, taking
    // the home screen down on every start. The failed part stays off until the next start retries
    // it; cancellation on stop still passes through untouched.
    @Suppress("TooGenericExceptionCaught")
    private fun CoroutineScope.isolated(part: String, block: suspend () -> Unit) = launch {
        try {
            block()
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            logUnexpected(TAG, "$part stopped", cause)
        }
    }

    // The binding this decides on outlives every session, as the posts must be heard while the
    // launcher is away.
    private suspend fun followNotificationNeed() {
        notificationListener.followNeed(
            combine(
                scenarios.observeScenarios().map(::needsNotificationPosts),
                settings.settings.map { it.appearance.notificationBadges },
            ) { scenariosNeedPosts, badgesOn -> scenariosNeedPosts || badgesOn },
        )
    }

    private companion object {
        const val TAG = "ForegroundSession"
    }
}
