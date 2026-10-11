package app.cue.launcher.system.iconpack

internal class AppFilter(private val entries: Map<AppFilterKey, String>) {

    val size: Int get() = entries.size

    fun drawableFor(packageName: String, className: String): String? =
        entries[AppFilterKey(packageName, className)]

    companion object {
        val EMPTY = AppFilter(emptyMap())
    }
}

internal data class AppFilterKey(val packageName: String, val className: String)

internal fun appFilterOf(items: Sequence<Pair<String?, String?>>): AppFilter {
    val entries = mutableMapOf<AppFilterKey, String>()
    items.forEach { (component, drawable) ->
        val key = component?.let(::appFilterKey) ?: return@forEach
        val name = drawable?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
        entries.putIfAbsent(key, name)
    }
    return AppFilter(entries)
}

internal fun appFilterKey(component: String): AppFilterKey? {
    val raw = component.trim().removeSurrounding(COMPONENT_PREFIX, COMPONENT_SUFFIX).trim()
    val separator = raw.indexOf('/')
    if (separator <= 0 || separator == raw.lastIndex) return null
    val packageName = raw.substring(0, separator)
    val className = raw.substring(separator + 1).let {
        if (it.startsWith('.')) packageName + it else it
    }
    return AppFilterKey(packageName, className)
}

private const val COMPONENT_PREFIX = "ComponentInfo{"
private const val COMPONENT_SUFFIX = "}"
