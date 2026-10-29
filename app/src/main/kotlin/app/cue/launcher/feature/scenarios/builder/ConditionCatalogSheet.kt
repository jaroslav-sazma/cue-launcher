package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.ConditionType
import app.cue.launcher.designsystem.GLYPH_CHEVRON
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.rememberSearchScrollState
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.LeadingIconBox
import app.cue.launcher.designsystem.SheetFrame
import app.cue.launcher.designsystem.SheetHeader
import app.cue.launcher.designsystem.SheetSearchField
import app.cue.launcher.designsystem.SheetBottomFade
import app.cue.launcher.designsystem.supportingInk
import app.cue.launcher.designsystem.supportingStyle

@Composable
internal fun ConditionCatalog(
    draft: ScenarioDraft,
    replacing: Boolean,
    onReplacingChange: (Boolean) -> Unit,
    onPick: (ConditionType) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    bridgePaired: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    val entries = rememberCatalogEntries(bridgePaired)
    val matched = remember(entries, query) { searchCatalog(entries, query) }
    val sections = remember(matched) { catalogSections(matched) }

    Box(modifier = modifier.fillMaxWidth()) {
        SheetFrame(
            header = {
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap)) {
                    SheetHeader(
                        title = stringResource(R.string.builder_catalog_title),
                        onClose = onClose,
                    )
                    SheetSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.builder_catalog_search),
                    )
                }
            },
            scrollState = rememberSearchScrollState(matched),
            gap = Tokens.Settings.rowGap,
        ) {
            if (sections.isEmpty()) {
                Text(
                    text = stringResource(R.string.builder_catalog_no_match),
                    modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
                    style = supportingStyle,
                    color = supportingInk(),
                )
            }
            sections.forEach { section ->
                SectionLabel(title = catalogSectionTitle(section.section))
                section.types.forEachIndexed { index, type ->
                    CatalogRow(
                        type = type,
                        availability = if (replacing) {
                            CatalogAvailability.AVAILABLE
                        } else {
                            catalogAvailability(draft, type)
                        },

                        showReason = index == 0,
                        onPick = { onPick(type) },
                    )
                }
            }
            if (!draft.acceptsInstant && !replacing) {
                ReplaceOffer(onReplace = { onReplacingChange(true) })
            }
        }
        SheetBottomFade(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun CatalogRow(
    type: ConditionType,
    availability: CatalogAvailability,
    showReason: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocked = availability == CatalogAvailability.BLOCKED_BY_INSTANT
    val reason = stringResource(R.string.builder_catalog_blocked)
    TonalRow(
        onClick = onPick,
        modifier = modifier.alpha(if (blocked) Tokens.Scenarios.DISABLED_ALPHA else 1f),
        enabled = !blocked,
    ) {
        LeadingIconBox(glyph = conditionTypeGlyph(type), enabled = !blocked)
        RowText(
            title = conditionTypeLabel(type),
            modifier = Modifier.weight(1f),
            detail = if (blocked && showReason) reason else conditionTypeExamples(type),
        )
        if (!blocked) {
            GlyphIcon(
                name = GLYPH_CHEVRON,
                size = Tokens.Settings.glyph,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun ReplaceOffer(onReplace: () -> Unit, modifier: Modifier = Modifier) {
    TonalRow(onClick = onReplace, modifier = modifier) {
        RowText(
            title = stringResource(R.string.builder_catalog_replace),
            modifier = Modifier.weight(1f),
        )
        GlyphIcon(
            name = GLYPH_CHEVRON,
            size = Tokens.Settings.glyph,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@Suppress("CyclomaticComplexMethod")
private fun conditionTypeGlyph(type: ConditionType): String = when (type) {
    ConditionType.TIME_WINDOW -> "clock-outline"
    ConditionType.BT_CONNECTED -> "bluetooth"
    ConditionType.WIFI -> "wifi"
    ConditionType.CHARGING -> "flash"
    ConditionType.AUDIO_OUTPUT -> "headphones"
    ConditionType.MEDIA_PLAYING -> "music"
    ConditionType.RINGER_MODE -> "volume-high"
    ConditionType.CALENDAR_EVENT -> "calendar"
    ConditionType.HA_STATE -> "home-automation"
    ConditionType.MAC_NEARBY -> "laptop"
    ConditionType.MAC_LOCKED -> "lock"
    ConditionType.TIME_INSTANT, ConditionType.ALARM -> "timer-outline"
    ConditionType.BT_DISCONNECTED -> "bluetooth-off"
    ConditionType.NOTIFICATION -> "bell-outline"
    ConditionType.HA_EVENT -> "home-assistant"
    ConditionType.NOTIFICATION_ENDED -> "bell-off-outline"
    ConditionType.PLACE_SAVED -> "map-marker-check"
}

