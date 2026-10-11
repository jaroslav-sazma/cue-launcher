package app.cue.launcher.feature.home

import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.designsystem.glyph.mdiGlyphName

val TileContent.Template.wholeTileActions: List<TapAction>
    get() = if (layout == TemplateLayout.TEXT_ONLY) items.firstOrNull()?.actions.orEmpty() else emptyList()

fun Scenario.templateItem(): TemplateItem? =
    (content as? TileContent.Template)?.items?.firstOrNull()

fun Scenario.tileLabel(): String = templateItem()?.label ?: name

fun templateGlyphName(item: TemplateItem): String? =
    mdiGlyphName(item.icon) ?: item.primaryAction?.let(::derivedGlyphName)

fun templateIconPackage(item: TemplateItem): String? =
    if (mdiGlyphName(item.icon) != null) null else item.primaryAction?.iconPackage

val SYSTEM_ACTION_GLYPHS: Map<SystemActionType, String> = mapOf(
    SystemActionType.RINGER_SILENT to "volume-off",
    SystemActionType.RINGER_VIBRATE to "vibrate",
    SystemActionType.RINGER_NORMAL to "volume-high",
    SystemActionType.DND_ON to "minus-circle",
    SystemActionType.DND_OFF to "minus-circle-off",
    SystemActionType.FLASHLIGHT to "flashlight",
    SystemActionType.TV_REMOTE to "remote-tv",
    SystemActionType.PANEL_VOLUME to "volume-medium",
    SystemActionType.PANEL_INTERNET to "signal",
    SystemActionType.PANEL_WIFI to "wifi",
    SystemActionType.PANEL_NFC to "nfc",
    SystemActionType.OPEN_SETTINGS to "cog",
    SystemActionType.REMEMBER_PLACE to "map-marker-plus",
    SystemActionType.SHOW_PLACE to "map-marker",
)

const val HA_GLYPH = "home-assistant"

private fun derivedGlyphName(action: TapAction): String? = when (action) {
    is TapAction.SystemAction -> SYSTEM_ACTION_GLYPHS[action.action]
    is TapAction.HaAction -> HA_GLYPH
    is TapAction.App,
    is TapAction.Shortcut,
    is TapAction.ActivityLaunch,
    is TapAction.Capability,
    is TapAction.DeepLink,
    -> null
}

private val TapAction.iconPackage: String?
    get() = when (this) {
        is TapAction.App -> packageName
        is TapAction.Shortcut -> packageName
        is TapAction.ActivityLaunch -> packageName
        is TapAction.Capability -> packageName
        is TapAction.DeepLink,
        is TapAction.SystemAction,
        is TapAction.HaAction,
        -> null
    }
