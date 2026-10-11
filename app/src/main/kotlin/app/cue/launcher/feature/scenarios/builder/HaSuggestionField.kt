package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.cue.launcher.R
import app.cue.launcher.data.ha.HaService
import app.cue.launcher.data.ha.haServiceNamesNeeded
import app.cue.launcher.data.ha.haServicesNamed
import app.cue.launcher.designsystem.GLYPH_CHECK
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// Free text first, suggestions second: whatever is typed stays the value, and a suggestion only
// fills the field in. The field keeps its own TextFieldValue so a picked suggestion lands with the
// cursor at its end rather than wherever the typing had left it. Leaving the field drops the wish to
// open: a list that arrived later would otherwise open its menu there, Material would pull the focus
// back, and typing meant for another field would land in this one.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HaSuggestionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: HaSuggestions,
    modifier: Modifier = Modifier,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var open by remember { mutableStateOf(false) }
    val shown = if (field.text == value) field else TextFieldValue(value, TextRange(value.length))
    val offered = suggestions.rows.isNotEmpty()
    val expanded = open && offered
    val pick: (String) -> Unit = { picked ->
        field = TextFieldValue(picked, TextRange(picked.length))
        open = false
        onValueChange(picked)
    }
    val anchor = remember { FieldAnchor() }
    val maxHeight = if (expanded) aboveKeyboard(anchor.bottom) else null
    val bringIntoView = remember { BringIntoViewRequester() }
    val menuScroll = rememberScrollState()
    MenuEffects(expanded, suggestions.rows, menuScroll, bringIntoView, anchor)

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = shown,
            onValueChange = { edited ->
                if (edited.text != shown.text) open = true
                field = edited
                if (edited.text != value) onValueChange(edited.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoView)
                .onFocusChanged { if (!it.isFocused) open = false }
                .onGloballyPositioned(anchor::place)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            label = { Text(text = label) },
            trailingIcon = if (offered) {
                {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expanded,
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable),
                    )
                }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { open = false },
            modifier = maxHeight?.let { Modifier.heightIn(max = it) } ?: Modifier,
            scrollState = menuScroll,
            shape = MenuDefaults.standaloneGroupShape,
            containerColor = MenuDefaults.groupStandardContainerColor,
        ) {
            HaSuggestionItems(suggestions = suggestions, value = value, onPick = pick)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HaSuggestionItems(suggestions: HaSuggestions, value: String, onPick: (String) -> Unit) {
    val count = suggestions.rows.size + if (suggestions.more > 0) 1 else 0
    suggestions.rows.forEachIndexed { index, row ->
        SelectableDropdownMenuItem(
            selected = row.value == value.trim(),
            onClick = { onPick(row.value) },
            text = { Text(text = row.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            shapes = MenuDefaults.itemShape(index, count),
            supportingText = row.detail?.let { detail ->
                { Text(text = detail, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            },
            selectedLeadingIcon = {
                GlyphIcon(name = GLYPH_CHECK, size = MenuDefaults.LeadingIconSize, tint = LocalContentColor.current)
            },
            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
        )
    }
    if (suggestions.more > 0) {
        DropdownMenuItem(
            onClick = {},
            text = {
                val more = suggestions.more
                Text(text = pluralStringResource(R.plurals.action_ha_suggestions_more, more, more))
            },
            shape = MenuDefaults.itemShape(count - 1, count).shape,
            enabled = false,
            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
        )
    }
}

// The menu sizes itself by the window's visible frame, which inside a bottom sheet's window does not
// shrink for the keyboard, so a long list ran on underneath it and its last rows could not be
// scrolled into view. While the keyboard is up the menu is held to the room above it.
@Composable
private fun aboveKeyboard(anchorBottom: Int): Dp? {
    val density = LocalDensity.current
    val keyboard = WindowInsets.ime.getBottom(density)
    if (keyboard == 0 || anchorBottom == 0) return null
    val keyboardTop = LocalWindowInfo.current.containerSize.height - keyboard
    val room = with(density) { (keyboardTop - anchorBottom).toDp() } - Tokens.Builder.suggestionKeyboardGap
    return room.coerceAtLeast(Tokens.Builder.suggestionMinHeight)
}

// Material keeps the menu's scroll offset while it is closed and while its rows change, which would
// leave the chosen value or the best match, both at the top, out of sight; so the menu starts at the
// top each time. And a field sitting right on the keyboard would open its menu underneath it, so the
// sheet scrolls the field up, as far as it can, to leave room for a few rows below. That waits for the
// keyboard: the sheet makes room for it only as it rises, after the tap that opened the menu.
@Composable
private fun MenuEffects(
    expanded: Boolean,
    rows: List<HaSearchRow>,
    scroll: ScrollState,
    requester: BringIntoViewRequester,
    anchor: FieldAnchor,
) {
    val density = LocalDensity.current
    val room = with(density) { Tokens.Builder.suggestionMinHeight.toPx() }
    val ime = WindowInsets.ime
    LaunchedEffect(expanded, rows) {
        if (expanded) scroll.scrollTo(0)
    }
    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        snapshotFlow { ime.getBottom(density) }.collectLatest {
            withFrameNanos {}
            requester.bringIntoView(Rect(0f, 0f, anchor.width, anchor.height + room))
        }
    }
}

// One after the other, on purpose: an instance that did not hand over its entities is not asked for
// its services, so a missing connection costs one try per address instead of two, and a reachable one
// answers both over the same connection. The English services show as soon as they are in; the names
// in the app's language follow, so a socket that stalls costs the labels, never the list.
@Composable
internal fun rememberHaChoices(ha: HaAccess): State<HaChoices> {
    val language = stringResource(R.string.ha_services_language)
    return produceState(HaChoices.NONE, ha, language) {
        val entities = ha.entities.entities()
        if (entities.isEmpty()) return@produceState
        val entityChoices = withContext(Dispatchers.Default) { HaEntityChoices(entities) }
        value = HaChoices(entityChoices, HaServiceChoices.NONE)
        val services = ha.services.list()
        if (services.isEmpty()) return@produceState
        value = HaChoices(entityChoices, withContext(Dispatchers.Default) { HaServiceChoices(services) })
        if (!haServiceNamesNeeded(language)) return@produceState
        val names = ha.services.names(language, services.mapTo(HashSet(), HaService::domain))
        if (names.isEmpty()) return@produceState
        val named = withContext(Dispatchers.Default) { HaServiceChoices(haServicesNamed(services, names)) }
        value = HaChoices(entityChoices, named)
    }
}

// Only the bottom edge is read in composition; the size serves the scroll request alone.
private class FieldAnchor {

    var bottom by mutableIntStateOf(0)
        private set

    var width = 0f
        private set

    var height = 0f
        private set

    fun place(coordinates: LayoutCoordinates) {
        bottom = (coordinates.positionInWindow().y + coordinates.size.height).roundToInt()
        width = coordinates.size.width.toFloat()
        height = coordinates.size.height.toFloat()
    }
}
