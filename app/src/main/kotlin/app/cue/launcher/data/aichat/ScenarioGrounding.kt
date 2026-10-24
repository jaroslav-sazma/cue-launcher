package app.cue.launcher.data.aichat

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.actionApp
import app.cue.launcher.data.scenario.hasPlaceholder
import java.util.Locale

sealed interface GroundingProblem {

    data class UnknownPackage(val packageName: String) : GroundingProblem

    data class UnknownBluetoothDevice(val mac: String) : GroundingProblem

    data class UnknownHaEntity(val entity: String) : GroundingProblem

    data class UnknownWidget(val packageName: String, val provider: String) : GroundingProblem

    // target is the shortcut id, capability or activity class, as the inventory lists it after the
    // package.
    data class UnknownAppAction(val packageName: String, val target: String) : GroundingProblem

    data class ServiceDomainMismatch(val service: String, val entity: String) : GroundingProblem

    data class MalformedHaService(val service: String) : GroundingProblem

    // suggestions are service ids the instance offers, the likeliest first.
    data class UnknownHaService(val service: String, val suggestions: List<String>) : GroundingProblem

    data class HaServiceMisfit(val service: String, val entity: String, val suggestions: List<String>) :
        GroundingProblem

    data class HaServiceTakesNoEntity(val service: String, val entity: String) : GroundingProblem

    data class UnavailableSystemAction(val action: SystemActionType) : GroundingProblem
}

// Machine-facing English, worded for a chat model to correct its scenario by.
fun GroundingProblem.description(): String = when (this) {
    is GroundingProblem.UnknownPackage ->
        "App package \"$packageName\" is not installed; use a package from the inventory."

    is GroundingProblem.UnknownBluetoothDevice ->
        "Bluetooth device \"$mac\" is not paired; use a MAC from the inventory or omit mac."

    is GroundingProblem.UnknownHaEntity ->
        "Home Assistant entity \"$entity\" does not exist; use an entity id from the inventory."

    is GroundingProblem.UnknownWidget ->
        "Use a widget from the inventory; \"$packageName/$provider\" is not installed."

    is GroundingProblem.UnknownAppAction ->
        "The app offers no action \"$packageName/$target\"; use one from the inventory or kind app."

    is GroundingProblem.ServiceDomainMismatch ->
        "Service \"$service\" does not fit entity \"$entity\"; use a ${entity.substringBefore('.')}.* or " +
            "homeassistant.* service."

    is GroundingProblem.MalformedHaService ->
        "\"$service\" is not a Home Assistant service; write domain.service, like light.toggle."

    is GroundingProblem.UnknownHaService ->
        "Home Assistant has no service \"$service\"" +
            if (suggestions.isEmpty()) "." else "; use ${choices(suggestions)}."

    is GroundingProblem.HaServiceMisfit ->
        "Service \"$service\" cannot act on \"$entity\"; " +
            if (suggestions.isEmpty()) "use another entity." else "use ${choices(suggestions)}."

    is GroundingProblem.HaServiceTakesNoEntity ->
        "Service \"$service\" takes no entity; leave the entity out."

    is GroundingProblem.UnavailableSystemAction ->
        "System action \"${action.jsonName}\" is not available on this phone; leave it out."
}

private fun choices(ids: List<String>): String =
    if (ids.size < 2) ids.joinToString() else ids.dropLast(1).joinToString() + " or " + ids.last()

fun interface ScenarioGrounding {

    suspend fun problems(scenario: Scenario): List<GroundingProblem>
}

// An in-app action is held to what its own app offers, read here for the apps the draft names: the
// snapshot itself carries no app's actions. The services are asked for whenever the draft calls one,
// but not of an instance whose entities did not answer. The whole phone is read, whatever the user
// kept out of the brief: the check stays on the phone.
class InventoryGrounding(private val inventory: InventorySource) : ScenarioGrounding {

    override suspend fun problems(scenario: Scenario): List<GroundingProblem> {
        val snapshot = inventory.snapshot(AiChatShare.ALL)
        val installed = snapshot.apps.mapTo(HashSet(), InventoryApp::packageName)
        val actions = scenario.actions().mapNotNull(TapAction::inAppPackage).filter(installed::contains).distinct()
            .mapNotNull { packageName -> inventory.appActions(packageName)?.let { packageName to it } }
        val callsService = scenario.actions().any {
            it is TapAction.HaAction && !hasPlaceholder(it.service) && HA_SERVICE.matches(it.service)
        }
        val services = if (callsService && snapshot.haEntities != null) inventory.haServices() else null
        return groundingProblems(scenario, snapshot.copy(appActions = actions.toMap(), haServices = services))
    }
}

