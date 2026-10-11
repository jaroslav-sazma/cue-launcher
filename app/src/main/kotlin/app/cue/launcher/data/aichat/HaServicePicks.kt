package app.cue.launcher.data.aichat

import app.cue.launcher.data.ha.HaEntity
import app.cue.launcher.data.ha.HaService
import app.cue.launcher.data.ha.fits
import app.cue.launcher.data.ha.fitsDomain

const val ANY_ENTITY_DOMAIN = "homeassistant"

val ANY_ENTITY_VERBS = listOf("toggle", "turn_on", "turn_off")

val HaService.verb: String get() = id.substringAfter('.')

// Only a tie-break after how many entities a service fits: by the alphabet alone a media player
// offers clear_playlist and join before play/pause, and its power toggle is not what a tile wants.
val SERVICE_ORDER: Comparator<HaService> = compareBy<HaService> { service ->
    COMMON_VERBS.indexOf(service.verb).takeIf { it >= 0 } ?: COMMON_VERBS.size
}.thenBy(HaService::id)

// The entity's own domain first, then the services that act on any entity, then the rest — a script
// or an integration's service whose target takes anything fits every entity as well.
fun servicesFitting(entity: HaEntity, services: List<HaService>, wantedVerb: String? = null): List<HaService> =
    services.filter { it.fits(entity) }.sortedWith(fittingOrder(entity.domain, wantedVerb))

fun servicesFittingDomain(domain: String, services: List<HaService>, wantedVerb: String? = null): List<HaService> =
    services.filter { it.fitsDomain(domain) }.sortedWith(fittingOrder(domain, wantedVerb))

private fun fittingOrder(domain: String, wantedVerb: String?): Comparator<HaService> =
    compareBy<HaService>({ domainRank(it, domain) }, { it.verb != wantedVerb }).then(SERVICE_ORDER)

fun anyEntityServices(services: List<HaService>): List<HaService> =
    ANY_ENTITY_VERBS.mapNotNull { verb -> services.firstOrNull { it.id == "$ANY_ENTITY_DOMAIN.$verb" } }

private fun domainRank(service: HaService, domain: String): Int = when (service.domain) {
    domain -> 0
    ANY_ENTITY_DOMAIN -> 1
    else -> 2
}

private val COMMON_VERBS = listOf(
    "media_play_pause", "open_cover", "close_cover", "unlock", "lock", "set_temperature", "press", "trigger",
    "start", "toggle", "turn_on", "turn_off",
)
