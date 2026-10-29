package app.cue.launcher.feature.scenarios.builder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioFormat
import app.cue.launcher.data.scenario.ScenarioRepository
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.widgets.WidgetPlacement
import app.cue.launcher.designsystem.Flash
import app.cue.launcher.designsystem.FlashHost
import app.cue.launcher.designsystem.FlashState
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.rememberFlashState
import app.cue.launcher.engine.SystemState
import app.cue.launcher.engine.SystemStateSource
import app.cue.launcher.feature.widgets.WidgetHosting
import app.cue.launcher.feature.widgets.rememberBentoSlotPx
import app.cue.launcher.system.AppActionsRepository
import app.cue.launcher.system.InstalledApp
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.widget.AvailableWidget
import app.cue.launcher.system.widget.WidgetBindOutcome
import app.cue.launcher.system.widget.widgetFitsSlot
import kotlinx.coroutines.launch
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Note

@Composable
fun ScenarioBuilderScreen(
    systemState: SystemStateSource,
    scenarios: ScenarioRepository,
    launcherApps: LauncherAppsRepository,
    appActions: AppActionsRepository,
    widgets: WidgetHosting,
    onClose: () -> Unit,

    onSaved: (Scenario) -> Unit,
    modifier: Modifier = Modifier,

    ha: HaAccess = HaAccess.NOT_CONFIGURED,
    onConfigureBridge: (() -> Unit)? = null,
    initial: Scenario? = null,
    initialStep: Int = FIRST_STEP,
) {
    val screen = rememberSaveable(saver = BuilderState.Saver) { prefilledBuilder(initial, initialStep) }
    val permissions = rememberRequirementResolver { screen.denied = it.denied }
    val flash = rememberFlashState()

    val state by remember(systemState, permissions.tick) { systemState.observeState() }
        .collectAsStateWithLifecycle(initialValue = SystemState())
    val suggestion = nameSuggestion(screen.draft)
    val name = draftName(screen.draft, suggestion)
    val onIncomplete = rememberStepFlash(step = screen.step, flash = flash)
    val save = rememberSaveAction(screen, scenarios, name, onIncomplete, onSaved)

    CollectWidgetBind(screen = screen, widgets = widgets, flash = flash)

    var leaving by rememberSaveable { mutableStateOf(false) }
    val back = { if (screen.canGoBack) screen.back() else if (screen.pristine) onClose() else leaving = true }

    // Left disabled while there is nothing to lose, so the settings' own predictive back closes it.
    BackHandler(enabled = screen.canGoBack || !screen.pristine, onBack = back)

    // The window does not resize for the keyboard (edge-to-edge), so without this the step body
    // keeps its full height: a field near its end stays under the keyboard with no scroll left to
    // reach it, and the bottom bar goes with it.
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        WizardTopBar(
            title = if (screen.step == FIRST_STEP && screen.draft.name.isBlank()) {
                stringResource(R.string.builder_title)
            } else {
                name
            },
            step = screen.step,
            closes = !screen.canGoBack,
            onBack = back,
        )
        WizardProgress(step = screen.step, label = stringResource(stepLabel(screen.step)))
        Box(modifier = Modifier.weight(1f)) {
            StepBody(
                screen = screen,
                state = state,
                suggestion = suggestion,
                scenarios = scenarios,
                launcherApps = launcherApps,
                widgets = widgets,
                flash = flash,
                modifier = Modifier.fillMaxSize(),
            )
            FlashHost(state = flash, modifier = Modifier.align(Alignment.BottomCenter))
        }

        WizardFooter(screen = screen, onSave = save)
    }

    BuilderSheetHost(
        screen = screen,
        state = state,
        permissions = permissions,
        launcherApps = launcherApps,
        appActions = appActions,
        widgets = widgets,
        ha = ha,
        onConfigureBridge = onConfigureBridge,
    )

    if (leaving) WizardDiscardDialog(onDismiss = { leaving = false }, onDiscard = onClose)
}

// The id stays fresh even when prefilled: saving must add a scenario, never replace the one whose id
// the prefill happened to carry.
internal fun prefilledBuilder(initial: Scenario?, initialStep: Int): BuilderState =
    if (initial == null) {
        BuilderState()
    } else {
        BuilderState(draft = scenarioDraftOf(initial), step = initialStep.coerceIn(FIRST_STEP, SUMMARY_STEP))
    }

