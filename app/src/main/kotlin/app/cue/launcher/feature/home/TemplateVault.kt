package app.cue.launcher.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.designsystem.glyph.LocalMdiGlyphs

// The open session lives in memory, so following it costs nothing while the vault is locked or was
// never set up. Not static, and read only for a button whose one action is the vault's, so a lock or
// an unlock recomposes those buttons and no other tile.
val LocalVaultUnlocked = compositionLocalOf { false }

fun onlyVaultAction(item: TemplateItem): Boolean =
    (item.actions.singleOrNull() as? TapAction.SystemAction)?.action in VAULT_ACTIONS

// A button that only unlocks, locks or toggles the vault shows which of the two it is in now. It is
// never greyed out: unlocked, Unlock opens the files, and Lock on a locked vault costs nothing.
internal fun vaultGlyphs(item: TemplateItem, unlocked: Boolean): List<String>? =
    if (onlyVaultAction(item)) listOf(if (unlocked) VAULT_UNLOCKED_GLYPH else VAULT_LOCKED_GLYPH) else null

// The local is read only behind onlyVaultAction, see LocalVaultUnlocked.
@Composable
@ReadOnlyComposable
internal fun vaultShownUnlocked(item: TemplateItem): Boolean = onlyVaultAction(item) && LocalVaultUnlocked.current

@Composable
@ReadOnlyComposable
internal fun tileStateGlyphs(
    item: TemplateItem,
    haGlyphs: Map<String, List<String>>,
    macLocked: Boolean?,
): List<String> = templateStateGlyphs(item, haGlyphs, LocalMdiGlyphs.current, macLocked, vaultShownUnlocked(item))

private val VAULT_ACTIONS =
    setOf(SystemActionType.VAULT_UNLOCK, SystemActionType.VAULT_LOCK, SystemActionType.VAULT_TOGGLE)

internal const val VAULT_LOCKED_GLYPH = "folder-lock"

internal const val VAULT_UNLOCKED_GLYPH = "folder-lock-open"
