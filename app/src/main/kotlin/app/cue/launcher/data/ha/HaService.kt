package app.cue.launcher.data.ha

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

// targets is null for a service that takes no target at all, and empty for one whose target accepts
// any entity. name is the English one /api/services carries, localName the one the app's language has.
data class HaService(
    val id: String,
    val name: String? = null,
    val localName: String? = null,
    val targets: List<HaEntityFilter>? = null,
) {

    val domain: String get() = id.substringBefore('.')
}

data class HaEntityFilter(
    val domains: Set<String>? = null,
    val deviceClasses: Set<String>? = null,
    val features: List<Int>? = null,
) {

    fun admits(entity: HaEntity): Boolean {
        val domain = domains?.contains(entity.domain) != false
        val deviceClass = deviceClasses == null || entity.deviceClass?.let(deviceClasses::contains) == true
        val featureMask = entity.features
        val feature = features == null || featureMask == null || features.any { featureMask and it != 0 }
        return domain && deviceClass && feature
    }
}

data class HaServiceList(val probe: HaProbe, val services: List<HaService> = emptyList())

fun haServicesOf(services: JsonElement): List<HaService> = (services as? JsonArray).orEmpty()
    .flatMap { element ->
        val entry = element as? JsonObject
        val domain = textOf(entry?.get("domain"))
        val described = entry?.get("services") as? JsonObject
        if (domain == null || described == null) {
            emptyList()
        } else {
            described.mapNotNull { (service, description) -> serviceOf("$domain.$service", description as? JsonObject) }
        }
    }
    .distinctBy(HaService::id)
    .sortedBy(HaService::id)

fun haServicesNamed(services: List<HaService>, names: Map<String, String>): List<HaService> =
    if (names.isEmpty()) services else services.map { service -> service.copy(localName = names[service.id]) }

// The filter Home Assistant's own action editor applies. A filter by integration is left out: the
// state list does not say which integration an entity comes from, and one service too many beats
// hiding the right one. For the same reason an entity whose state reports no supported_features is
// not held to a feature filter, while a missing device_class is a class of its own, as in Home Assistant.
fun HaService.fits(entity: HaEntity): Boolean =
    targets?.let { filters -> filters.isEmpty() || filters.any { it.admits(entity) } } ?: false

// For an entity the instance did not list — offline, or typed by hand — only its domain is known.
fun HaService.fitsDomain(domain: String): Boolean =
    targets?.let { filters -> filters.isEmpty() || filters.any { it.domains?.contains(domain) != false } } ?: false

// The launcher calls a service without asking for its response, which Home Assistant refuses for one
// that must return one, so such a service could never work from a tile.
private fun serviceOf(id: String, description: JsonObject?): HaService? {
    val response = description?.get("response") as? JsonObject
    val mustRespond = response != null && (response["optional"] as? JsonPrimitive)?.booleanOrNull != true
    if (description == null || mustRespond || !HA_ID_PATTERN.matches(id)) return null
    return HaService(
        id = id,
        name = textOf(description["name"])?.trim()?.takeIf(String::isNotEmpty),
        targets = targetsOf(description),
    )
}

// A target key, even one left null, is what the frontend reads as "takes entities". A service without
// one may still take an entity_id field, as scripts and older integrations do, and then its entity
// selector filters the same way — either directly or through a filter list.
private fun targetsOf(description: JsonObject): List<HaEntityFilter>? {
    val selector = ((description["fields"] as? JsonObject)?.get("entity_id") as? JsonObject)
        ?.let { (it["selector"] as? JsonObject)?.get("entity") as? JsonObject }
    val entity = when {
        "target" in description -> (description["target"] as? JsonObject)?.get("entity")
        selector != null -> selector["filter"] ?: selector
        else -> return null
    }
    return when (entity) {
        is JsonObject -> listOf(filterOf(entity))
        is JsonArray -> entity.filterIsInstance<JsonObject>().map(::filterOf)
        else -> emptyList()
    }
}

private fun filterOf(filter: JsonObject): HaEntityFilter = HaEntityFilter(
    domains = textsOf(filter["domain"]),
    deviceClasses = textsOf(filter["device_class"]),
    features = masksOf(filter["supported_features"]),
)

private fun textsOf(element: JsonElement?): Set<String>? = when (element) {
    is JsonArray -> element.mapNotNull(::textOf).toSet()
    else -> textOf(element)?.let(::setOf)
}

// Home Assistant resolves the feature names of services.yaml to bit masks before it answers; anything
// still in another form cannot be checked, and an unchecked filter lets everything through.
private fun masksOf(element: JsonElement?): List<Int>? {
    val items = when (element) {
        null -> return null
        is JsonArray -> element.toList()
        else -> listOf(element)
    }
    val masks = items.mapNotNull { (it as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.intOrNull }
    return masks.takeIf { it.size == items.size }
}

private fun textOf(element: JsonElement?): String? =
    (element as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
