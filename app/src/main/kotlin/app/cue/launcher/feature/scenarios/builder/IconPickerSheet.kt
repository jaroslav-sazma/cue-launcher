package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.cue.launcher.R
import app.cue.launcher.designsystem.GLYPH_CHEVRON
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.rememberSearchGridState
import app.cue.launcher.designsystem.rememberSearchScrollState
import app.cue.launcher.designsystem.glyph.LocalMdiGlyphs
import app.cue.launcher.designsystem.glyph.mdiGlyphName
import app.cue.launcher.designsystem.glyph.mdiIconReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.SheetFrame
import app.cue.launcher.designsystem.SheetHeader
import app.cue.launcher.designsystem.SheetSearchField
import app.cue.launcher.designsystem.SheetNote
import app.cue.launcher.designsystem.SheetConfirmButton

@Composable
internal fun IconPickerSheetContent(
    item: TemplateItemDraft,
    onConfirm: (String) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glyphs = LocalMdiGlyphs.current
    // Null until there is an index to ask: an empty one would answer every query with no match.
    val index by produceState<GlyphIndex?>(null, glyphs) {
        if (!glyphs.isEmpty) value = withContext(Dispatchers.Default) { glyphIndexOf(glyphs.names) }
    }

    var query by rememberSaveable { mutableStateOf(mdiGlyphName(item.icon).orEmpty()) }

    val results by produceState<List<String>?>(null, index, query) {
        val ready = index ?: return@produceState
        value = withContext(Dispatchers.Default) { ready.search(query) }
    }
    var selected by remember(item.icon) { mutableStateOf(mdiGlyphName(item.icon)) }

    SheetFrame(
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap)) {
                SheetHeader(title = stringResource(R.string.builder_icon_section), onClose = onClose)
                SheetSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.builder_catalog_search),
                )
            }
        },
        modifier = modifier,
        scrollState = rememberSearchScrollState(results),
        footer = {
            SheetConfirmButton(
                label = stringResource(R.string.builder_icon_use),
                onClick = selected?.let { glyph -> { onConfirm(mdiIconReference(glyph)) } },
            )
        },
    ) {
        when {
            results == null -> SheetNote(text = stringResource(R.string.builder_icon_loading))
            results?.isEmpty() == true -> SheetNote(text = stringResource(R.string.builder_catalog_no_match))
        }
        results?.let { names -> GlyphGrid(names = names, selected = selected, onSelect = { selected = it }) }
        SheetNote(text = stringResource(R.string.builder_icon_ha_note))
        IconResetRow(item = item, onReset = onReset)
    }
}

@Composable
private fun GlyphGrid(
    names: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = Tokens.Builder.glyphCell),
        state = rememberSearchGridState(names),
        modifier = modifier
            .heightIn(max = Tokens.Builder.glyphGridHeight)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        items(items = names, key = { it }) { name ->
            GlyphCell(name = name, selected = name == selected, onClick = { onSelect(name) })
        }
    }
}

@Composable
private fun IconResetRow(
    item: TemplateItemDraft,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TonalRow(onClick = onReset, modifier = modifier, enabled = item.icon != null) {
        RowText(
            title = stringResource(R.string.builder_icon_reset),
            modifier = Modifier.weight(1f),
            detail = if (item.icon == null) {
                stringResource(R.string.builder_icon_reset_none)
            } else {
                iconSummary(item.copy(icon = null))
            },
        )
        GlyphIcon(
            name = GLYPH_CHEVRON,
            size = Tokens.Settings.glyph,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun GlyphCell(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(

        modifier = modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {

        Surface(
            modifier = Modifier.size(Tokens.Builder.glyphCellIcon),
            shape = RoundedCornerShape(Tokens.Builder.rowRadius),
            color = if (selected) scheme.primary else scheme.surfaceContainerHigh,
            contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
        ) {
            Box(contentAlignment = Alignment.Center) {
                GlyphIcon(name = name, tint = LocalContentColor.current, contentDescription = name)
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
