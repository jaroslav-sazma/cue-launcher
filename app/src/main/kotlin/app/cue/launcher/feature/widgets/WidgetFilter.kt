package app.cue.launcher.feature.widgets

import app.cue.launcher.feature.drawer.search.normalizeForSearch
import app.cue.launcher.feature.drawer.search.searchWords
import app.cue.launcher.system.widget.AvailableWidget

fun filterWidgets(widgets: List<AvailableWidget>, query: String): List<AvailableWidget> {
    val queryWords = searchWords(normalizeForSearch(query))
    if (queryWords.isEmpty()) return widgets
    return widgets.filter { widget -> matchesWidgetQuery(widget, queryWords) }
}

fun <T> filterLauncherWidgets(widgets: Map<T, String>, query: String): List<Pair<T, String>> {
    val queryWords = searchWords(normalizeForSearch(query))
    return widgets.entries
        .filter { (_, name) -> matchesWords(searchWords(normalizeForSearch(name)), queryWords) }
        .map { (widget, name) -> widget to name }
}

fun matchesWidgetQuery(widget: AvailableWidget, queryWords: List<String>): Boolean =
    matchesWords(widgetSearchWords(widget), queryWords)

private fun matchesWords(words: List<String>, queryWords: List<String>): Boolean =
    queryWords.all { queryWord -> words.any { word -> word.startsWith(queryWord) } }

private fun widgetSearchWords(widget: AvailableWidget): List<String> =
    searchWords(normalizeForSearch("${widget.label} ${widget.appLabel} ${widget.provider.packageName}"))
