package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Note

@Composable
internal fun ContentStep(
    size: TileSize,
    draft: ContentDraft,
    scenarioName: String,
    launcherApps: LauncherAppsRepository,
    widgets: WidgetHosting,
    callbacks: ContentStepCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.builder_content_section))
        ContentKindChoice(size = size, draft = draft, onChange = callbacks.onChange)

        if (draft.kind == ContentKind.TEMPLATE) {
            LayoutChoice(size = size, draft = draft, onChange = callbacks.onChange)
        }
        SectionLabel(title = stringResource(R.string.builder_preview_section))

        TilePreview(
            size = size,
            draft = draft,
            scenarioName = scenarioName,
            launcherApps = launcherApps,
            widgets = widgets,
            modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding),
        )
        when (draft.kind) {
            ContentKind.TEMPLATE -> TemplateForm(draft, launcherApps, callbacks)
            ContentKind.APP_WIDGET -> AppWidgetForm(draft, callbacks)

            ContentKind.LAUNCHER_WIDGET -> {
                draft.launcherWidget?.let { launcherWidgetNote(it) }?.let { note ->
                    Note(
                        text = note,
                        modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
                    )
                }
                LauncherWidgetAccessRequest(draft.launcherWidget)
            }
        }
    }
}

internal data class ContentStepCallbacks(
    val onChange: (ContentDraft) -> Unit,

    val onPickWidget: () -> Unit,

    val onConfigureWidget: () -> Unit,

    val onPickAction: (ActionSlot) -> Unit,

    val onRemoveAction: (ActionSlot) -> Unit,

    val onMoveAction: (item: Int, from: Int, to: Int) -> Unit,

    val onPickIcon: (Int) -> Unit,
)

@Composable
private fun ContentKindChoice(
    size: TileSize,
    draft: ContentDraft,
    onChange: (ContentDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kinds = contentKindsFor(size)
    if (size == TileSize.HERO_4X2) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
        ) {
            kinds.forEach { kind ->
                when (kind) {
                    ContentKind.LAUNCHER_WIDGET -> LauncherWidgetRows(size, draft, onChange)
                    else -> RadioRow(
                        title = contentKindLabel(kind, size),
                        detail = contentKindDetail(draft, kind),
                        selected = draft.kind == kind,
                        onSelect = { onChange(draft.copy(kind = kind)) },
                    )
                }
            }
        }
        return
    }
    ConnectedChoiceRow(
        options = kinds.map { kind ->
            ConnectedChoice(
                label = contentKindLabel(kind, size),
                selected = draft.kind == kind,
                onSelect = { onChange(draft.copy(kind = kind)) },
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun LauncherWidgetRows(
    size: TileSize,
    draft: ContentDraft,
    onChange: (ContentDraft) -> Unit,
) {
    launcherWidgetsFor(size).forEach { widget ->
        RadioRow(
            title = launcherWidgetLabel(widget),
            detail = launcherWidgetDetail(widget),
            selected = draft.kind == ContentKind.LAUNCHER_WIDGET && draft.launcherWidget == widget,
            onSelect = {
                onChange(draft.copy(kind = ContentKind.LAUNCHER_WIDGET, launcherWidget = widget))
            },
        )
    }
}

@Composable
private fun LayoutChoice(
    size: TileSize,
    draft: ContentDraft,
    onChange: (ContentDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layouts = templateLayoutsFor(size)
    if (layouts.size <= 1) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.builder_layout_section))
        ConnectedChoiceRow(
            options = layouts.map { layout ->
                ConnectedChoice(
                    label = templateLayoutLabel(layout),
                    selected = draft.layout == layout,
                    onSelect = { onChange(draft.withLayout(layout)) },
                )
            },
        )
    }
}

@Composable
private fun TemplateForm(
    draft: ContentDraft,
    launcherApps: LauncherAppsRepository,
    callbacks: ContentStepCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        when (draft.layout) {
            TemplateLayout.ICON_TEXT -> IconTextForm(draft, launcherApps, callbacks)
            TemplateLayout.TEXT_ONLY -> TextOnlyForm(draft, launcherApps, callbacks)
            TemplateLayout.TEXT_TWO_ICONS -> {
                TileTexts(draft, callbacks.onChange)
                ItemList(draft, launcherApps, callbacks)
            }

            TemplateLayout.FOUR_ICONS -> ItemList(draft, launcherApps, callbacks)
            null -> Unit
        }

        if (draft.layout != null) {
            Note(
                text = stringResource(R.string.builder_template_values_note),
                modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
            )
        }
    }
}

