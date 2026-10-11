package app.cue.launcher.data.scenario

import app.cue.launcher.data.widgets.PANEL_WIDGET_ROWS_DEFAULT
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
data class LauncherConfig(
    val staticZone: StaticZoneConfig = StaticZoneConfig(),
    val appearance: AppearanceConfig = AppearanceConfig(),
    val drawer: DrawerConfig = DrawerConfig(),
    val gestures: GesturesConfig = GesturesConfig(),
    val widgets: WidgetsConfig = WidgetsConfig(),
)

@Serializable
data class WidgetsConfig(
    val mediaApp: String? = null,
)

@Serializable
data class StaticZoneConfig(
    @Serializable(with = StaticZoneEntries::class) val grid: List<StaticZoneEntry> = emptyList(),
    @Serializable(with = StaticZoneEntries::class) val dock: List<StaticZoneEntry> = emptyList(),
    val widgetPanel: WidgetPanelConfig = WidgetPanelConfig(),
)

@Serializable
data class WidgetPanelConfig(
    @Serializable(with = PanelEntries::class) val widgets: List<PanelEntry> = emptyList(),
    @Serializable(with = StaticZoneEntries::class) val dock: List<StaticZoneEntry> = emptyList(),
)

@Serializable
data class ComponentEntry(
    @SerialName("package") val packageName: String,
    val component: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface StaticZoneEntry {

    val position: Int

    @Serializable
    @SerialName("shortcut")
    data class Shortcut(
        override val position: Int,
        @SerialName("package") val packageName: String,

        val component: String,
    ) : StaticZoneEntry

    @Serializable
    @SerialName("folder")
    data class Folder(
        override val position: Int,
        val name: String,
        val items: List<ComponentEntry> = emptyList(),
    ) : StaticZoneEntry

    @Serializable
    @SerialName("launcher_widget")
    data class LauncherWidget(
        override val position: Int,
        val widget: LauncherWidgetType,
    ) : StaticZoneEntry

    @Serializable
    @SerialName("app_widget")
    data class AppWidget(
        override val position: Int,
        @SerialName("package") val packageName: String,
        val provider: String,
        val appWidgetId: String,
        val columnSpan: Int = 1,
        val rowSpan: Int = 1,
    ) : StaticZoneEntry
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface PanelEntry {

    val position: Int

    @Serializable
    @SerialName("app_widget")
    data class AppWidget(
        override val position: Int,
        @SerialName("package") val packageName: String,
        val provider: String,
        val appWidgetId: String,
        val rowSpan: Int = PANEL_WIDGET_ROWS_DEFAULT,
    ) : PanelEntry

    @Serializable
    @SerialName("launcher_widget")
    data class LauncherWidget(
        override val position: Int,
        val widget: LauncherWidgetType,
    ) : PanelEntry
}

private fun JsonElement.dropsUnknownKinds(known: Set<String>): JsonElement {
    val array = this as? JsonArray ?: return this
    return JsonArray(
        array.filter { element ->
            val kind = (element as? JsonObject)?.get(FIELD_KIND) as? JsonPrimitive
            kind?.takeIf { it.isString }?.content in known
        },
    )
}

private const val FIELD_KIND = "kind"

// A sealed serializer's descriptor carries "type" at 0 and the subclass catalog at 1; deriving the
// known kinds from it means a newly added entry subtype can never be silently dropped on import.
private fun SerialDescriptor.subclassKinds(): Set<String> = getElementDescriptor(1).elementNames.toSet()

object StaticZoneEntries : JsonTransformingSerializer<List<StaticZoneEntry>>(
    ListSerializer(StaticZoneEntry.serializer()),
) {
    private val KNOWN = StaticZoneEntry.serializer().descriptor.subclassKinds()

    override fun transformDeserialize(element: JsonElement): JsonElement = element.dropsUnknownKinds(KNOWN)
}

object PanelEntries : JsonTransformingSerializer<List<PanelEntry>>(ListSerializer(PanelEntry.serializer())) {

    private val KNOWN = PanelEntry.serializer().descriptor.subclassKinds()

    override fun transformDeserialize(element: JsonElement): JsonElement = element.dropsUnknownKinds(KNOWN)
}

@Serializable
enum class IconMode {
    @SerialName("native")
    NATIVE,

    @SerialName("iconpack")
    ICONPACK,

    @SerialName("tinted")
    TINTED,
}

@Serializable
data class AppearanceConfig(
    val iconMode: IconMode = IconMode.NATIVE,
    val iconOverrides: List<ComponentEntry> = emptyList(),
    @Serializable(with = AccentSources::class) val accent: AccentSource = AccentSource.PRIMARY,
    @Serializable(with = AccentHexes::class) val customAccent: String? = null,
)

// A colour that does not parse counts as missing.
val AppearanceConfig.customAccentRgb: Int?
    get() = customAccent?.let(::parseAccentHex)

val AppearanceConfig.effectiveAccent: AccentSource
    get() = effectiveAccentOf(accent, customAccentRgb)

@Serializable
data class DrawerConfig(
    val contacts: Boolean = false,
    val aiRow: Boolean = false,
    val webRow: Boolean = true,

    val hiddenApps: List<ComponentEntry> = emptyList(),
)

@Serializable
data class GesturesConfig(
    val swipeLeft: TapAction? = null,
    val searchbarTap: TapAction? = null,
    val calendarTap: TapAction? = null,
)

data class ConfigAppWidget(val key: String, val packageName: String, val provider: String)

fun widgetPlaceholderKey(appWidgetId: Int): String = "widget_$appWidgetId"

fun widgetKeyOf(appWidgetId: String): String =
    placeholderKeysIn(appWidgetId).firstOrNull() ?: appWidgetId
