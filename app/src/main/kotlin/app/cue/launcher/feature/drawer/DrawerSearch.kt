package app.cue.launcher.feature.drawer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import app.cue.launcher.feature.drawer.search.AppSearchIndex
import app.cue.launcher.feature.drawer.search.AppSearchResult
import app.cue.launcher.feature.drawer.search.ContactSearchIndex
import app.cue.launcher.feature.drawer.search.ContactSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

// A data class on purpose: re-indexing that yields the same results (a contacts sync, the second
// app emission with English labels) must compare equal, or the list resets to the top for nothing.
@Immutable
data class DrawerSearch(val apps: List<AppSearchResult>, val contacts: List<ContactSearchResult>) {
    val isEmpty: Boolean get() = apps.isEmpty() && contacts.isEmpty()

    val enterApp: AppSearchResult? get() = apps.firstOrNull()

    val enterContact: ContactSearchResult? get() = if (apps.isEmpty()) contacts.firstOrNull() else null

    companion object {
        val EMPTY = DrawerSearch(emptyList(), emptyList())
    }
}

// One value for both lists, so the rows and the Enter target always answer the same query.
@Composable
internal fun drawerSearch(
    apps: AppSearchIndex,
    contacts: ContactSearchIndex,
    query: TextFieldState,
): State<DrawerSearch> = produceState(initialValue = DrawerSearch.EMPTY, apps, contacts) {
    snapshotFlow { query.text.toString() }.collectLatest { text ->
        value = withContext(Dispatchers.Default) {
            DrawerSearch(apps = apps.search(text), contacts = contacts.search(text))
        }
    }
}
