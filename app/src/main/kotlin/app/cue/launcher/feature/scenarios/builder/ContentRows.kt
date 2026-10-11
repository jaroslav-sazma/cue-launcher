package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import app.cue.launcher.R
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.designsystem.GLYPH_CHEVRON
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.scenarios.ScenarioItemIcon
import app.cue.launcher.feature.scenarios.rememberReorderState
import app.cue.launcher.feature.scenarios.reorderable
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.Note

@Composable
internal fun ActionList(
    item: TemplateItemDraft,
    itemIndex: Int,
    callbacks: ContentStepCallbacks,
    modifier: Modifier = Modifier,
) {
    val reorder = rememberReorderState()
    val moveUp = stringResource(R.string.builder_action_move_up)
    val moveDown = stringResource(R.string.builder_action_move_down)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        item.actions.forEachIndexed { index, chosen ->
            val move: (Int, Int) -> Unit = { from, to -> callbacks.onMoveAction(itemIndex, from, to) }
            ActionRow(
                title = actionSummary(chosen.action, chosen.label),
                detail = (chosen.action as? TapAction.HaAction)?.entity,
                onClick = { callbacks.onPickAction(ActionSlot(itemIndex, index)) },
                onRemove = { callbacks.onRemoveAction(ActionSlot(itemIndex, index)) },
                modifier = Modifier
                    .reorderable(
                        state = reorder,
                        group = itemIndex,
                        index = index,
                        count = item.actions.size,
                        onDrop = move,
                    )
                    .semantics {
                        customActions = buildList {
                            if (index > 0) {
                                add(CustomAccessibilityAction(moveUp) { move(index, index - 1); true })
                            }
                            if (index < item.actions.lastIndex) {
                                add(CustomAccessibilityAction(moveDown) { move(index, index + 1); true })
                            }
                        }
                    },
            )
        }
        ActionRow(
            title = stringResource(
                if (item.isFilled) R.string.builder_action_add else R.string.builder_action_empty,
            ),

            detail = if (item.isFilled) null else stringResource(R.string.builder_action_empty_detail),
            onClick = { callbacks.onPickAction(ActionSlot(itemIndex, item.actions.size)) },
        )
        if (item.actions.size > 1) {
            Note(
                text = stringResource(R.string.builder_action_order_note),
                modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
            )
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onRemove: (() -> Unit)? = null,
) {
    TonalRow(onClick = onClick, modifier = modifier) {
        RowText(title = title, modifier = Modifier.weight(1f), detail = detail)
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                GlyphIcon(
                    name = GLYPH_REMOVE,
                    contentDescription = stringResource(R.string.builder_action_remove),
                    tint = LocalContentColor.current,
                )
            }
        } else {
            GlyphIcon(name = GLYPH_CHEVRON, tint = LocalContentColor.current)
        }
    }
}

@Composable
internal fun IconRow(
    item: TemplateItemDraft,
    launcherApps: LauncherAppsRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TonalRow(onClick = onClick, modifier = modifier) {
        ScenarioItemIcon(
            glyph = previewGlyphName(item),
            iconPackage = previewIconPackage(item),
            launcherApps = launcherApps,
        )
        RowText(
            title = iconSummary(item),
            modifier = Modifier.weight(1f),
            detail = stringResource(
                if (item.icon == null) R.string.builder_icon_from_action else R.string.builder_icon_custom,
            ),
        )
        GlyphIcon(name = GLYPH_CHEVRON, tint = LocalContentColor.current)
    }
}

@Composable
internal fun AppWidgetForm(
    draft: ContentDraft,
    callbacks: ContentStepCallbacks,
    modifier: Modifier = Modifier,
) {
    val widget = draft.widget
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        TonalRow(onClick = callbacks.onPickWidget) {
            RowText(
                title = stringResource(
                    if (widget == null) R.string.builder_widget_pick else R.string.builder_widget_change,
                ),
                modifier = Modifier.weight(1f),
                detail = widget?.let { stringResource(R.string.builder_summary_value, it.appLabel, it.label) },
            )
            GlyphIcon(name = GLYPH_CHEVRON, tint = LocalContentColor.current)
        }
        if (widget?.needsConfiguration == true) {
            TonalRow(onClick = callbacks.onConfigureWidget) {
                RowText(
                    title = stringResource(R.string.builder_widget_configure),
                    modifier = Modifier.weight(1f),
                    detail = stringResource(R.string.builder_widget_configure_detail, widget.appLabel),
                )
                GlyphIcon(name = GLYPH_CHEVRON, tint = LocalContentColor.current)
            }
        }
    }
}

private const val GLYPH_REMOVE = "close"
