package app.cue.launcher.data.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import app.cue.launcher.data.aichat.AiChatShare
import app.cue.launcher.data.bridge.BridgeSettings
import app.cue.launcher.data.ha.HaSettings
import app.cue.launcher.data.scenario.AccentSource
import app.cue.launcher.data.scenario.GesturesConfig
import app.cue.launcher.data.scenario.IconMode
import app.cue.launcher.data.scenario.effectiveAccentOf
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class LauncherSettings(

    val dockSeeded: Boolean = false,

    val onboardingSeen: Boolean = false,

    val stackHintSeen: Boolean = false,

    val clockSeeded: Boolean = false,

    val panelSeeded: Boolean = false,

    val ha: HaSettings = HaSettings(),

    val layoutLocked: Boolean = false,

    val drawer: DrawerSettings = DrawerSettings(),

    val appearance: AppearanceSettings = AppearanceSettings(),

    val gestures: GesturesConfig = GesturesConfig(),

    val calendarWidget: CalendarWidgetSettings = CalendarWidgetSettings(),

    val mediaWidget: MediaWidgetSettings = MediaWidgetSettings(),

    val lastBackup: BackupSummary? = null,

    val upcomingAlarm: Long? = null,

    val aiChatScenarios: Boolean = false,

    val aiChatShare: AiChatShare = AiChatShare(),

    // A brief went to the AI chat and its answer has not been opened in the builder yet. Only a send
    // sets it; the switch going off clears it with the rest.
    val aiChatPending: Boolean = false,

    val bridge: BridgeSettings = BridgeSettings(),
)

@Serializable
data class BackupSummary(
    val name: String,
    val scenarios: Int,
    val bytes: Int,
    val at: Long,
)

@Serializable
data class AppearanceSettings(
    val iconMode: IconMode = IconMode.NATIVE,
    val iconPack: String? = null,
    val reducedTransparency: Boolean = false,
    val notificationBadges: Boolean = false,
    val accent: AccentSource = AccentSource.PRIMARY,
    val customAccent: Int? = null,
)

val AppearanceSettings.selectedPack: String?
    get() = iconPack.takeIf { iconMode == IconMode.ICONPACK }

val AppearanceSettings.effectiveAccent: AccentSource
    get() = effectiveAccentOf(accent, customAccent)

@Serializable
data class CalendarWidgetSettings(

    // null = follow the calendar app, i.e. every calendar visible there.
    val calendars: Set<Long>? = null,
)

@Serializable
data class MediaWidgetSettings(val app: String = DEFAULT_MEDIA_APP)

const val DEFAULT_MEDIA_APP = "com.google.android.apps.youtube.music"

@Serializable
data class DrawerSettings(
    val contacts: Boolean = false,
    val aiRow: Boolean = false,
    val webRow: Boolean = true,
)

object LauncherSettingsSerializer : Serializer<LauncherSettings> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        // An unknown enum value or a stray null falls back to the field's default instead of
        // counting as corruption, which would reset every setting.
        coerceInputValues = true
    }

    override val defaultValue: LauncherSettings = LauncherSettings()

    override suspend fun readFrom(input: InputStream): LauncherSettings =
        try {
            json.decodeFromString<LauncherSettings>(input.readBytes().decodeToString())
        } catch (cause: SerializationException) {
            throw CorruptionException("Could not read the settings", cause)
        }

    override suspend fun writeTo(t: LauncherSettings, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}
