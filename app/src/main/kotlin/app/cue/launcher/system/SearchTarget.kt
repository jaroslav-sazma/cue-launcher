package app.cue.launcher.system

import app.cue.launcher.data.scenario.TapAction

sealed interface SearchTarget {

    data class Action(val action: TapAction) : SearchTarget

    data class SearchActivity(val packageName: String) : SearchTarget

    data object DefaultBrowser : SearchTarget
}

const val CHROMIUM_SEARCH_ACTIVITY: String = "org.chromium.chrome.browser.searchwidget.SearchActivity"

const val CHROME_PACKAGE: String = "com.android.chrome"

fun searchTargets(browserPackage: String?): List<SearchTarget> {
    val packages = listOfNotNull(browserPackage, CHROME_PACKAGE).distinct()
    return packages.map(SearchTarget::SearchActivity) + SearchTarget.DefaultBrowser
}

fun searchbarTargets(action: TapAction?, browserPackage: String?): List<SearchTarget> =
    if (action == null) searchTargets(browserPackage) else listOf(SearchTarget.Action(action))
