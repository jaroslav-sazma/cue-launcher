package app.cue.launcher.feature.scenarios.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.ha.HaStateRead
import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioRepository
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.engine.ActivationStore
import app.cue.launcher.engine.SystemState
import app.cue.launcher.engine.SystemStateSource
import app.cue.launcher.engine.haStateRefs
import app.cue.launcher.feature.scenarios.ScenarioNotice
import app.cue.launcher.feature.scenarios.builder.BuilderSheet
import app.cue.launcher.feature.scenarios.builder.BuilderSheetHost
import app.cue.launcher.feature.scenarios.builder.BuilderState
import app.cue.launcher.feature.scenarios.builder.CollectWidgetBind
import app.cue.launcher.feature.scenarios.builder.ContentStep
import app.cue.launcher.feature.scenarios.builder.HaAccess
import app.cue.launcher.feature.scenarios.builder.PriorityStep
import app.cue.launcher.feature.scenarios.builder.TileTypeStep
import app.cue.launcher.feature.scenarios.builder.bound
import app.cue.launcher.feature.scenarios.builder.contentFormError
import app.cue.launcher.feature.scenarios.builder.contentFormErrorText
import app.cue.launcher.feature.scenarios.builder.draftName
import app.cue.launcher.feature.scenarios.builder.editedScenario
import app.cue.launcher.feature.scenarios.builder.nameSuggestion
import app.cue.launcher.feature.scenarios.builder.rememberContentCallbacks
import app.cue.launcher.feature.scenarios.builder.rememberRequirementResolver
import app.cue.launcher.feature.scenarios.builder.scenarioDraftOf
import app.cue.launcher.feature.scenarios.diagnoseScenario
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.system.AppActionsRepository
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.unavailableSystemActions
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.DetailPaneScaffold
import app.cue.launcher.designsystem.Message
import app.cue.launcher.designsystem.Flash
import app.cue.launcher.designsystem.FlashState
import app.cue.launcher.designsystem.rememberFlashState

@Composable
internal fun ScenarioEditScreen(
    scenarioId: String,
    scenarios: ScenarioRepository,
    activations: ActivationStore,
    systemState: SystemStateSource,
    launcherApps: LauncherAppsRepository,
    appActions: AppActionsRepository,
    widgets: WidgetHosting,
    onClose: () -> Unit,

    onNotice: (ScenarioNotice) -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
    ha: HaAccess = HaAccess.NOT_CONFIGURED,
) {

    val original by produceState<Scenario?>(initialValue = null, scenarioId, scenarios) {
        value = scenarios.scenario(scenarioId)
    }
    original?.let { scenario ->
        ScenarioEditor(
            original = scenario,
            scenarios = scenarios,
            activations = activations,
            systemState = systemState,
            launcherApps = launcherApps,
            appActions = appActions,
            widgets = widgets,
            onClose = onClose,
            onNotice = onNotice,
            modifier = modifier,
            notice = notice,
            ha = ha,
        )
    }
}

@Composable
private fun ScenarioEditor(
    original: Scenario,
    scenarios: ScenarioRepository,
    activations: ActivationStore,
    systemState: SystemStateSource,
    launcherApps: LauncherAppsRepository,
    appActions: AppActionsRepository,
    widgets: WidgetHosting,
    onClose: () -> Unit,
    onNotice: (ScenarioNotice) -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
    ha: HaAccess = HaAccess.NOT_CONFIGURED,
) {
    val screen = rememberSaveable(saver = BuilderState.Saver) {
        BuilderState(draft = scenarioDraftOf(original), id = original.id)
    }
    var tileOpen by rememberSaveable { mutableStateOf(false) }
    val permissions = rememberRequirementResolver { screen.denied = it.denied }
    val flash = rememberFlashState()
    LaunchedEffect(flash) { notice?.let { flash.show(Flash(it)) } }
    val state by remember(systemState, permissions.tick) { systemState.observeState() }
        .collectAsStateWithLifecycle(initialValue = SystemState())
    val all by remember(scenarios) { scenarios.observeScenarios() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val running by remember(activations) { activations.observeActivations() }
        .collectAsStateWithLifecycle(initialValue = emptyList<Activation>())

    val suggestion = nameSuggestion(screen.draft)
    val name = draftName(screen.draft, suggestion)
    val edited = editedScenario(original, screen.draft, name)

    val haRead = rememberHaStateRead(ha, edited.content)

    CollectWidgetBind(screen = screen, widgets = widgets, flash = flash)
    ResolveWidgetLabels(screen = screen, widgets = widgets)
    AutoSave(original = original, edited = edited, scenarios = scenarios)
    BackHandler(enabled = tileOpen) { tileOpen = false }

    val actions = rememberEditActions(
        scenario = edited,
        scenarios = scenarios,
        onNotice = onNotice,
        flash = flash,
        onClose = onClose,
    )

    EditorLayout(
        screen = screen,
        original = original,
        edited = edited,
        name = name,
        suggestion = suggestion,
        state = state,
        scenarios = scenarios,
        all = all,
        running = running,
        launcherApps = launcherApps,
        widgets = widgets,
        actions = actions,
        haRead = haRead,
        flash = flash,
        tileOpen = tileOpen,
        onTileOpen = { tileOpen = it },
        onClose = onClose,
        modifier = modifier,
    )

    BuilderSheetHost(
        screen = screen,
        state = state,
        permissions = permissions,
        launcherApps = launcherApps,
        appActions = appActions,
        widgets = widgets,
        ha = ha,
    )
}

@Composable
private fun rememberHaStateRead(ha: HaAccess, content: TileContent): HaStateRead? {
    val refs = remember(content) { haStateRefs(content) }
    val read by produceState<HaStateRead?>(null, refs, ha) {
        value = if (refs.isEmpty()) null else ha.states.read(refs)
    }
    return read
}

@Composable
private fun EditorLayout(
    screen: BuilderState,
    original: Scenario,
    edited: Scenario,
    name: String,
    suggestion: String,
    state: SystemState,
    scenarios: ScenarioRepository,
    all: List<Scenario>,
    running: List<Activation>,
    launcherApps: LauncherAppsRepository,
    widgets: WidgetHosting,
    actions: EditActions,
    haRead: HaStateRead?,
    flash: FlashState,
    tileOpen: Boolean,
    onTileOpen: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailPaneScaffold(
        title = name,
        onBack = { if (tileOpen) onTileOpen(false) else onClose() },
        modifier = modifier,
        subtitle = stringResource(if (tileOpen) R.string.edit_section_tile else R.string.edit_subtitle),
        flash = flash,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
        ) {
            if (tileOpen) {
                TileAndContentBody(
                    screen = screen,
                    name = name,
                    launcherApps = launcherApps,
                    widgets = widgets,
                    flash = flash,
                )
            } else {
                EditBody(
                    screen = screen,
                    original = original,
                    edited = edited,
                    name = name,
                    suggestion = suggestion,
                    state = state,
                    scenarios = scenarios,
                    all = all,
                    running = running,
                    launcherApps = launcherApps,
                    widgets = widgets,
                    actions = actions,
                    haRead = haRead,
                    onOpenTile = { onTileOpen(true) },
                )
            }
        }
        contentFormError(screen.draft.content)?.let { error ->
            Message(text = contentFormErrorText(error, screen.draft.content, screen.draft.size ?: original.size))
        }
    }
}