// A new step dismisses the flash of the one before, so the flash naming a reopened step is raised only
// once that step is on screen.
@Composable
private fun rememberStepFlash(step: Int, flash: FlashState): (reopened: Int) -> Unit {
    var reopened by remember { mutableStateOf<Int?>(null) }
    val resources = LocalResources.current
    LaunchedEffect(step) {
        val incomplete = reopened
        reopened = null
        if (incomplete == null) {
            flash.dismiss()
        } else {
            val label = resources.getString(stepLabel(incomplete))
            flash.show(Flash(resources.getString(R.string.builder_save_incomplete, label), error = true))
        }
    }
    return { reopened = it }
}

@Composable
private fun rememberSaveAction(
    screen: BuilderState,
    scenarios: ScenarioRepository,
    name: String,
    onIncomplete: (step: Int) -> Unit,
    onSaved: (Scenario) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        val scenario = scenarioOf(screen.draft, screen.id, name)
        if (scenario == null) {
            screen.incompleteStep?.let { step ->
                screen.editFromSummary(step)
                onIncomplete(step)
            }
        } else {
            scope.launch {
                scenarios.save(scenario)
                onSaved(scenario)
            }
        }
    }
}

@Composable
private fun StepBody(
    screen: BuilderState,
    state: SystemState,
    suggestion: String,
    scenarios: ScenarioRepository,
    launcherApps: LauncherAppsRepository,
    widgets: WidgetHosting,
    flash: FlashState,
    modifier: Modifier = Modifier,
) {
    val name = draftName(screen.draft, suggestion)
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())

            .padding(bottom = Tokens.Builder.contentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        when (screen.step) {
            FIRST_STEP -> ConditionStepBody(screen, state, suggestion)
            TYPE_STEP -> TileTypeStep(
                selected = screen.draft.size,
                onSelect = { screen.draft = screen.draft.withSize(it) },
            )

            CONTENT_STEP -> ContentStep(
                size = screen.draft.size ?: TileSize.GRID_2X2,
                draft = screen.draft.content,
                scenarioName = name,
                launcherApps = launcherApps,
                widgets = widgets,
                callbacks = rememberContentCallbacks(screen, widgets, flash),
            )

            PRIORITY_STEP -> PriorityStep(
                draft = screen.draft,
                name = name,
                scenarios = scenarios,
                onChange = { screen.draft = screen.draft.copy(priorityText = it) },
                onPin = { screen.draft = screen.draft.copy(pinnedPosition = it) },
            )

            else -> SummaryStep(
                draft = screen.draft,
                name = name,
                launcherApps = launcherApps,
                widgets = widgets,
                onEditStep = screen::editFromSummary,
                onEnabled = { screen.draft = screen.draft.copy(enabled = it) },
            )
        }
    }
}

@Composable
internal fun rememberContentCallbacks(
    screen: BuilderState,
    widgets: WidgetHosting,
    flash: FlashState,
): ContentStepCallbacks {
    val scope = rememberCoroutineScope()
    val slot = rememberBentoSlotPx(screen.draft.size ?: TileSize.GRID_2X2)
    return remember(screen, widgets, flash, scope, slot) {
        ContentStepCallbacks(
            onChange = { content -> screen.draft = screen.draft.copy(content = content) },
            onPickWidget = {
                val content = screen.draft.content
                val suggested = content.suggestedWidget.takeIf { content.widget == null }
                if (suggested == null) {
                    screen.show(BuilderSheet.WidgetPicker)
                } else {
                    scope.launch {
                        // The picker's own fit rule: one it would not list for this tile is not bound either.
                        val widget = suggestedWidgetIn(widgets.available(), suggested)
                            ?.takeIf { widgetFitsSlot(it.size, slot.width, slot.height) }
                        screen.show(widget?.let(BuilderSheet::WidgetBind) ?: BuilderSheet.WidgetPicker)
                    }
                }
            },
            onConfigureWidget = {
                val appWidgetId = screen.draft.content.widget?.appWidgetId
                if (appWidgetId == null || !widgets.configure(appWidgetId)) {
                    flash.show(R.string.builder_widget_configure_missing, error = true)
                }
            },
            onPickAction = { slot -> screen.show(BuilderSheet.ActionPicker(slot)) },
            onRemoveAction = screen::removeAction,
            onMoveAction = screen::moveAction,
            onPickIcon = { index -> screen.show(BuilderSheet.IconPicker(index)) },
        )
    }
}

