package app.cue.launcher.designsystem

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch

// Every modal sheet in the app opens straight to full height; there is no half-expanded stop.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberExpandedSheetState(): SheetState = rememberBottomSheetState(
    initialValue = SheetValue.Hidden,
    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
)

// The sheet slides away before what comes next, rather than being torn down mid-frame. Only the
// first exit counts, so a second tap during the slide neither dismisses twice nor runs two actions;
// a slide the user interrupts re-arms it.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSheetExit(sheetState: SheetState, onDismiss: () -> Unit): (next: () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    return remember(sheetState, scope) {
        var leaving = false
        exit@{ next: () -> Unit ->
            if (leaving) return@exit
            leaving = true
            scope.launch { sheetState.hide() }.invokeOnCompletion { cause ->
                if (cause == null) {
                    dismiss()
                    next()
                } else {
                    leaving = false
                }
            }
        }
    }
}

// Every modal sheet goes through here so the supporting text inside it is shaded against the sheet
// rather than the screen behind it.
//
// Restored state waits in the registry until something composes in the same place, so a sheet not
// brought back after a recreation would hand its query to the next one opened there. The content
// therefore saves nothing, unless the host restores its sheet and passes an `opening` saved with it
// and new for every opening, which the content is keyed by.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    opening: Any? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ground = MaterialTheme.colorScheme.surfaceContainerLow
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = ground,
    ) {
        CompositionLocalProvider(LocalSupportingGround provides ground) {
            if (opening == null) {
                CompositionLocalProvider(LocalSaveableStateRegistry provides null) { content() }
            } else {
                key(opening) { content() }
            }
        }
    }
}

// A sheet is one screenful at most: whatever confirms it has to stay reachable without scrolling to
// the end, or the choice above it reads as already applied. Hence header and footer sit outside the
// scrolling body, and the body takes `weight(fill = false)` so short sheets stay short.
@Composable
fun SheetFrame(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    scrollState: ScrollState = rememberScrollState(),
    gap: Dp = Tokens.Scenarios.rowGap,
    footer: @Composable (ColumnScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        header()
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(vertical = Tokens.Builder.sheetPadding),
            verticalArrangement = Arrangement.spacedBy(gap),
            content = body,
        )
        footer?.let { SheetFooter(content = it) }
    }
}

@Composable
fun SheetFooter(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ground = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(modifier = modifier.fillMaxWidth(), color = ground) {
        CompositionLocalProvider(LocalSupportingGround provides ground) {
            Column(
                modifier = Modifier.padding(
                    horizontal = Tokens.Settings.surfacePadding,
                    vertical = Tokens.Settings.highlightPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
                content = content,
            )
        }
    }
}

// One place for the expressive medium-size button: 56 dp tall with the matching shapes, padding
// and text style. Both the sheet footers and the wizard bottom bar are built from these.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediumFilledButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
        enabled = enabled,
        shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
    ) {
        Text(text = label, style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediumOutlinedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
        enabled = enabled,
        shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
    ) {
        Text(text = label, style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
    }
}

// A null onClick is the disabled footer button: the sheets read "there is nothing to confirm yet".
@Composable
fun SheetConfirmButton(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    MediumFilledButton(
        label = label,
        onClick = { onClick?.invoke() },
        modifier = modifier.fillMaxWidth(),
        enabled = onClick != null,
    )
}

@Composable
fun SheetSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MediumOutlinedButton(label = label, onClick = onClick, modifier = modifier.fillMaxWidth())
}
