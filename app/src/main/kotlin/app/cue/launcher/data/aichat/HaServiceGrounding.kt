package app.cue.launcher.data.aichat

import app.cue.launcher.data.ha.HaEntity
import app.cue.launcher.data.ha.HaService
import app.cue.launcher.data.ha.fits
import app.cue.launcher.data.ha.fitsDomain
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.hasPlaceholder

// A chat sometimes puts a system action or a widget provider into the service field; the
// entity is optional in the contract, so the service's own shape is the only thing always there
// to check. What the instance offers decides the rest; without its list only the domains can.
internal fun haServiceProblems(actions: List<TapAction>, inventory: InventorySnapshot): List<GroundingProblem> {
    val services = inventory.haServices?.takeIf(List<HaService>::isNotEmpty)
    val entities = inventory.haEntities?.associateBy(HaEntity::id).orEmpty()
    return actions.filterIsInstance<TapAction.HaAction>().mapNotNull { action ->
        when {
            hasPlaceholder(action.service) -> null
            !HA_SERVICE.matches(action.service) -> GroundingProblem.MalformedHaService(action.service)
            services == null -> domainMismatch(action)
            else -> offeredServiceProblem(action, services, entities)
        }
    }
}

// A service handed an entity of another domain does nothing. homeassistant.* acts on any entity, a
// script may take any entity as its target, and an old-style group.* entity stands for its members;
// scene, automation, button and notify services act on their own domain alone.
private fun domainMismatch(action: TapAction.HaAction): GroundingProblem? {
    val entity = action.entity ?: return null
    val serviceDomain = action.service.substringBefore('.', "")
    val entityDomain = entity.substringBefore('.', "")
    val fits = hasPlaceholder(entity) || serviceDomain.isEmpty() || entityDomain.isEmpty() ||
        serviceDomain == entityDomain || serviceDomain in ANY_ENTITY_SERVICES || entityDomain in ANY_SERVICE_ENTITIES
    return GroundingProblem.ServiceDomainMismatch(action.service, entity).takeUnless { fits }
}

// A placeholder has no domain yet, and a call without an entity may target an area or a device
// through its data, which no filter can see. An entity the instance did not list is held to the
// domains the service's target admits, as its domain is all there is to know of it. Home Assistant
// expands an old-style group into its members before the call, so the group's own domain says nothing.
private fun offeredServiceProblem(
    action: TapAction.HaAction,
    services: List<HaService>,
    entities: Map<String, HaEntity>,
): GroundingProblem? {
    val listed = action.entity?.let(entities::get)
    val offered = services.firstOrNull { it.id == action.service }
    val entity = action.entity?.takeUnless(::hasPlaceholder)
    return when {
        offered == null -> GroundingProblem.UnknownHaService(action.service, replacements(action, listed, services))
        entity == null -> null
        else -> entityProblem(offered, entity, listed, services)
    }
}

private fun entityProblem(
    offered: HaService,
    entity: String,
    listed: HaEntity?,
    services: List<HaService>,
): GroundingProblem? {
    val domain = entity.substringBefore('.', "")
    val verb = offered.verb
    return when {
        offered.targets == null -> targetlessProblem(offered, entity)
        domain in ANY_SERVICE_ENTITIES -> null
        listed != null -> misfit(offered, entity, servicesFitting(listed, services, verb))
            .takeUnless { offered.fits(listed) }

        else -> misfit(offered, entity, servicesFittingDomain(domain, services, verb))
            .takeUnless { offered.fitsDomain(domain) }
    }
}

// Home Assistant hands the entity to a script as a variable the script may read, so a script's own
// service takes one although it declares no target.
private fun targetlessProblem(offered: HaService, entity: String): GroundingProblem? =
    GroundingProblem.HaServiceTakesNoEntity(offered.id, entity).takeUnless { offered.domain == SCRIPT_DOMAIN }

private fun misfit(offered: HaService, entity: String, fitting: List<HaService>) =
    GroundingProblem.HaServiceMisfit(offered.id, entity, fitting.take(MAX_SUGGESTIONS).map(HaService::id))

// What fits the entity, else what the domain of the entity or the service offers, else the services
// that act on anything; one with the verb the chat wrote leads.
private fun replacements(action: TapAction.HaAction, entity: HaEntity?, services: List<HaService>): List<String> {
    val verb = action.service.substringAfter('.')
    val domain = action.entity?.takeUnless(::hasPlaceholder)?.substringBefore('.', "")?.takeIf(String::isNotEmpty)
        ?: action.service.substringBefore('.')
    val fitting = entity?.let { servicesFitting(it, services, verb) }.orEmpty()
    val picks = fitting.ifEmpty {
        services.filter { it.domain == domain }.sortedWith(compareBy<HaService> { it.verb != verb }.then(SERVICE_ORDER))
    }.ifEmpty { anyEntityServices(services).take(ANY_ENTITY_SUGGESTIONS) }
    return picks.take(MAX_SUGGESTIONS).map(HaService::id)
}

internal val HA_SERVICE = Regex("""[a-z_]+\.[a-z0-9_]+""")
private const val SCRIPT_DOMAIN = "script"
private val ANY_ENTITY_SERVICES = setOf(ANY_ENTITY_DOMAIN, SCRIPT_DOMAIN)
private val ANY_SERVICE_ENTITIES = setOf("group")
private const val MAX_SUGGESTIONS = 3
private const val ANY_ENTITY_SUGGESTIONS = 2