@Composable
private fun ConditionStepBody(screen: BuilderState, state: SystemState, suggestion: String) {
    ConditionStep(
        draft = screen.draft,
        state = state,
        onAdd = { screen.show(BuilderSheet.Catalog()) },
        onEdit = { index ->
            screen.show(BuilderSheet.Settings(screen.draft.conditions[index], index))
        },
        onDelete = { index -> screen.draft = screen.draft.withoutConditionAt(index) },
        onFallback = {
            screen.draft = if (screen.draft.fallback) {
                screen.draft.copy(fallback = false)
            } else {
                screen.draft.asFallback()
            }
        },
        onMatch = { screen.draft = screen.draft.withMatch(it) },
    )
    if (screen.draft.canContinue) {
        NameSection(
            value = screen.draft.name,
            placeholder = suggestion,
            onChange = { screen.draft = screen.draft.copy(name = it) },
        )
    }
}

@Composable
internal fun CollectWidgetBind(screen: BuilderState, widgets: WidgetHosting, flash: FlashState) {
    LaunchedEffect(widgets, screen) {
        widgets.outcomes.collect { result ->
            if (result.placement != WidgetPlacement.SCENARIO) return@collect
            when (result.outcome) {
                WidgetBindOutcome.ADDED -> {
                    screen.draft.content.widget?.appWidgetId
                        ?.takeIf { it != result.appWidgetId }
                        ?.let(widgets::unbind)
                    screen.draft = screen.draft.copy(
                        content = screen.draft.content.copy(widget = result.widget.bound(result.appWidgetId)),
                    )
                }

                WidgetBindOutcome.FAILED -> flash.show(R.string.widget_bind_failed, error = true)

                WidgetBindOutcome.CANCELED -> Unit
            }
        }
    }
}

internal fun AvailableWidget.bound(appWidgetId: Int) = BoundWidget(
    provider = provider,
    appWidgetId = appWidgetId,
    label = label,
    appLabel = appLabel,
    needsConfiguration = needsConfiguration,
)

@Composable
private fun NameSection(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.builder_section_name))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Settings.surfacePadding),
            placeholder = { Text(text = placeholder) },
            singleLine = true,
        )
        Note(
            text = stringResource(R.string.builder_name_hint),
            modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
        )
    }
}

internal sealed interface BuilderSheet {

    data class Catalog(val replacing: Boolean = false) : BuilderSheet

    data class Settings(
        val condition: Condition,
        val index: Int?,
        val diagnosis: Boolean = false,
    ) : BuilderSheet

    data object WidgetPicker : BuilderSheet

    data class WidgetBind(val widget: AvailableWidget) : BuilderSheet

    data class ActionPicker(val slot: ActionSlot) : BuilderSheet

    data class AppPicker(val request: AppPickRequest) : BuilderSheet

    data class AppActions(val slot: ActionSlot, val app: InstalledApp) : BuilderSheet

    data class SystemAction(val slot: ActionSlot) : BuilderSheet

    data class HaAction(val slot: ActionSlot) : BuilderSheet

    data class UriAction(val slot: ActionSlot) : BuilderSheet

    data class IconPicker(val item: Int) : BuilderSheet
}

internal sealed interface AppPickRequest {

    data class OpenApp(val slot: ActionSlot) : AppPickRequest

    data class InApp(val slot: ActionSlot) : AppPickRequest

    data class ForCondition(
        val condition: Condition.InstantCondition,
        val index: Int?,
        val target: AppTarget = AppTarget.CONDITION,
    ) : AppPickRequest
}

// Which package the picked app fills in: the condition's own, or that of its ending notification.
internal enum class AppTarget { CONDITION, END_EVENT }

// listSaver cannot hold a null, so an absent pin or index travels as a value neither can take.
internal const val SAVED_ABSENT = -1

internal const val FIRST_STEP = 1
internal const val TYPE_STEP = 2
internal const val CONTENT_STEP = 3
internal const val PRIORITY_STEP = 4
internal const val SUMMARY_STEP = 5

