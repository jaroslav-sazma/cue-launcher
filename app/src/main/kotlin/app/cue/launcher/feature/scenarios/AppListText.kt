package app.cue.launcher.feature.scenarios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import java.util.concurrent.ConcurrentHashMap

// Phrases are composed in every scenario row and status line, so a label is looked up once per
// process and off the main thread; until then the row shows the package name. An app that is not
// installed is never remembered, so it gets its label once it is.
@Stable
class AppLabels(private val lookup: suspend (String) -> String?) {

    private val known = ConcurrentHashMap<String, String>()

    fun cached(packageName: String): String = known[packageName] ?: packageName

    suspend fun load(packageName: String): String =
        known[packageName] ?: lookup(packageName)?.also { known[packageName] = it } ?: packageName

    companion object {
        val NONE = AppLabels { null }
    }
}

val LocalAppLabels: ProvidableCompositionLocal<AppLabels> = staticCompositionLocalOf { AppLabels.NONE }

@Composable
internal fun appListText(packages: List<String>, joiner: AppJoiner): String {
    val labels = LocalAppLabels.current
    val names by produceState(packages.map(labels::cached), packages, labels) {
        value = packages.map { labels.load(it) }
    }
    return listText(names, joiner)
}

@Composable
internal fun listText(names: List<String>, joiner: AppJoiner): String {
    if (names.size < 2) return names.firstOrNull().orEmpty()
    val separator = stringResource(R.string.phrase_apps_separator)
    val head = names.dropLast(1).joinToString(separator)
    return when (joiner) {
        AppJoiner.OR -> stringResource(R.string.phrase_apps_or, head, names.last())
        AppJoiner.NOR -> stringResource(
            if (names.size == 2) R.string.phrase_apps_nor_two else R.string.phrase_apps_nor_many,
            head,
            names.last(),
        )
    }
}

enum class AppJoiner { OR, NOR }
