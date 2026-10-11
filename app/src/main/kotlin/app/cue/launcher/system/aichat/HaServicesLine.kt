package app.cue.launcher.system.aichat

import app.cue.launcher.data.aichat.ANY_ENTITY_DOMAIN
import app.cue.launcher.data.aichat.SERVICE_ORDER
import app.cue.launcher.data.aichat.anyEntityServices
import app.cue.launcher.data.aichat.verb
import app.cue.launcher.data.ha.HaEntity
import app.cue.launcher.data.ha.HaService
import app.cue.launcher.data.ha.fits

// Said where the instance's own list is not known: no entity listed, or no list read.
internal const val HA_SERVICES_FALLBACK =
    "ha actions run a Home Assistant service on an entity of its domain (light.turn_on, switch.toggle, " +
        "lock.unlock, cover.open_cover, media_player.media_play_pause, climate.set_temperature) or " +
        "homeassistant.toggle/turn_on/turn_off on any."

// The services of the listed entities' domains, in the entities' order, those fitting the most of them
// first; a service that fits none of them is left out. Ids only: the scenario names the id.
internal fun haServicesLine(entities: List<HaEntity>, services: List<HaService>?): String {
    val offered = if (entities.isEmpty()) emptyList() else services.orEmpty()
    val picks = entities.groupBy(HaEntity::domain)
        .flatMap { (domain, members) -> domainServices(domain, members, offered) }
    val any = anyEntityServices(offered).map(HaService::verb)
    return if (picks.isEmpty() && any.isEmpty()) {
        HA_SERVICES_FALLBACK
    } else {
        buildString {
            append("ha actions run a Home Assistant service on an entity of its domain")
            if (picks.isNotEmpty()) append(" (this home offers ${picks.joinToString()})")
            if (any.isNotEmpty()) append(" or $ANY_ENTITY_DOMAIN.${any.joinToString("/")} on any")
            append(".")
        }
    }
}

private fun domainServices(domain: String, entities: List<HaEntity>, services: List<HaService>): List<String> =
    services.filter { it.domain == domain }
        .map { service -> service to entities.count(service::fits) }
        .filter { (_, fitting) -> fitting > 0 }
        .sortedWith(compareByDescending<Pair<HaService, Int>> { it.second }.thenBy(SERVICE_ORDER) { it.first })
        .map { (service, _) -> service.id }
