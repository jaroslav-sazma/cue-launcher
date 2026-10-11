package app.cue.launcher.data.settings

import androidx.datastore.core.DataStore
import app.cue.launcher.data.aichat.AiChatShare
import app.cue.launcher.data.calendar.toggledCalendarIds
import app.cue.launcher.data.scenario.AccentSource
import app.cue.launcher.data.scenario.GesturesConfig
import app.cue.launcher.data.scenario.IconMode
import app.cue.launcher.data.scenario.LauncherConfig
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.accentRgbOf
import app.cue.launcher.data.scenario.customAccentRgb
import app.cue.launcher.data.scenario.effectiveAccent
import app.cue.launcher.engine.AlarmMemory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Suppress("TooManyFunctions")
@SingleIn(AppScope::class)
@Inject
class SettingsRepository(private val dataStore: DataStore<LauncherSettings>) : AlarmMemory {

    val settings: Flow<LauncherSettings> = dataStore.data

    suspend fun setDockSeeded(seeded: Boolean) {
        dataStore.updateData { it.copy(dockSeeded = seeded) }
    }

    suspend fun setOnboardingSeen(seen: Boolean) {
        dataStore.updateData { it.copy(onboardingSeen = seen) }
    }

    suspend fun setClockSeeded(seeded: Boolean) {
        dataStore.updateData { it.copy(clockSeeded = seeded) }
    }

    suspend fun setPanelSeeded(seeded: Boolean) {
        dataStore.updateData { it.copy(panelSeeded = seeded) }
    }

    suspend fun setStackHintSeen(seen: Boolean) {
        dataStore.updateData { it.copy(stackHintSeen = seen) }
    }

    suspend fun setLayoutLocked(locked: Boolean) {
        dataStore.updateData { it.copy(layoutLocked = locked) }
    }

    suspend fun setDrawerContacts(enabled: Boolean) {
        dataStore.updateData { it.copy(drawer = it.drawer.copy(contacts = enabled)) }
    }

    suspend fun setDrawerAiRow(enabled: Boolean) {
        dataStore.updateData { it.copy(drawer = it.drawer.copy(aiRow = enabled)) }
    }

    suspend fun setDrawerWebRow(enabled: Boolean) {
        dataStore.updateData { it.copy(drawer = it.drawer.copy(webRow = enabled)) }
    }

    suspend fun setSwipeLeftAction(action: TapAction?) {
        dataStore.updateData { it.copy(gestures = it.gestures.copy(swipeLeft = action)) }
    }

    suspend fun setSearchbarTapAction(action: TapAction?) {
        dataStore.updateData { it.copy(gestures = it.gestures.copy(searchbarTap = action)) }
    }

    suspend fun setCalendarTapAction(action: TapAction?) {
        dataStore.updateData { it.copy(gestures = it.gestures.copy(calendarTap = action)) }
    }

    suspend fun setCalendarWidgetCalendars(calendars: Set<Long>?) {
        dataStore.updateData { it.copy(calendarWidget = CalendarWidgetSettings(calendars)) }
    }

    // Read-modify-write inside updateData so two quick taps can't base a toggle on a stale snapshot.
    suspend fun toggleCalendarWidgetCalendar(id: Long, visibleDefault: Set<Long>) {
        dataStore.updateData {
            val base = it.calendarWidget.calendars ?: visibleDefault
            it.copy(calendarWidget = CalendarWidgetSettings(toggledCalendarIds(base, id)))
        }
    }

    suspend fun setMediaWidgetApp(packageName: String) {
        dataStore.updateData { it.copy(mediaWidget = MediaWidgetSettings(packageName)) }
    }

    suspend fun setReducedTransparency(enabled: Boolean) {
        dataStore.updateData { it.copy(appearance = it.appearance.copy(reducedTransparency = enabled)) }
    }

    suspend fun setAccent(source: AccentSource, custom: Int? = null) {
        dataStore.updateData {
            it.copy(
                appearance = it.appearance.copy(
                    accent = source,
                    customAccent = custom?.let(::accentRgbOf) ?: it.appearance.customAccent,
                ),
            )
        }
    }

    suspend fun setAiChatScenarios(enabled: Boolean) {
        dataStore.updateData { it.copy(aiChatScenarios = enabled, aiChatPending = it.aiChatPending && enabled) }
    }

    suspend fun setAiChatShare(share: AiChatShare) {
        dataStore.updateData { it.copy(aiChatShare = share) }
    }

    suspend fun setAiChatPending(pending: Boolean) {
        dataStore.updateData { it.copy(aiChatPending = pending && it.aiChatScenarios) }
    }

    suspend fun setNotificationBadges(enabled: Boolean) {
        dataStore.updateData { it.copy(appearance = it.appearance.copy(notificationBadges = enabled)) }
    }

    suspend fun setLastBackup(summary: BackupSummary) {
        dataStore.updateData { it.copy(lastBackup = summary) }
    }

    suspend fun applyConfig(config: LauncherConfig) {
        dataStore.updateData {
            it.copy(
                dockSeeded = true,
                clockSeeded = true,
                panelSeeded = true,
                appearance = it.appearance.copy(
                    iconMode = config.appearance.iconMode,
                    accent = config.appearance.effectiveAccent,
                    customAccent = config.appearance.customAccentRgb,
                ),
                drawer = DrawerSettings(
                    contacts = config.drawer.contacts,
                    aiRow = config.drawer.aiRow,
                    webRow = config.drawer.webRow,
                ),
                gestures = config.gestures,
                mediaWidget = MediaWidgetSettings(config.widgets.mediaApp ?: DEFAULT_MEDIA_APP),
            )
        }
    }

    suspend fun setIconMode(mode: IconMode, packageName: String? = null) {
        dataStore.updateData {
            it.copy(
                appearance = it.appearance.copy(
                    iconMode = mode,
                    iconPack = packageName ?: it.appearance.iconPack,
                ),
            )
        }
    }

    override suspend fun rememberedAlarm(): Long? = dataStore.data.first().upcomingAlarm

    override suspend fun rememberAlarm(upcoming: Long?) {
        dataStore.updateData { it.copy(upcomingAlarm = upcoming) }
    }
}
