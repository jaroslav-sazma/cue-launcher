package app.cue.launcher.feature.scenarios

import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.ImportPlan
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioDefect
import app.cue.launcher.data.scenario.ScenarioFormat
import app.cue.launcher.data.scenario.ScenarioRead
import app.cue.launcher.data.scenario.ScenarioRepository
import app.cue.launcher.data.scenario.importPlanOf
import app.cue.launcher.data.settings.BackupSummary
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.feature.scenarios.builder.appDetailsIntent
import app.cue.launcher.designsystem.Flash
import app.cue.launcher.designsystem.FlashState
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.system.ConfigBackup
import app.cue.launcher.system.DocumentStore
import app.cue.launcher.system.launchOrElse
import app.cue.launcher.system.launchSystemScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.Message

@Composable
fun ScenarioBackup(
    repository: ScenarioRepository,
    settings: SettingsRepository,
    backup: ConfigBackup,
    flash: FlashState,
    modifier: Modifier = Modifier,
    importUri: Uri? = null,
    onImportConsumed: () -> Unit = {},

    onEditScenario: (id: String, notice: String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberSaveable(saver = BackupState.Saver) { BackupState() }
    val scenarioFlow = remember(repository) { repository.observeScenarios() }
    val scenarios by scenarioFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val stored by remember(settings) { settings.settings }.collectAsStateWithLifecycle(initialValue = null)

    val setName = stringResource(R.string.scenarios_export_set_name)
    val setFileName = stringResource(R.string.scenarios_export_file)
    val configFileName = stringResource(R.string.backup_export_config_file)

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) state.source = uri
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_JSON),
    ) { uri ->
        val export = state.pending
        state.pending = null
        if (uri != null && export != null) {
            val snapshot = scenarios
            scope.launch {
                val written = when (export) {
                    ExportKind.CONFIG -> exportConfig(context, uri, backup, snapshot, configFileName)
                    ExportKind.SET -> exportSet(context, uri, setName, snapshot)
                }
                flash.show(Flash(written.message, error = written.failed))
                written.summary?.let { summary -> settings.setLastBackup(summary) }
            }
        }
    }

    val resolveRequirement = rememberRequirementResolver(state)

    ImportEffects(state, repository, importUri, onImportConsumed)

    BackupBody(
        state = state,
        scenarios = scenarios,
        lastBackup = stored?.lastBackup,
        onImport = {
            openDocument.launchOrElse(IMPORT_MIME_TYPES) { flash.show(R.string.document_picker_missing, error = true) }
        },
        onExport = { kind ->
            state.pending = kind
            createDocument.launchOrElse(if (kind == ExportKind.CONFIG) configFileName else setFileName) {
                state.pending = null
                flash.show(R.string.document_picker_missing, error = true)
            }
        },
        onConfirm = { plan ->
            scope.launch {
                val done = applyImport(context, repository, backup, plan)
                state.source = null
                state.required = missingRequirements(context, plan.scenarios)

                // A lone scenario opens in the editor right away, so the result goes along with it: a
                // flash here would play on a screen that is no longer there.
                val opened = plan.scenarios.singleOrNull()?.takeIf { !plan.hasConfig }
                if (opened != null) onEditScenario(opened.id, done) else flash.show(Flash(done))
            }
        },
        onResolve = resolveRequirement,
        modifier = modifier,
    )
}

private enum class ExportKind { CONFIG, SET }

@Composable
private fun rememberRequirementResolver(state: BackupState): (JitRequirement) -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val systemSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }
    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val asked = state.required.firstOrNull() as? JitRequirement.Grant
        state.required = state.required.drop(1)
        if (asked != null && results[asked.permission.decisive] != true) {
            // Same recovery as the builder resolver: a permanently refused permission returns
            // straight away, so the app's own settings page is the only way left to grant it.
            if (activity?.shouldShowRequestPermissionRationale(asked.permission.decisive) == false) {
                systemSettings.launchSystemScreen(appDetailsIntent(context.packageName), context)
            }
            // Kept on screen rather than flashed: when it opens the settings page the flash would run
            // out there, and the user would come back to a feature that is off with no reason given.
            state.denied = asked.permission.denied
        }
    }

    return { requirement ->
        state.denied = null
        when (requirement) {
            is JitRequirement.Grant -> permissionRequest.launch(requirement.permission.requested.toTypedArray())
            JitRequirement.LocationServices,
            JitRequirement.NotificationPolicy,
            JitRequirement.NotificationListener,
            -> {
                state.required = state.required.drop(1)
                requirement.settingsIntent(context)?.let { systemSettings.launchSystemScreen(it, context) }
            }
        }
    }
}

