package app.cue.launcher.data.scenario

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

private const val FOUR_ICON_ITEMS = 4

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface TileContent {

    @Serializable
    @SerialName("template")
    data class Template(
        val layout: TemplateLayout,
        val text: String? = null,
        val text2: String? = null,
        val items: List<TemplateItem> = emptyList(),
    ) : TileContent

    @Serializable
    @SerialName("launcher_widget")
    data class LauncherWidget(val widget: LauncherWidgetType) : TileContent

    @Serializable
    @SerialName("app_widget")
    data class AppWidget(
        @SerialName("package") val packageName: String,
        val provider: String,
        val appWidgetId: String,
    ) : TileContent {

        val boundAppWidgetId: Int? get() = appWidgetId.toIntOrNull()
    }
}

@Serializable
enum class TemplateLayout(val jsonName: String, val size: TileSize, val itemCount: Int) {
    @SerialName("icon_text")
    ICON_TEXT("icon_text", TileSize.GRID_1X1, itemCount = 1),

    @SerialName("text_only")
    TEXT_ONLY("text_only", TileSize.GRID_2X2, itemCount = 1),

    @SerialName("text_two_icons")
    TEXT_TWO_ICONS("text_two_icons", TileSize.GRID_2X2, itemCount = 2),

    @SerialName("four_icons")
    FOUR_ICONS("four_icons", TileSize.GRID_2X2, itemCount = FOUR_ICON_ITEMS),
    ;

    companion object {
        fun fromJsonName(jsonName: String): TemplateLayout? = entries.firstOrNull { it.jsonName == jsonName }
    }
}

@Serializable
enum class LauncherWidgetType(
    val jsonName: String,
    val size: TileSize?,
    val staticColumns: Int,
    val staticRows: Int,
) {
    @SerialName("calendar")
    CALENDAR("calendar", TileSize.HERO_4X2, staticColumns = 4, staticRows = 2),

    @SerialName("searchbar")
    SEARCHBAR("searchbar", size = null, staticColumns = 2, staticRows = 1),

    @SerialName("clock")
    CLOCK("clock", TileSize.HERO_4X2, staticColumns = 4, staticRows = 2),

    @SerialName("media")
    MEDIA("media", TileSize.HERO_4X2, staticColumns = 4, staticRows = 2),
    ;

    companion object {
        fun fromJsonName(jsonName: String): LauncherWidgetType? = entries.firstOrNull { it.jsonName == jsonName }
    }
}

@Serializable
data class TemplateItem(
    val label: String? = null,
    val icon: String? = null,
    @SerialName("action")
    @Serializable(with = TapActionsSerializer::class)
    val actions: List<TapAction>,
) {

    val primaryAction: TapAction? get() = actions.firstOrNull()
}

internal object TapActionsSerializer :
    JsonTransformingSerializer<List<TapAction>>(ListSerializer(TapAction.serializer())) {

    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonArray) element else JsonArray(listOf(element))

    override fun transformSerialize(element: JsonElement): JsonElement =
        if (element is JsonArray && element.size == 1) element.single() else element
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface TapAction {

    // profile was added after schemaVersion 1 froze; null is the launcher's own profile and is never
    // written, so an action of the personal profile exports exactly as before.
    @Serializable
    @SerialName("app")
    data class App(
        @SerialName("package") val packageName: String,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val profile: AppProfile? = null,
    ) : TapAction

    @Serializable
    @SerialName("shortcut")
    data class Shortcut(
        @SerialName("package") val packageName: String,
        val shortcutId: String,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val profile: AppProfile? = null,
    ) : TapAction

    @Serializable
    @SerialName("deeplink")
    data class DeepLink(val uri: String) : TapAction

    @Serializable
    @SerialName("capability")
    data class Capability(
        @SerialName("package") val packageName: String,
        val capability: String,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val profile: AppProfile? = null,
    ) : TapAction

    @Serializable
    @SerialName("activity")
    data class ActivityLaunch(
        @SerialName("package") val packageName: String,
        val component: String,
    ) : TapAction

    @Serializable
    @SerialName("system")
    data class SystemAction(val action: SystemActionType) : TapAction

    @Serializable
    @SerialName("ha")
    data class HaAction(
        val service: String,
        val entity: String? = null,
        val data: JsonObject = JsonObject(emptyMap()),

        // Added after schemaVersion 1 froze; optional with defaults, so the version holds.
        val confirm: Boolean = false,

        // When set, the only entity whose state colours the icon; entity is then never read for it.
        // NEVER keeps the null out of every exported HA action that does not use it.
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val stateEntity: String? = null,
    ) : TapAction
}

@Serializable
enum class AppProfile {
    @SerialName("work")
    WORK,
}

// A link or an app screen can only be started in the launcher's own profile, so only App, Shortcut
// and Capability ever name another one.
data class ActionApp(val packageName: String, val profile: AppProfile? = null)

val TapAction.actionApp: ActionApp?
    get() = when (this) {
        is TapAction.App -> ActionApp(packageName, profile)
        is TapAction.Shortcut -> ActionApp(packageName, profile)
        is TapAction.Capability -> ActionApp(packageName, profile)
        is TapAction.ActivityLaunch -> ActionApp(packageName)
        is TapAction.DeepLink,
        is TapAction.SystemAction,
        is TapAction.HaAction,
        -> null
    }

@Serializable
enum class SystemActionType(val jsonName: String) {
    @SerialName("ringer_silent")
    RINGER_SILENT("ringer_silent"),

    @SerialName("ringer_vibrate")
    RINGER_VIBRATE("ringer_vibrate"),

    @SerialName("ringer_normal")
    RINGER_NORMAL("ringer_normal"),

    @SerialName("dnd_on")
    DND_ON("dnd_on"),

    @SerialName("dnd_off")
    DND_OFF("dnd_off"),

    @SerialName("flashlight")
    FLASHLIGHT("flashlight"),

    @SerialName("tv_remote")
    TV_REMOTE("tv_remote"),

    @SerialName("panel_volume")
    PANEL_VOLUME("panel_volume"),

    @SerialName("panel_internet")
    PANEL_INTERNET("panel_internet"),

    @SerialName("panel_wifi")
    PANEL_WIFI("panel_wifi"),

    @SerialName("panel_nfc")
    PANEL_NFC("panel_nfc"),

    @SerialName("open_settings")
    OPEN_SETTINGS("open_settings"),

    @SerialName("remember_place")
    REMEMBER_PLACE("remember_place"),

    @SerialName("show_place")
    SHOW_PLACE("show_place"),

    @SerialName("mac_lock")
    MAC_LOCK("mac_lock"),
    ;

    companion object {
        fun fromJsonName(jsonName: String): SystemActionType? = entries.firstOrNull { it.jsonName == jsonName }
    }
}
