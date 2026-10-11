package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioRepository
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.canPin
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Note

@Composable
internal fun PriorityStep(
    draft: ScenarioDraft,
    name: String,
    scenarios: ScenarioRepository,
    onChange: (String) -> Unit,
    onPin: (Int?) -> Unit,
    modifier: Modifier = Modifier,

    excludeId: String? = null,
) {

    val all by remember(scenarios) { scenarios.observeScenarios() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val size = draft.size ?: TileSize.GRID_2X2
    val peers = remember(all, size, draft.priorityText, draft.fallback, name, excludeId) {
        priorityPeers(
            scenarios = all,
            size = size,
            excludeId = excludeId,
            draft = PriorityPeer(
                name = name,

                priority = parsePriority(draft.priorityText) ?: 0,
                fallback = draft.fallback,
                isDraft = true,
            ),
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.builder_priority_section))
        OutlinedTextField(
            value = draft.priorityText,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        Note(
            text = stringResource(R.string.builder_priority_note),
            modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
        )
        PositionSection(
            size = size,
            scenarios = all,
            selected = draft.pinnedPosition,
            excludeId = excludeId,
            onPin = onPin,
        )
        SectionLabel(title = tileSizeGroupTitle(size))
        peers.forEach { peer -> PeerRow(peer = peer) }
        Note(
            text = stringResource(R.string.builder_priority_tiebreak),
            modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
        )
    }
}

@Composable
private fun PositionSection(
    size: TileSize,
    scenarios: List<Scenario>,
    selected: Int?,
    excludeId: String?,
    onPin: (Int?) -> Unit,
) {
    if (!canPin(size)) return

    val holders = remember(scenarios, size, excludeId) { pinHolders(scenarios, size, excludeId) }

    SectionLabel(title = stringResource(R.string.builder_position_section))
    ConnectedChoiceRow(
        options = listOf(
            ConnectedChoice(
                label = stringResource(R.string.builder_position_floating),
                selected = selected == null,
                onSelect = { onPin(null) },
            ),
            ConnectedChoice(
                label = stringResource(R.string.builder_position_fixed),
                selected = selected != null,
                onSelect = { defaultPin(size, holders)?.let(onPin) },
            ),
        ),
    )
    if (selected != null) {
        PinSlotRow(slots = pinSlots(size, selected, holders), onPick = onPin)
        holders[selected]?.takeIf { it.isNotEmpty() }?.let { shared ->
            Note(text = stringResource(R.string.builder_position_shared, selected + 1, shared.joinToString()))
        }
    }
    Note(
        text = stringResource(
            if (selected == null) R.string.builder_position_note_floating else R.string.builder_position_note_fixed,
        ),
    )
}

@Composable
private fun PinSlotRow(slots: List<PinSlot>, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Builder.pinSlotGap),
    ) {
        slots.forEach { slot ->
            PinSlotCell(slot = slot, onPick = onPick, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PinSlotCell(slot: PinSlot, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        slot.selected -> scheme.primary
        !slot.pinnable -> scheme.surfaceContainerLow
        slot.shared -> scheme.secondaryContainer
        else -> scheme.surfaceContainerHigh
    }
    val ink = when {
        slot.selected -> scheme.onPrimary
        !slot.pinnable -> scheme.outline
        slot.shared -> scheme.onSecondaryContainer
        else -> scheme.onSurfaceVariant
    }
    val label = when {
        !slot.pinnable -> stringResource(R.string.builder_position_overflow)
        slot.shared -> stringResource(
            R.string.builder_position_slot_shared,
            slot.position + 1,
            slot.holders.joinToString(),
        )

        else -> stringResource(R.string.builder_position_slot, slot.position + 1)
    }
    Surface(
        onClick = { onPick(slot.position) },
        modifier = modifier
            .height(Tokens.Builder.pinSlotHeight)
            .semantics { contentDescription = label },
        enabled = slot.pinnable,
        shape = RoundedCornerShape(Tokens.Builder.pinSlotRadius),
        color = container,
        contentColor = ink,
    ) {
        // The cell already says what it is; without this the reader adds the bare number after it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            if (slot.pinnable) {
                Text(text = (slot.position + 1).toString(), style = MaterialTheme.typography.labelLarge)
            } else {
                GlyphIcon(
                    name = GLYPH_OVERFLOW,
                    size = Tokens.Settings.glyph,
                    tint = LocalContentColor.current,
                )
            }
        }
    }
}

@Composable
private fun PeerRow(peer: PriorityPeer, modifier: Modifier = Modifier) {
    val detail = when {
        peer.isDraft -> stringResource(R.string.builder_priority_this)
        peer.fallback -> stringResource(R.string.builder_priority_fallback)
        else -> null
    }
    TonalRow(
        onClick = null,
        modifier = modifier.alpha(if (peer.fallback) Tokens.Builder.BEHIND_ALPHA else 1f),
        selected = peer.isDraft,
    ) {
        RowText(title = peer.name, modifier = Modifier.weight(1f), detail = detail)
        PeerBadge(value = peer.priority, highlighted = peer.isDraft)
    }
}

@Composable
private fun PeerBadge(value: Int, highlighted: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Tokens.Scenarios.badgeRadius),
        color = if (highlighted) scheme.surface else scheme.surfaceContainer,
        contentColor = if (highlighted) scheme.primary else scheme.onSurfaceVariant,
    ) {
        Text(
            text = value.toString(),
            modifier = Modifier.padding(
                horizontal = Tokens.Scenarios.badgePadding,
                vertical = Tokens.Settings.rowGap,
            ),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private const val GLYPH_OVERFLOW = "layers-triple-outline"