@Stable
private class BackupState(source: Uri? = null) {

    var source by mutableStateOf(source)

    var pending by mutableStateOf<ExportKind?>(null)

    var phase by mutableStateOf<ImportState?>(null)

    var required by mutableStateOf<List<JitRequirement>>(emptyList())

    @get:StringRes
    var denied by mutableStateOf<Int?>(null)

    companion object {
        val Saver: Saver<BackupState, Any> = listSaver(
            save = { listOf(it.source) },
            restore = { BackupState(it.firstOrNull()) },
        )
    }
}

private sealed interface ImportState {

    data object Reading : ImportState

    data object Unreadable : ImportState

    data class Failed(val defect: ScenarioDefect) : ImportState

    data class Preview(val plan: ImportPlan) : ImportState
}

private val IMPORT_MIME_TYPES = arrayOf(MIME_JSON, "text/plain", "application/octet-stream")

@Composable
private fun ImportEffects(
    state: BackupState,
    repository: ScenarioRepository,
    importUri: Uri?,
    onImportConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val consumed by rememberUpdatedState(onImportConsumed)

    LaunchedEffect(importUri) {
        if (importUri != null) {
            state.source = importUri
            consumed()
        }
    }

    LaunchedEffect(state.source) {
        val uri = state.source
        if (uri == null) {
            state.phase = null
            return@LaunchedEffect
        }
        state.phase = ImportState.Reading
        state.phase = readImport(context, uri, repository)
    }
}

@Composable
private fun BackupBody(
    state: BackupState,
    scenarios: List<Scenario>,
    lastBackup: BackupSummary?,
    onImport: () -> Unit,
    onExport: (ExportKind) -> Unit,
    onConfirm: (ImportPlan) -> Unit,
    onResolve: (JitRequirement) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        when (val phase = state.phase) {
            null -> BackupActions(
                scenarios = scenarios,
                lastBackup = lastBackup,
                requirement = state.required.firstOrNull(),
                denied = state.denied,
                onImport = onImport,
                onExportConfig = { onExport(ExportKind.CONFIG) },
                onExportSet = { onExport(ExportKind.SET) },
                onResolve = onResolve,
            )

            ImportState.Reading -> Note(text = stringResource(R.string.import_reading))

            ImportState.Unreadable -> ImportFailure(
                reason = stringResource(R.string.import_read_failed),
                onClose = { state.source = null },
            )

            is ImportState.Failed -> ImportFailure(
                reason = defectText(phase.defect),
                onClose = { state.source = null },
            )

            is ImportState.Preview -> ImportPreview(
                plan = phase.plan,
                onConfirm = { onConfirm(phase.plan) },
                onCancel = { state.source = null },
            )
        }
    }
}

private suspend fun readImport(
    context: Context,
    uri: Uri,
    repository: ScenarioRepository,
): ImportState {
    val text = DocumentStore.readText(context, uri) ?: return ImportState.Unreadable
    val knownIds = repository.knownIds()
    // A file of up to four megabytes from anywhere: decoding it on Main would freeze the home screen.
    return withContext(Dispatchers.Default) {
        when (val read = ScenarioFormat.read(text)) {
            is ScenarioRead.Failed -> ImportState.Failed(read.defect)

            is ScenarioRead.Ok -> ImportState.Preview(importPlanOf(read, knownIds))
        }
    }
}

private suspend fun applyImport(
    context: Context,
    repository: ScenarioRepository,
    backup: ConfigBackup,
    plan: ImportPlan,
): String {
    repository.saveAll(plan.scenarios)
    val count = plan.scenarios.size
    val scenariosDone = context.resources.getQuantityString(R.plurals.import_done, count, count)
    val config = plan.config ?: return scenariosDone
    val restore = backup.apply(config)
    val skipped = restore.skippedWidgets
    val configDone = if (skipped == 0) {
        context.getString(R.string.import_config_done)
    } else {
        context.resources.getQuantityString(R.plurals.import_config_widgets_skipped, skipped, skipped)
    }
    return "$scenariosDone $configDone"
}

@Composable
private fun ImportFailure(
    reason: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.import_title))
        Message(text = reason)
        TextButton(
            onClick = onClose,
            modifier = Modifier.padding(horizontal = Tokens.Settings.surfacePadding),
        ) {
            Text(text = stringResource(R.string.import_close))
        }
    }
}