@Composable
private fun EditBody(
    screen: BuilderState,
    original: Scenario,
    edited: Scenario,
    name: String,
    suggestion: String,
    state: SystemState,
    scenarios: ScenarioRepository,
    all: List<Scenario>,
    running: List<Activation>,
    launcherApps: LauncherAppsRepository,
    widgets: WidgetHosting,
    actions: EditActions,
    haRead: HaStateRead?,
    onOpenTile: () -> Unit,
) {
    val context = LocalContext.current
    val unavailable = remember(context, edited.content) { unavailableSystemActions(context) }
    val diagnosis = remember(edited, state, all, running, haRead, unavailable) {
        diagnoseScenario(
            scenario = edited,
            state = state,
            scenarios = all,
            activations = running,
            now = System.currentTimeMillis(),
            haRead = haRead,
            unavailable = unavailable,
        )
    }
    val size = screen.draft.size ?: original.size

    PreviewSection(
        screen = screen,
        size = size,
        name = name,
        launcherApps = launcherApps,
        widgets = widgets,
    )
    ConditionsSection(
        draft = screen.draft,
        diagnosis = diagnosis,
        suggestion = suggestion,
        onEdit = { index ->
            screen.show(BuilderSheet.Settings(screen.draft.conditions[index], index, diagnosis = true))
        },
        onDelete = { index -> screen.draft = screen.draft.withoutConditionAt(index) },
        onAdd = { screen.show(BuilderSheet.Catalog()) },
        onName = { screen.draft = screen.draft.copy(name = it) },
        onMatch = { screen.draft = screen.draft.withMatch(it) },
    )
    TileSection(
        draft = screen.draft,
        size = size,
        health = diagnosis.content,
        haRead = haRead,
        onOpen = onOpenTile,
    )
    PrioritySection(
        screen = screen,
        name = name,
        scenarios = scenarios,
        outrankedBy = diagnosis.outrankedBy,
    )
    StateSection(screen = screen)
    EditActionsSection(actions = actions, name = name)
}

@Composable
private fun TileAndContentBody(
    screen: BuilderState,
    name: String,
    launcherApps: LauncherAppsRepository,
    widgets: WidgetHosting,
    flash: FlashState,
) {
    SectionLabel(title = stringResource(R.string.builder_step2_label))
    TileTypeStep(
        selected = screen.draft.size,
        onSelect = { screen.draft = screen.draft.withSize(it) },
    )
    ContentStep(
        size = screen.draft.size ?: TileSize.GRID_2X2,
        draft = screen.draft.content,
        scenarioName = name,
        launcherApps = launcherApps,
        widgets = widgets,
        callbacks = rememberContentCallbacks(screen, widgets, flash),
    )
}

@Composable
private fun AutoSave(original: Scenario, edited: Scenario, scenarios: ScenarioRepository) {
    LaunchedEffect(edited) {
        if (edited != original) withContext(NonCancellable) { scenarios.save(edited) }
    }
}

@Composable
private fun ResolveWidgetLabels(screen: BuilderState, widgets: WidgetHosting) {
    LaunchedEffect(widgets, screen) {
        val bound = screen.draft.content.widget ?: return@LaunchedEffect
        val found = widgets.available().firstOrNull {
            it.provider.packageName == bound.provider.packageName &&
                it.provider.className == bound.provider.className
        } ?: return@LaunchedEffect
        screen.draft = screen.draft.copy(
            content = screen.draft.content.copy(widget = found.bound(bound.appWidgetId)),
        )
    }
}
