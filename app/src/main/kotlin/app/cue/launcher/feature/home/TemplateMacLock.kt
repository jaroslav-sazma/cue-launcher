package app.cue.launcher.feature.home

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import app.cue.launcher.R
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.engine.onlyLocksMac
import app.cue.launcher.engine.templateItemEnabled

fun TileContent.Template.wholeTileEnabled(macLocked: Boolean?): Boolean =
    wholeTileItem?.let { templateItemEnabled(it, macLocked) } != false

fun TileContent.Template.enabledWholeTileActions(macLocked: Boolean?): List<TapAction> =
    wholeTileActions.takeIf { wholeTileEnabled(macLocked) }.orEmpty()

@StringRes
fun TileContent.Template.wholeTileStateRes(macLocked: Boolean?): Int? =
    wholeTileItem?.let { macLockStateRes(it, macLocked) }

@StringRes
fun macLockStateRes(item: TemplateItem, macLocked: Boolean?): Int? = when {
    !onlyLocksMac(item) -> null
    macLocked == true -> R.string.tile_mac_locked
    macLocked == null -> R.string.tile_mac_unavailable
    else -> null
}

// Unlocked keeps the action's own glyph, the laptop it locks.
internal fun macLockGlyphs(item: TemplateItem, macLocked: Boolean?): List<String>? = when {
    !onlyLocksMac(item) -> null
    macLocked == true -> listOf(MAC_LOCKED_GLYPH)
    macLocked == null -> listOf(MAC_ABSENT_GLYPH)
    else -> emptyList()
}

internal fun Color.disabledUnless(enabled: Boolean): Color =
    if (enabled) this else copy(alpha = alpha * Tokens.Template.DISABLED_CONTENT_OPACITY)

private const val MAC_LOCKED_GLYPH = "lock"

private const val MAC_ABSENT_GLYPH = "laptop-off"
