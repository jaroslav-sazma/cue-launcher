package app.cue.launcher.data.ha

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

data class HaIconAttributes(val deviceClass: String? = null, val icon: String? = null)

@Serializable
data class HaIconSet(val default: String? = null, val states: Map<String, String> = emptyMap())

// asked are the domains the table was read for, a domain Home Assistant has no icons for included:
// asking again for it would bring nothing new until the next version.
@Serializable
data class HaIconTable(
    val instance: String,
    val version: String,
    val domains: Map<String, Map<String, HaIconSet>> = emptyMap(),
    val asked: Set<String> = emptySet(),
) {

    fun covers(needed: Set<String>): Boolean = asked.containsAll(needed)

    // A table read before the handshake carried a version, or from an instance that sends none, is
    // never outdated by one.
    fun isOutdated(announced: String?): Boolean =
        announced != null && version.isNotEmpty() && announced != version

    fun with(read: HaIconTable): HaIconTable =
        if (read.instance != instance || read.version != version) {
            read
        } else {
            copy(domains = domains + read.domains, asked = asked + read.asked)
        }
}

data class HaIcons(val version: String, val domains: Map<String, Map<String, HaIconSet>>)

// Only a digest of which instance the table belongs to is stored: the table outlives a disconnect,
// and the instance's addresses should not.
fun haIconInstance(config: HaConfig): String = MessageDigest.getInstance(DIGEST)
    .digest(haInstanceKey(config).toByteArray())
    .joinToString("") { "%02x".format(it) }

// Home Assistant's own order: an icon set on the entity, then the one its domain gives the device
// class — or the domain's plain set when the class has none — for the state, else that set's
// default. The rest only catches a name the bundled font does not know yet.
fun haStateGlyphs(
    table: HaIconTable?,
    entity: String,
    state: String?,
    attributes: HaIconAttributes?,
): List<String> {
    val sets = table?.domains?.get(entity.substringBefore('.')).orEmpty()
    val plain = sets[PLAIN_SET]
    val chosen = attributes?.deviceClass?.let(sets::get) ?: plain
    return listOfNotNull(
        attributes?.icon,
        state?.let { chosen?.states?.get(it) },
        chosen?.default,
        state?.let { plain?.states?.get(it) },
        plain?.default,
    ).distinct()
}

fun haIconAttributesOf(attributes: JsonObject?): HaIconAttributes? {
    if (attributes == null) return null
    return HaIconAttributes(deviceClass = attributes.text(DEVICE_CLASS), icon = attributes.text(ICON))
}

// The compressed format of subscribe_entities: an added entity brings every attribute, a change
// only the ones it gained under "+" and the names of the ones it lost under "-".
fun haLiveAttributes(
    current: Map<String, HaIconAttributes>,
    event: JsonObject,
    entities: Set<String>,
): Map<String, HaIconAttributes> {
    val attributes = current.toMutableMap()
    (event[HA_LIVE_ADD] as? JsonObject)?.forEach { (entity, added) ->
        val read = haIconAttributesOf((added as? JsonObject)?.get(HA_LIVE_ATTRIBUTES) as? JsonObject)
        if (entity in entities && read != null) attributes[entity] = read
    }
    (event[HA_LIVE_CHANGE] as? JsonObject)?.forEach { (entity, diff) ->
        if (entity !in entities || diff !is JsonObject) return@forEach
        attributes[entity] = changed(attributes[entity] ?: HaIconAttributes(), diff)
    }
    (event[HA_LIVE_REMOVE] as? JsonArray)?.forEach { entity ->
        (entity as? JsonPrimitive)?.content?.let { attributes -= it }
    }
    return attributes
}

private fun changed(current: HaIconAttributes, diff: JsonObject): HaIconAttributes {
    val gained = (diff[HA_LIVE_DIFF_ADD] as? JsonObject)?.get(HA_LIVE_ATTRIBUTES) as? JsonObject
    val lost = ((diff[HA_LIVE_DIFF_REMOVE] as? JsonObject)?.get(HA_LIVE_ATTRIBUTES) as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
    return HaIconAttributes(
        deviceClass = if (DEVICE_CLASS in lost) null else gained.text(DEVICE_CLASS) ?: current.deviceClass,
        icon = if (ICON in lost) null else gained.text(ICON) ?: current.icon,
    )
}

fun haIconsOf(resources: JsonObject): Map<String, Map<String, HaIconSet>> = resources.entries
    .mapNotNull { (domain, sets) ->
        val parsed = (sets as? JsonObject)?.entries?.mapNotNull { (key, set) ->
            (set as? JsonObject)?.let(::iconSetOf)?.let { key to it }
        }?.toMap()
        parsed?.takeIf(Map<String, HaIconSet>::isNotEmpty)?.let { domain to it }
    }
    .toMap()

private fun iconSetOf(set: JsonObject): HaIconSet? {
    val states = (set[STATE] as? JsonObject)?.entries
        ?.mapNotNull { (state, icon) -> textOf(icon)?.let { state to it } }
        ?.toMap().orEmpty()
    val default = textOf(set[DEFAULT])
    return if (default == null && states.isEmpty()) null else HaIconSet(default, states)
}

private fun JsonObject?.text(key: String): String? = textOf(this?.get(key))?.takeIf(String::isNotBlank)

private fun textOf(element: JsonElement?): String? =
    (element as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

private const val DIGEST = "SHA-256"

private const val PLAIN_SET = "_"

private const val DEVICE_CLASS = "device_class"

private const val ICON = "icon"

private const val DEFAULT = "default"

private const val STATE = "state"
