package app.cue.launcher.engine

import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TriggerPayload

private val TEMPLATE_VALUE_PATTERN = Regex("""(?<!\{)\{([A-Za-z]+:[^{}]+)\}(?!\})""")

internal fun templateValueKeys(text: String?): List<String> {
    if (text == null) return emptyList()
    return TEMPLATE_VALUE_PATTERN.findAll(text)
        .map { it.groupValues[1].trim().lowercase() }
        .toList()
}

fun resolveTemplateValues(
    content: TileContent,
    payload: TriggerPayload,
    states: Map<String, String> = emptyMap(),
): TileContent? =
    if (content is TileContent.Template) content.resolve(TemplateValues(payload, states)) else content

private fun TileContent.Template.resolve(payload: TemplateValues): TileContent.Template? {
    val text = text.resolveOrNull(payload)
    val text2 = text2.resolveOrNull(payload)
    val items = items.map { item -> item.resolve(payload) }
    if (text == null || text2 == null || items.any { it == null }) return null
    val resolved = copy(text = text.value, text2 = text2.value, items = items.filterNotNull())

    return if (resolved == this) this else resolved
}

private fun TemplateItem.resolve(payload: TemplateValues): TemplateItem? {
    val label = label.resolveOrNull(payload) ?: return null
    val icon = icon ?: payload.icon?.takeIf { primaryAction is TapAction.HaAction }
    return copy(label = label.value, icon = icon)
}

private fun String?.resolveOrNull(payload: TemplateValues): ResolvedText? {
    if (this == null || !TEMPLATE_VALUE_PATTERN.containsMatchIn(this)) return ResolvedText(this)
    var missing = false
    val resolved = TEMPLATE_VALUE_PATTERN.replace(this) { match ->

        val value = payload[match.groupValues[1].trim().lowercase()]
        if (value == null) missing = true
        value.orEmpty()
    }
    return if (missing) null else ResolvedText(resolved)
}

private class TemplateValues(
    private val payload: TriggerPayload,
    private val states: Map<String, String>,
) {
    val icon: String? get() = payload.icon

    operator fun get(key: String): String? = payload[key] ?: states[key]
}

@JvmInline
private value class ResolvedText(val value: String?)