@Composable
private fun IconTextForm(
    draft: ContentDraft,
    launcherApps: LauncherAppsRepository,
    callbacks: ContentStepCallbacks,
    modifier: Modifier = Modifier,
) {
    val item = draft.items.firstOrNull() ?: TemplateItemDraft()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.builder_action_section))
        ActionList(item = item, itemIndex = 0, callbacks = callbacks)
        SectionLabel(title = stringResource(R.string.builder_icon_section))
        IconRow(
            item = item,
            launcherApps = launcherApps,
            onClick = { callbacks.onPickIcon(0) },
        )
        SectionLabel(title = stringResource(R.string.builder_text_section))
        OutlinedTextField(
            value = item.label,
            onValueChange = { callbacks.onChange(draft.withItem(0, item.copy(label = it))) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
            singleLine = true,
        )
    }
}

@Composable
private fun TextOnlyForm(
    draft: ContentDraft,
    launcherApps: LauncherAppsRepository,
    callbacks: ContentStepCallbacks,
    modifier: Modifier = Modifier,
) {
    val item = draft.items.firstOrNull() ?: TemplateItemDraft()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        TileTexts(draft, callbacks.onChange)
        SectionLabel(title = stringResource(R.string.builder_action_tile_section))
        ActionList(item = item, itemIndex = 0, callbacks = callbacks)
        SectionLabel(title = stringResource(R.string.builder_icon_section))
        IconRow(
            item = item,
            launcherApps = launcherApps,
            onClick = { callbacks.onPickIcon(0) },
        )
    }
}

@Composable
private fun TileTexts(
    draft: ContentDraft,
    onChange: (ContentDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.builder_tile_text_section))
        OutlinedTextField(
            value = draft.text,
            onValueChange = { onChange(draft.copy(text = it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.text2,
            onValueChange = { onChange(draft.copy(text2 = it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
            label = { Text(text = stringResource(R.string.builder_tile_text2_label)) },
            singleLine = true,
        )
    }
}

@Composable
private fun ItemList(
    draft: ContentDraft,
    launcherApps: LauncherAppsRepository,
    callbacks: ContentStepCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        SectionLabel(title = templateItemsSectionTitle(draft.layout ?: TemplateLayout.FOUR_ICONS))
        draft.items.forEachIndexed { index, item ->
            ItemRow(
                index = index,
                item = item,
                launcherApps = launcherApps,
                callbacks = callbacks,
                onLabel = { callbacks.onChange(draft.withItem(index, item.copy(label = it))) },
            )
        }
    }
}

@Composable
private fun ItemRow(
    index: Int,
    item: TemplateItemDraft,
    launcherApps: LauncherAppsRepository,
    callbacks: ContentStepCallbacks,
    onLabel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {

        SectionLabel(title = stringResource(R.string.builder_item_ordinal, index + 1))
        ActionList(item = item, itemIndex = index, callbacks = callbacks)
        if (item.isFilled) {
            IconRow(
                item = item,
                launcherApps = launcherApps,
                onClick = { callbacks.onPickIcon(index) },
            )
            OutlinedTextField(
                value = item.label,
                onValueChange = onLabel,
                modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
                label = { Text(text = stringResource(R.string.builder_item_label)) },
                singleLine = true,
            )
        }
    }
}
