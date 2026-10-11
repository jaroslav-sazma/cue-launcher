package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.cue.launcher.R
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.designsystem.GLYPH_CHEVRON
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.Tokens
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.RowSwitch
import app.cue.launcher.designsystem.RowText
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.SheetFrame
import app.cue.launcher.designsystem.SheetHeader
import app.cue.launcher.designsystem.SheetConfirmButton
import app.cue.launcher.designsystem.SheetSecondaryButton
import app.cue.launcher.designsystem.Message

@Composable
internal fun ActionChoiceSheetContent(
    onPick: (ActionChoice) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetFrame(
        header = {
            SheetHeader(title = stringResource(R.string.builder_action_section), onClose = onClose)
        },
        modifier = modifier,
    ) {
        ACTION_CATALOG.forEach { choice ->
            TonalRow(onClick = { onPick(choice) }) {
                RowText(
                    title = actionChoiceLabel(choice),
                    modifier = Modifier.weight(1f),
                    detail = actionChoiceDetail(choice),
                )
                GlyphIcon(
                    name = GLYPH_CHEVRON,
                    size = Tokens.Settings.glyph,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
internal fun HaActionSheetContent(
    initial: TapAction.HaAction?,
    ha: HaAccess,
    onConfirm: (TapAction.HaAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var entity by remember(initial) { mutableStateOf(initial?.entity.orEmpty()) }
    var service by remember(initial) { mutableStateOf(initial?.service.orEmpty()) }
    var data by remember(initial) { mutableStateOf(haDataText(initial?.data)) }
    var confirm by remember(initial) { mutableStateOf(initial?.confirm == true) }
    var stateEntity by remember(initial) { mutableStateOf(initial?.stateEntity.orEmpty()) }
    val choices by rememberHaChoices(ha)

    val run = remember(entity, service, data) { HaTestRun() }
    val scope = rememberCoroutineScope()

    val error = haActionError(service, data, stateEntity)
    val action = haActionOf(entity, service, data, confirm, stateEntity)

    SheetFrame(
        header = {
            SheetHeader(
                title = stringResource(R.string.action_ha_title),
                onClose = onClose,
                onBack = onBack,
                backDescription = R.string.action_sheet_back,
            )
        },
        modifier = modifier,
        footer = {
            HaSheetFooter(
                error = error,
                test = run.test,
                onConfigure = ha.onConfigure,
                onUse = action?.let { usable -> { onConfirm(usable) } },
            )
        },
    ) {
        HaActionFields(
            entity = entity,
            service = service,
            data = data,
            entities = choices.entities,
            services = choices.services,
            onEntity = { entity = it },
            onService = { service = it },
            onData = { data = it },
            stateEntity = stateEntity,
            onStateEntity = { stateEntity = it },
        )
        HaConfirmRow(confirm = confirm, onChange = { confirm = it })
        HaTestRow(
            state = run.test,
            millis = run.millis,
            detail = run.detail,
            enabled = action != null,
            onTest = {
                action?.let { tested ->
                    run.test = HaTestState.RUNNING
                    scope.launch { run.finish(ha.tester.test(tested)) }
                }
            },
        )
    }
}

private class HaTestRun {

    var test by mutableStateOf(HaTestState.NOT_RUN)
    var millis by mutableLongStateOf(0L)
    var detail by mutableStateOf<String?>(null)

    fun finish(result: HaTestResult) {
        millis = (result as? HaTestResult.Ok)?.millis ?: 0L
        detail = (result as? HaTestResult.Failed)?.detail
        test = haTestStateOf(result)
    }
}

@Composable
private fun ColumnScope.HaSheetFooter(
    error: ActionFormError?,
    test: HaTestState,
    onConfigure: (() -> Unit)?,
    onUse: (() -> Unit)?,
) {
    error?.let { Message(text = actionFormErrorText(it), modifier = Modifier) }
    if (test == HaTestState.NOT_CONFIGURED && onConfigure != null) {
        SheetSecondaryButton(
            label = stringResource(R.string.action_ha_configure),
            onClick = onConfigure,
        )
    }
    SheetConfirmButton(label = stringResource(R.string.action_use), onClick = onUse)
}

@Suppress("LongParameterList")
@Composable
private fun HaActionFields(
    entity: String,
    service: String,
    data: String,
    entities: HaEntityChoices,
    services: HaServiceChoices,
    onEntity: (String) -> Unit,
    onService: (String) -> Unit,
    onData: (String) -> Unit,
    stateEntity: String,
    onStateEntity: (String) -> Unit,
) {
    val relevantRows = remember(services, entities, entity) { services.rowsFor(entity, entities) }
    val entitySuggestions = remember(entities, entity) { haSuggestions(entities.rows, entity, HA_SUGGESTIONS) }
    val serviceSuggestions = remember(relevantRows, service) { haSuggestions(relevantRows, service, HA_SUGGESTIONS) }

    SectionLabel(title = stringResource(R.string.action_ha_entity))
    HaSuggestionField(
        value = entity,
        onValueChange = onEntity,
        label = stringResource(R.string.action_ha_entity_hint),
        suggestions = entitySuggestions,
        modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding),
    )
    val listed = entities.entities.size
    if (entity.isBlank() && listed > 0) {
        Note(text = pluralStringResource(R.plurals.action_ha_entity_inventory, listed, listed))
    }
    SectionLabel(title = stringResource(R.string.action_ha_service))
    HaSuggestionField(
        value = service,
        onValueChange = onService,
        label = stringResource(R.string.action_ha_service_hint),
        suggestions = serviceSuggestions,
        modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding),
    )
    SectionLabel(title = stringResource(R.string.action_ha_data))
    HaField(value = data, hint = R.string.action_ha_data_hint, onChange = onData)
    HaStateEntityField(value = stateEntity, entities = entities, onChange = onStateEntity)
}

@Composable
private fun HaStateEntityField(value: String, entities: HaEntityChoices, onChange: (String) -> Unit) {
    val suggestions = remember(entities, value) { haSuggestions(entities.rows, value, HA_SUGGESTIONS) }

    SectionLabel(title = stringResource(R.string.action_ha_state_entity))
    HaSuggestionField(
        value = value,
        onValueChange = onChange,
        label = stringResource(R.string.action_ha_state_entity_hint),
        suggestions = suggestions,
        modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding),
    )
    Note(text = stringResource(R.string.action_ha_state_entity_note))
}

// A menu item is a full row; past this many the list reads as a wall, and typing narrows it faster.
private const val HA_SUGGESTIONS = 50

@Composable
private fun HaConfirmRow(
    confirm: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.action_ha_confirm)
    TonalRow(onClick = { onChange(!confirm) }, modifier = modifier) {
        RowText(
            title = label,
            modifier = Modifier.weight(1f),
            detail = stringResource(R.string.action_ha_confirm_detail),
        )
        RowSwitch(
            checked = confirm,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun HaTestRow(
    state: HaTestState,
    millis: Long,
    detail: String?,
    enabled: Boolean,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TonalRow(onClick = onTest, modifier = modifier, enabled = enabled) {
        RowText(
            title = stringResource(R.string.action_ha_test),
            modifier = Modifier.weight(1f),
            detail = haTestText(state, millis, detail),
        )
        GlyphIcon(
            name = GLYPH_CHEVRON,
            size = Tokens.Settings.glyph,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun HaField(value: String, hint: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
        label = { Text(text = stringResource(hint)) },
        singleLine = true,
    )
}

private fun haDataText(data: JsonObject?): String =
    if (data == null || data.isEmpty()) "" else data.toString()

@Composable
internal fun UriActionSheetContent(
    initial: String,
    onConfirm: (TapAction.DeepLink) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var uri by remember(initial) { mutableStateOf(initial) }
    val error = uriActionError(uri)

    SheetFrame(
        header = {
            SheetHeader(
                title = stringResource(R.string.action_choice_uri),
                onClose = onClose,
                onBack = onBack,
                backDescription = R.string.action_sheet_back,
            )
        },
        modifier = modifier,
        footer = {
            error?.takeIf { it != ActionFormError.URI_EMPTY }?.let {
                Message(text = actionFormErrorText(it), modifier = Modifier)
            }
            SheetConfirmButton(
                label = stringResource(R.string.action_use),
                onClick = if (error == null) {
                    { uriActionOf(uri)?.let(onConfirm) }
                } else {
                    null
                },
            )
        },
    ) {
        OutlinedTextField(
            value = uri,
            onValueChange = { uri = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
            label = { Text(text = stringResource(R.string.action_uri_label)) },
            singleLine = true,
        )
        Note(text = stringResource(R.string.action_uri_hint))
    }
}

