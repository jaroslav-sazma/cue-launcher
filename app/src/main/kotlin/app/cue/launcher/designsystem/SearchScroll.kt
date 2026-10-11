package app.cue.launcher.designsystem

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

// A keyed lazy layout holds its first visible key in place when the items change, which drops a
// re-ranked result set somewhere in its middle; a new `search` starts at the top instead. Pass the
// results where only the query changes them, the query where a reorder or toggle can change them
// too. Null is still loading, and the first search shown keeps a position restored after a
// recreation.
@Composable
fun rememberSearchListState(search: Any?): LazyListState {
    val state = rememberLazyListState()
    TopOnNewSearch(search) { state.requestScrollToItem(0) }
    return state
}

@Composable
fun rememberSearchGridState(search: Any?): LazyGridState {
    val state = rememberLazyGridState()
    TopOnNewSearch(search) { state.requestScrollToItem(0) }
    return state
}

// ScrollState has no synchronous request like the lazy states; a raw delta lands in the same frame.
@Composable
fun rememberSearchScrollState(search: Any?): ScrollState {
    val state = rememberScrollState()
    val scope = rememberCoroutineScope()
    TopOnNewSearch(search) {
        if (state.isScrollInProgress) scope.launch { state.stopScroll() }
        state.dispatchRawDelta(-state.value.toFloat())
    }
    return state
}

@Composable
private fun TopOnNewSearch(search: Any?, toTop: () -> Unit) {
    val shown = remember { ShownSearch(search) }
    DisposableEffect(search) {
        if (search != null) {
            if (shown.search != null && shown.search != search) toTop()
            shown.search = search
        }
        onDispose {}
    }
}

private class ShownSearch(var search: Any?)