// A package is checked against the launchable apps, which is what every action here opens; the apps
// a notification or media condition watches are held to the same list, so one without a launcher
// entry is refused. Lists the inventory could not read are not held against the candidate, and
// neither is a watched app, an entity, an in-app action or a widget left as a placeholder for the user
// to fill in. A widget is held to the widget providers alone: a provider's app need not have a
// launcher entry.
fun groundingProblems(scenario: Scenario, inventory: InventorySnapshot): List<GroundingProblem> {
    val packages = inventory.apps.mapTo(HashSet(), InventoryApp::packageName)
    val macs = inventory.devices?.mapTo(HashSet()) { it.mac.uppercase(Locale.ROOT) }
    val entities = inventory.haEntities?.mapTo(HashSet()) { it.id }
    val widgets = inventory.widgets?.mapTo(HashSet()) { it.packageName to it.provider }

    val actions = scenario.actions()
    val widget = (scenario.content as? TileContent.AppWidget)
        ?.takeUnless { hasPlaceholder(it.packageName) || hasPlaceholder(it.provider) }

    return buildList {
        actions.mapNotNull(TapAction::targetPackage)
            .filterNot(packages::contains)
            .forEach { add(GroundingProblem.UnknownPackage(it)) }
        scenario.conditions.flatMap(Condition::watchedPackages)
            .filterNot { it in packages || hasPlaceholder(it) }
            .forEach { add(GroundingProblem.UnknownPackage(it)) }
        if (macs != null) {
            scenario.conditions.flatMap(Condition::requestedMacs)
                .filterNot { it.uppercase(Locale.ROOT) in macs }
                .forEach { add(GroundingProblem.UnknownBluetoothDevice(it)) }
        }
        if (entities != null) {
            val watched = scenario.conditions.filterIsInstance<Condition.HaState>().map { it.entity }
            val acted = actions.filterIsInstance<TapAction.HaAction>()
                .flatMap { listOfNotNull(it.entity, it.stateEntity) }
            (watched + acted)
                .filterNot { it in entities || hasPlaceholder(it) }
                .forEach { add(GroundingProblem.UnknownHaEntity(it)) }
        }
        if (widgets != null && widget != null && (widget.packageName to widget.provider) !in widgets) {
            add(GroundingProblem.UnknownWidget(widget.packageName, widget.provider))
        }
        actions.filterIsInstance<TapAction.SystemAction>()
            .filter { it.action in inventory.unavailableSystemActions }
            .forEach { add(GroundingProblem.UnavailableSystemAction(it.action)) }
        addAll(unknownAppActions(actions, inventory))
        addAll(haServiceProblems(actions, inventory))
    }.distinct()
}

// What an app offers is read in the personal profile only, so a work action has nothing to be
// checked against and is left to fail on its own tap if it is wrong.
private fun unknownAppActions(actions: List<TapAction>, inventory: InventorySnapshot): List<GroundingProblem> =
    actions.mapNotNull { action ->
        if (action.actionApp?.profile != null) return@mapNotNull null
        val packageName = action.inAppPackage() ?: return@mapNotNull null
        val offered = inventory.appActions[packageName] ?: return@mapNotNull null
        val target = action.inAppTarget()
        val placeholder = hasPlaceholder(packageName) || hasPlaceholder(target)
        GroundingProblem.UnknownAppAction(packageName, target)
            .takeUnless { placeholder || offered.any { it.action == action } }
    }

private fun Scenario.actions(): List<TapAction> =
    (content as? TileContent.Template)?.items.orEmpty().flatMap { it.actions }

private fun TapAction.inAppPackage(): String? = when (this) {
    is TapAction.Shortcut -> packageName
    is TapAction.Capability -> packageName
    is TapAction.ActivityLaunch -> packageName
    else -> null
}

private fun TapAction.inAppTarget(): String = when (this) {
    is TapAction.Shortcut -> shortcutId
    is TapAction.Capability -> capability
    is TapAction.ActivityLaunch -> component
    else -> ""
}

private fun Condition.requestedMacs(): List<String> {
    val own = when (this) {
        is Condition.BtConnected -> mac
        is Condition.BtDisconnected -> mac
        else -> null
    }
    val until = (this as? Condition.InstantCondition)?.until as? EndEvent.BtDisconnected
    return listOfNotNull(own, until?.mac)
}

private fun Condition.watchedPackages(): List<String> {
    val own = when (this) {
        is Condition.NotificationFrom -> listOf(packageName)
        is Condition.NotificationEnded -> listOf(packageName)
        is Condition.MediaPlaying -> packages
        else -> emptyList()
    }
    val until = (this as? Condition.InstantCondition)?.until as? EndEvent.Notification
    return own + listOfNotNull(until?.packageName)
}

private fun TapAction.targetPackage(): String? = when (this) {
    is TapAction.App -> packageName
    is TapAction.Shortcut -> packageName
    is TapAction.Capability -> packageName
    is TapAction.ActivityLaunch -> packageName
    is TapAction.DeepLink, is TapAction.SystemAction, is TapAction.HaAction -> null
}
