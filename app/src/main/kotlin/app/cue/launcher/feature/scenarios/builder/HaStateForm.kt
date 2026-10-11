package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import app.cue.launcher.R
import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.ha.haEntityIdOrNull
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.MediumOutlinedButton
import app.cue.launcher.designsystem.Message
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.engine.HaStates
import app.cue.launcher.engine.SystemState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HaStateForm(
    condition: Condition.HaState,
    ha: HaAccess,
    read: HaEntityRead?,
    onChange: (Condition.HaState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entities by rememberHaEntityChoices(ha)
    val suggestions = remember(entities, condition.entity) {
        haSuggestions(entities.rows, condition.entity, HA_STATE_SUGGESTIONS)
    }
    val choices = remember(condition.entity, read?.state, condition.states) {
        haStateChoices(condition.entity, read?.state, condition.states)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.action_ha_entity))
        HaSuggestionField(
            value = condition.entity,
            onValueChange = { onChange(condition.copy(entity = it)) },
            label = stringResource(R.string.builder_ha_state_entity_hint),
            suggestions = suggestions,
            modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding),
        )
        HaSetupNotice(probe = read?.probe, onConfigure = ha.onConfigure)
        SectionLabel(title = stringResource(R.string.builder_ha_state_which))
        if (choices.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.Settings.surfacePadding),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Scenarios.chipGap),
            ) {
                choices.forEach { state ->
                    ToggleButton(
                        checked = condition.states.any { it.equals(state, ignoreCase = true) },
                        onCheckedChange = { on ->
                            onChange(condition.copy(states = toggledHaStates(condition.states, state, on)))
                        },
                        buttonSize = ToggleButtonSize.ExtraSmall,
                    ) {
                        Text(text = state)
                    }
                }
            }
        }
        HaCustomStateField(onAdd = { state ->
            onChange(condition.copy(states = toggledHaStates(condition.states, state, on = true)))
        })
        Note(text = stringResource(R.string.builder_ha_state_note))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HaCustomStateField(onAdd: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val add = {
        if (text.isNotBlank()) onAdd(text)
        text = ""
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        label = { Text(text = stringResource(R.string.builder_ha_state_custom)) },
        trailingIcon = {
            IconButton(onClick = add, enabled = text.isNotBlank(), shapes = IconButtonDefaults.shapes()) {
                GlyphIcon(
                    name = GLYPH_ADD,
                    size = Tokens.Settings.glyph,
                    tint = LocalContentColor.current,
                    contentDescription = stringResource(R.string.builder_ha_state_add),
                )
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { add() }),
    )
}

// Settings → Home Assistant is where both are answered: the connection, and the local network
// permission a home address needs.
@Composable
private fun HaSetupNotice(probe: HaProbe?, onConfigure: (() -> Unit)?) {
    val text = when (probe) {
        HaProbe.NotConfigured -> R.string.action_ha_not_configured
        HaProbe.LocalNetworkDenied -> R.string.action_ha_local_network
        else -> return
    }
    Message(text = stringResource(text))
    if (onConfigure != null) {
        MediumOutlinedButton(
            label = stringResource(R.string.action_ha_configure),
            onClick = onConfigure,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.screenPadding),
        )
    }
}

// Read for the sheet alone: the condition being edited need not be in any saved scenario yet, so
// the state the launcher follows may not hold it. Typing pauses a moment before each read, as every
// keystroke of an entity id is a valid id of some other entity.
@Composable
internal fun rememberHaEntityRead(ha: HaAccess, condition: Condition): HaEntityRead? {
    val entity = (condition as? Condition.HaState)?.entity?.let(::haEntityIdOrNull)
    val read by produceState<HaEntityRead?>(null, ha, entity) {
        if (entity == null) return@produceState
        delay(HA_READ_PAUSE_MILLIS)
        val result = ha.states.read(setOf(entity)) ?: return@produceState
        value = HaEntityRead(entity, result.values[entity], result.probe)
    }
    return read?.takeIf { it.entity == entity }
}

internal fun SystemState.withHaRead(read: HaEntityRead?): SystemState {
    val state = read?.state ?: return this
    return copy(ha = HaStates.of(ha?.states.orEmpty() + (read.entity to state)))
}

@Composable
private fun rememberHaEntityChoices(ha: HaAccess): State<HaEntityChoices> =
    produceState(HaEntityChoices.NONE, ha) {
        val entities = ha.entities.entities()
        if (entities.isNotEmpty()) value = withContext(Dispatchers.Default) { HaEntityChoices(entities) }
    }

private const val HA_STATE_SUGGESTIONS = 50

private const val HA_READ_PAUSE_MILLIS = 400L

private const val GLYPH_ADD = "plus"