@Stable
internal class BuilderState(
    draft: ScenarioDraft = ScenarioDraft(),
    step: Int = FIRST_STEP,
    val id: String = newScenarioId(),
    returnsToSummary: Boolean = false,
    sheet: BuilderSheet? = null,
    opening: Int = 0,
) {

    var draft by mutableStateOf(draft)

    var returnsToSummary by mutableStateOf(returnsToSummary)
        private set

    private var shownStep by mutableIntStateOf(step)

    // Reaching the summary, whichever way, ends a detour started from it.
    var step: Int
        get() = shownStep
        set(value) {
            shownStep = value
            if (value == SUMMARY_STEP) returnsToSummary = false
        }

    var sheet by mutableStateOf(sheet)
        private set

    // What a sheet saves belongs to one opening of it (see ModalSheet); moving between sheets keeps
    // the opening, a sheet opened over none starts a new one.
    var opening by mutableIntStateOf(opening)
        private set

    var denied by mutableStateOf<Int?>(null)

    fun show(next: BuilderSheet?) {
        if (sheet == null && next != null) opening++
        sheet = next
        denied = null
    }

    fun setAction(slot: ActionSlot, action: TapAction, actionLabel: String?) {
        val item = draft.content.items.getOrNull(slot.item) ?: return
        val next = item.withAction(slot.action, action, actionLabel)
        draft = draft.copy(content = draft.content.withItem(slot.item, next))
    }

    fun setIcon(item: Int, icon: String?) {
        val current = draft.content.items.getOrNull(item) ?: return
        draft = draft.copy(content = draft.content.withItem(item, current.copy(icon = icon)))
    }

    fun removeAction(slot: ActionSlot) {
        val item = draft.content.items.getOrNull(slot.item) ?: return
        draft = draft.copy(content = draft.content.withItem(slot.item, item.withoutAction(slot.action)))
    }

    fun moveAction(item: Int, from: Int, to: Int) {
        val current = draft.content.items.getOrNull(item) ?: return
        draft = draft.copy(content = draft.content.withItem(item, current.withActionMoved(from, to)))
    }

    fun actionAt(slot: ActionSlot): TapAction? =
        draft.content.items.getOrNull(slot.item)?.actions?.getOrNull(slot.action)?.action

    val canAdvance: Boolean get() = isComplete(step)

    // A prefill always carries a size, so only an untouched new builder can be pristine.
    val pristine: Boolean get() = draft == ScenarioDraft()

    val canGoBack: Boolean get() = step > FIRST_STEP || returnsToSummary

    fun advance(save: () -> Unit) {
        when {
            step == SUMMARY_STEP -> save()
            returnsToSummary -> step = resumeStep
            else -> step++
        }
    }

    fun back() {
        when {
            returnsToSummary -> step = resumeStep
            step > FIRST_STEP -> step--
        }
    }

    fun editFromSummary(target: Int) {
        returnsToSummary = true
        step = target
    }

    // The summary can be reached with an earlier step undone (a new size drops the content, a pasted
    // scenario arrives without its widget), so a save from there opens this step instead.
    val incompleteStep: Int?
        get() = (FIRST_STEP until SUMMARY_STEP).firstOrNull { !isComplete(it) }

    // A change can undo a later step (a new size drops the content picked for the old one), so the way
    // back to the summary stops at the first later step that is no longer complete.
    private val resumeStep: Int
        get() = (step + 1 until SUMMARY_STEP).firstOrNull { !isComplete(it) } ?: SUMMARY_STEP

    private fun isComplete(of: Int): Boolean = when (of) {
        FIRST_STEP -> draft.canContinue
        TYPE_STEP -> draft.size != null
        CONTENT_STEP -> contentFormError(draft.content) == null
        PRIORITY_STEP -> priorityError(draft.priorityText) == null

        else -> true
    }

    companion object {
        val Saver: Saver<BuilderState, Any> = listSaver(
            save = { state ->
                listOf(
                    ScenarioFormat.encodeConditions(state.draft.conditions),
                    state.draft.fallback,
                    state.draft.name,
                    state.draft.size?.jsonName.orEmpty(),
                    encodeContentDraft(state.draft.content),
                    state.draft.priorityText,
                    state.draft.pinnedPosition ?: SAVED_ABSENT,
                    state.draft.enabled,
                    state.step,
                    state.id,
                    state.draft.match.name,
                    state.returnsToSummary,
                    savedSheet(state.sheet),
                    savedOpening(state.sheet, state.opening),
                )
            },
            restore = { saved ->
                BuilderState(
                    draft = ScenarioDraft(
                        conditions = ScenarioFormat.decodeConditions(saved[0] as String),
                        fallback = saved[1] as Boolean,
                        name = saved[2] as String,
                        size = TileSize.fromJsonName(saved[3] as String),
                        content = decodeContentDraft(saved[4] as String),
                        priorityText = saved[5] as String,
                        pinnedPosition = (saved[6] as Int).takeIf { it != SAVED_ABSENT },
                        enabled = saved[7] as Boolean,
                        match = ConditionMatch.valueOf(saved[10] as String),
                    ),
                    step = saved[8] as Int,
                    id = saved[9] as String,
                    returnsToSummary = saved[11] as Boolean,
                    sheet = restoredSheet(saved[12] as List<*>),
                    opening = saved[13] as Int,
                )
            },
        )
    }
}
