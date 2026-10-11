package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.InfoCard
import app.cue.launcher.designsystem.ChoiceCard

@Composable
internal fun TileTypeStep(
    selected: TileSize?,
    onSelect: (TileSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        TILE_TYPE_CATALOG.forEach { size ->
            TileTypeRow(
                size = size,
                selected = size == selected,
                onSelect = { onSelect(size) },
            )
        }
        SectionLabel(title = stringResource(R.string.builder_where_section))
        InfoCard(text = stringResource(R.string.builder_where_note))
    }
}

@Composable
private fun TileTypeRow(
    size: TileSize,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val available = tileTypeAvailable(size)
    ChoiceCard(
        selected = selected,
        onSelect = onSelect,
        modifier = if (available) modifier else modifier.alpha(Tokens.Scenarios.DISABLED_ALPHA),
        enabled = available,
    ) {
        TileTypeThumb(size = size, selected = selected)
        RowText(
            title = tileTypeTitle(size),
            modifier = Modifier.weight(1f),
            detail = tileTypeDetail(size),
        )

        if (available) RadioButton(selected = selected, onClick = onSelect)
    }
}

@Composable
private fun TileTypeThumb(size: TileSize, selected: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier.width(Tokens.Builder.thumbWidth),
        contentAlignment = Alignment.Center,
    ) {

        Surface(
            modifier = Modifier
                .width(Tokens.Builder.thumbWidth * tileTypeThumbScale(size))
                .aspectRatio(tileTypeAspect(size)),
            shape = RoundedCornerShape(Tokens.Builder.thumbRadius),
            color = if (selected) scheme.primary else scheme.surfaceContainerHighest,
            contentColor = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {

                if (tileTypeThumbScale(size) > SMALL_THUMB_SCALE) {
                    Text(text = tileSizeBadge(size), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private const val SMALL_THUMB_SCALE = 0.6f
