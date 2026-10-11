package app.cue.launcher.feature.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.cue.launcher.R
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.designsystem.ModalSheet
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.rememberExpandedSheetState
import app.cue.launcher.designsystem.rememberSearchListState
import app.cue.launcher.feature.home.launcherWidgetLabelRes
import app.cue.launcher.system.widget.AvailableWidget
import app.cue.launcher.system.widget.WidgetPreview
import app.cue.launcher.designsystem.SheetFrame
import app.cue.launcher.designsystem.SheetHeader
import app.cue.launcher.designsystem.SheetSearchField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(
    widgets: WidgetHosting,
    onPick: (AvailableWidget) -> Unit,
    onPickLauncherWidget: (LauncherWidgetType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    fit: (AvailableWidget) -> Boolean = { true },

    launcherWidgets: List<LauncherWidgetType> = LauncherWidgetType.entries,
) {

    val sheetState = rememberExpandedSheetState()
    ModalSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        WidgetPickerContent(
            widgets = widgets,
            onPick = onPick,
            onClose = onDismiss,
            note = note?.let { text -> { PickerNote(text) } },
            fit = fit,

            launcherWidgets = launcherWidgets,
            onPickLauncherWidget = onPickLauncherWidget,
        )
    }
}

@Composable
fun WidgetPickerContent(
    widgets: WidgetHosting,
    onPick: (AvailableWidget) -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.widget_picker_title),

    onClose: (() -> Unit)? = null,
    note: (@Composable () -> Unit)? = null,
    fit: (AvailableWidget) -> Boolean = { true },

    launcherWidgets: List<LauncherWidgetType> = emptyList(),
    onPickLauncherWidget: (LauncherWidgetType) -> Unit = {},
) {
    val catalog by produceState<List<AvailableWidget>?>(initialValue = null, widgets) {
        value = widgets.available()
    }

    val previews = remember(widgets) { WidgetPreviewCache<WidgetPreview>() }
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(catalog, query, fit) { filterWidgets(catalog.orEmpty().filter(fit), query) }
    val launcherNames = launcherWidgets.associateWith { stringResource(launcherWidgetLabelRes(it)) }
    val visibleLauncher = remember(launcherNames, query) { filterLauncherWidgets(launcherNames, query) }

    SheetFrame(
        header = {
            WidgetPickerHeader(
                title = title,
                onClose = onClose,
                note = note,
                query = query,
                onQueryChange = { query = it },
            )
        },
        modifier = modifier
            .imePadding()
            .padding(horizontal = Tokens.WidgetPicker.sheetPadding),
        scrollable = false,
    ) {
        if (visible.isEmpty() && visibleLauncher.isEmpty()) {
            Text(
                text = stringResource(
                    if (catalog == null) R.string.widget_picker_loading else R.string.widget_picker_empty,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = Tokens.WidgetPicker.rowPadding),
            )
        } else {
            LazyColumn(state = rememberSearchListState(catalog?.let { visibleLauncher to visible })) {

                items(items = visibleLauncher, key = { it.first.jsonName }) { (widget, name) ->
                    LauncherWidgetPickerRow(name = name, onPick = { onPickLauncherWidget(widget) })
                }
                items(items = visible, key = AvailableWidget::listKey) { widget ->
                    WidgetPickerRow(
                        widget = widget,
                        widgets = widgets,
                        previews = previews,
                        onPick = { onPick(widget) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetPickerHeader(
    title: String,
    onClose: (() -> Unit)?,
    note: (@Composable () -> Unit)?,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (onClose == null) {
            Text(text = title, style = MaterialTheme.typography.titleLargeEmphasized)
        } else {
            SheetHeader(title = title, onClose = onClose)
        }
        SheetSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.widget_picker_search),
            modifier = Modifier.padding(vertical = Tokens.WidgetPicker.sheetPadding),
        )
        note?.invoke()
    }
}

@Composable
private fun PickerNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Tokens.WidgetPicker.rowPadding),
    )
}

@Composable
private fun LauncherWidgetPickerRow(name: String, onPick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(vertical = Tokens.WidgetPicker.rowPadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.WidgetPicker.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Drawer.metaGap)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.widget_picker_launcher_widget),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun WidgetPickerRow(
    widget: AvailableWidget,
    widgets: WidgetHosting,
    previews: WidgetPreviewCache<WidgetPreview>,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {

    val preview by produceState(initialValue = previews.peek(widget.listKey), widget) {
        value = previews.getOrLoad(widget.listKey) { widgets.preview(widget) }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(vertical = Tokens.WidgetPicker.rowPadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.WidgetPicker.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetPreviewBox(
            preview = preview,
            widthPx = widget.minWidthPx,
            heightPx = widget.minHeightPx,
            modifier = Modifier.size(
                width = Tokens.WidgetPicker.previewWidth,
                height = Tokens.WidgetPicker.previewHeight,
            ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Drawer.metaGap)) {
            Text(
                text = widget.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = widget.appLabel,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val AvailableWidget.listKey: String
    get() = "${provider.packageName}/${provider.className}#${provider.userSerial}"
