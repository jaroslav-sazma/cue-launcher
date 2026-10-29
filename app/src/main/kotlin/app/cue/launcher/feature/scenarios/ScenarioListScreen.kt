package app.cue.launcher.feature.scenarios

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioRepository
import app.cue.launcher.designsystem.FlashHost
import app.cue.launcher.designsystem.GLYPH_CHECK
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.SearchField
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.rememberFlashState
import app.cue.launcher.designsystem.rememberSearchListState
import app.cue.launcher.engine.SystemStateSource
import app.cue.launcher.feature.drawer.search.normalizeForSearch
import app.cue.launcher.feature.scenarios.aichat.AiChatEntries
import app.cue.launcher.feature.scenarios.aichat.AiChatPendingCard
import app.cue.launcher.feature.scenarios.aichat.NewScenarioChooser
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.designsystem.TabPaneScaffold
import app.cue.launcher.designsystem.supportingInk
import app.cue.launcher.designsystem.supportingStyle

@Composable
internal fun ScenarioListScreen(
    repository: ScenarioRepository,
    systemState: SystemStateSource,
    launcherApps: LauncherAppsRepository,
    onOpenBackups: () -> Unit,
    onHomeAssistant: () -> Unit,
    onBridge: () -> Unit,
    onOpenLicenses: () -> Unit,
    onNewScenario: () -> Unit,
    onEditScenario: (Scenario) -> Unit,
    modifier: Modifier = Modifier,

    notice: ScenarioNotice? = null,
    onNoticeConsumed: () -> Unit = {},

    // Null while the AI chat setting is off; then the button opens the wizard straight away.
    aiChat: AiChatEntries? = null,
) {
    val screen = rememberSaveable(saver = ScenarioListState.Saver) { ScenarioListState() }
    val scenarios by remember(repository) { repository.observeScenarios() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val state by remember(systemState) { systemState.observeState() }
        .collectAsStateWithLifecycle(initialValue = null)

    val sections = remember(scenarios, screen.filter, screen.query) {
        scenarioSections(filterScenarios(scenarios, screen.filter, screen.query))
    }
    val actions = rememberScenarioActions(repository, onEditScenario) { screen.notice = it }
    val flash = rememberFlashState()
    NoticeFlash(
        notice = screen.notice,
        state = state,
        flash = flash,
        onUndo = actions::undo,
        onGone = { gone -> if (screen.notice == gone) screen.notice = null },
    )

    LaunchedEffect(notice) {
        notice?.let {
            screen.notice = it
            onNoticeConsumed()
        }
    }

    TabPaneScaffold(
        title = stringResource(R.string.scenarios_title),
        modifier = modifier,
        actions = {
            ScenarioListMenu(onOpenBackups, onHomeAssistant, onBridge, onAbout = { screen.aboutVisible = true })
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ScenarioListBody(
                screen = screen,
                scenarios = scenarios,
                sections = sections,
                actions = actions,
                launcherApps = launcherApps,
                aiChat = aiChat,
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                FlashHost(state = flash)
                NewScenarioFab(onNewScenario = onNewScenario, aiChat = aiChat)
            }
        }
    }

    if (screen.aboutVisible) {
        AboutSheet(onLicenses = onOpenLicenses, onDismiss = { screen.aboutVisible = false })
    }
}

@Composable
private fun ScenarioListBody(
    screen: ScenarioListState,
    scenarios: List<Scenario>,
    sections: List<ScenarioSection>,
    actions: ScenarioActions,
    launcherApps: LauncherAppsRepository,
    aiChat: AiChatEntries?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        if (aiChat?.pending == true) {
            AiChatPendingCard(
                onPaste = aiChat.onPaste,
                onDismiss = aiChat.onDismissPending,
                modifier = Modifier.padding(bottom = Tokens.Settings.highlightPadding),
            )
        }
        ScenarioSearchField(
            query = screen.query,
            onQueryChange = { screen.query = it },
        )
        ScenarioFilterChips(
            selected = screen.filter,
            scenarios = scenarios,
            onSelect = { screen.filter = it },
        )
        ScenarioSections(
            sections = sections,
            search = screen.filter to normalizeForSearch(screen.query),
            actions = actions,
            launcherApps = launcherApps,

            highlightId = (screen.notice as? ScenarioNotice.Saved)?.scenario?.id,
            emptyText = stringResource(
                if (scenarios.isEmpty()) R.string.scenarios_empty else R.string.scenarios_no_match,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Stable
internal class ScenarioDrag(
    val state: ReorderState,
    val group: List<Scenario>,
    val key: ScenarioGroup,
    val index: Int,
)

@Stable
private class ScenarioListState(
    query: String = "",
    filter: ScenarioFilter = ScenarioFilter.ALL,
    aboutVisible: Boolean = false,
) {

    var query by mutableStateOf(query)

    var filter by mutableStateOf(filter)

    var notice by mutableStateOf<ScenarioNotice?>(null)

    var aboutVisible by mutableStateOf(aboutVisible)

    companion object {
        val Saver: Saver<ScenarioListState, Any> = listSaver<ScenarioListState, Any>(
            save = { listOf(it.query, it.filter.name, it.aboutVisible) },
            restore = {
                ScenarioListState(it[0] as String, ScenarioFilter.valueOf(it[1] as String), it[2] as Boolean)
            },
        )
    }
}

@Composable
private fun ScenarioSections(
    sections: List<ScenarioSection>,
    search: Any,
    actions: ScenarioActions,
    launcherApps: LauncherAppsRepository,
    highlightId: String?,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    if (sections.isEmpty()) {
        Text(
            text = emptyText,
            modifier = modifier.padding(horizontal = Tokens.Settings.screenPadding),
            style = supportingStyle,
            color = supportingInk(),
        )
        return
    }
    val reorder = rememberReorderState(gap = Tokens.Settings.rowGap)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = rememberSearchListState(search),

            contentPadding = PaddingValues(bottom = Tokens.Scenarios.listBottomPadding),
            verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
        ) {
            sections.forEach { section ->
                item(key = "header-${section.group}") { SectionHeader(group = section.group) }
                itemsIndexed(section.scenarios, key = { _, row -> row.id }) { index, scenario ->
                    ScenarioRow(
                        scenario = scenario,
                        actions = actions,
                        launcherApps = launcherApps,
                        highlighted = scenario.id == highlightId,
                        drag = ScenarioDrag(reorder, section.scenarios, section.group, index),
                    )
                }
            }
        }
        if (reorder.active) {
            Text(
                text = stringResource(R.string.scenarios_drag_hint),
                modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
                style = supportingStyle,
                color = supportingInk(),
            )
        }
    }
}

@Composable
private fun ScenarioListMenu(
    onImport: () -> Unit,
    onHomeAssistant: () -> Unit,
    onBridge: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { open = true }) {
            GlyphIcon(
                name = GLYPH_MENU,
                size = Tokens.Settings.glyph,
                tint = LocalContentColor.current,
                contentDescription = stringResource(R.string.scenarios_header_menu),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem(title = stringResource(R.string.scenarios_menu_import)) {
                open = false
                onImport()
            }
            MenuItem(title = stringResource(R.string.scenarios_menu_ha)) {
                open = false
                onHomeAssistant()
            }
            MenuItem(title = stringResource(R.string.scenarios_menu_bridge)) {
                open = false
                onBridge()
            }
            MenuItem(title = stringResource(R.string.scenarios_menu_about)) {
                open = false
                onAbout()
            }
        }
    }
}

@Composable
private fun ScenarioSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = stringResource(R.string.scenarios_search),
        container = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
    )
}

