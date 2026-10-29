package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TileContent

fun macLockStateOf(present: Boolean, locked: Boolean?): MacLockState = when {
    !present || locked == null -> MacLockState.UNREPORTED
    locked -> MacLockState.LOCKED
    else -> MacLockState.UNLOCKED
}

fun MacLockState?.reportedLocked(): Boolean? = when (this) {
    MacLockState.LOCKED -> true
    MacLockState.UNLOCKED -> false
    MacLockState.UNREPORTED, null -> null
}

// Only a button that does nothing but lock the Mac follows it. One with more actions stays live and
// runs them all, the lock included: greyed out, it would hold the others back.
fun onlyLocksMac(item: TemplateItem): Boolean =
    (item.actions.singleOrNull() as? TapAction.SystemAction)?.action == SystemActionType.MAC_LOCK

// Only a Mac known to be there and unlocked has anything to lock; locked, away or not reported yet,
// a tap would lock nothing.
fun templateItemEnabled(item: TemplateItem, macLocked: Boolean?): Boolean = !onlyLocksMac(item) || macLocked == false

fun macLockShown(scenarios: List<Scenario>, activations: List<Activation>, now: Long): Boolean =
    visibleScenarios(scenarios, activations.associateBy(Activation::scenarioId), now).any { scenario ->
        (scenario.content as? TileContent.Template)?.items?.any(::onlyLocksMac) == true
    }
