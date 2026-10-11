package app.cue.launcher.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// The field never expands into a SearchBar: the SearchBarState stays unattached and only the
// text flows out through onQueryChange.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    container: Color,
    modifier: Modifier = Modifier,
) {
    val state = rememberTextFieldState(query)
    val change by rememberUpdatedState(onQueryChange)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { change(it) }
    }
    Surface(
        modifier = modifier,
        shape = SearchBarDefaults.inputFieldShape,
        color = container,
    ) {
        SearchBarDefaults.InputField(
            textFieldState = state,
            searchBarState = rememberSearchBarState(),
            onSearch = {},
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = placeholder) },
            leadingIcon = {
                GlyphIcon(
                    name = GLYPH_SEARCH,
                    size = Tokens.Settings.glyph,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

private const val GLYPH_SEARCH = "magnify"