@Composable
private fun ScenarioFilterChips(
    selected: ScenarioFilter,
    scenarios: List<Scenario>,
    onSelect: (ScenarioFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Settings.surfacePadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Scenarios.chipGap),
    ) {
        ScenarioFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = {
                    Text(text = scenarioFilterLabel(filter, filterCount(filter, scenarios)))
                },

                leadingIcon = if (filter != selected) {
                    null
                } else {
                    {
                        GlyphIcon(
                            name = GLYPH_CHECK,
                            size = FilterChipDefaults.IconSize,
                            tint = LocalContentColor.current,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SectionHeader(group: ScenarioGroup, modifier: Modifier = Modifier) {
    Text(
        text = scenarioGroupTitle(group),
        modifier = modifier.padding(
            start = Tokens.Settings.screenPadding,
            end = Tokens.Settings.screenPadding,
            top = Tokens.Settings.sectionSpacing,
        ),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NewScenarioFab(
    onNewScenario: () -> Unit,
    aiChat: AiChatEntries?,
    modifier: Modifier = Modifier,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    if (choosing && aiChat != null) {
        NewScenarioChooser(
            onWizard = onNewScenario,
            onAiChat = aiChat.onAiChat,
            onDismiss = { choosing = false },
        )
    }
    MediumExtendedFloatingActionButton(
        onClick = { if (aiChat == null) onNewScenario() else choosing = true },
        modifier = modifier.padding(Tokens.Settings.surfacePadding),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        icon = {
            GlyphIcon(
                name = GLYPH_PLUS,
                size = Tokens.Settings.glyph,
                tint = LocalContentColor.current,
            )
        },
        text = { Text(text = stringResource(R.string.scenarios_new)) },
    )
}

private const val GLYPH_PLUS = "plus"
