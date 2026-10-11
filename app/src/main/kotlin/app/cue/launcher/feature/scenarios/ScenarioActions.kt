package app.cue.launcher.feature.scenarios

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioFormat
import app.cue.launcher.data.scenario.ScenarioRepository
import app.cue.launcher.data.scenario.portableScenario
import app.cue.launcher.system.DocumentStore
import app.cue.launcher.system.launchOrElse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
internal class ScenarioActions(
    private val repository: ScenarioRepository,
    private val context: Context,
    private val scope: CoroutineScope,
    private val notify: (ScenarioNotice) -> Unit,
    private val startExport: (Scenario) -> Unit,
    private val openEditor: (Scenario) -> Unit,
) {

    fun toggle(scenario: Scenario, enabled: Boolean) {
        scope.launch { repository.setEnabled(scenario.id, enabled) }
    }

    fun edit(scenario: Scenario) = openEditor(scenario)

    fun applyPriorities(updates: List<PriorityUpdate>) {
        scope.launch { updates.forEach { repository.setPriority(it.id, it.priority) } }
    }

    fun duplicate(scenario: Scenario) {
        scope.launch {
            val copy = duplicateOf(
                scenario = scenario,

                takenIds = repository.knownIds(),
                name = context.getString(R.string.scenarios_copy_name, scenario.name),
            )
            repository.save(copy)
            notify(ScenarioNotice.Saved(copy))
        }
    }

    fun export(scenario: Scenario) = startExport(scenario)

    fun delete(scenario: Scenario) {
        scope.launch {
            repository.delete(scenario.id)
            notify(ScenarioNotice.Deleted(scenario))
        }
    }

    fun undo(notice: ScenarioNotice) {
        scope.launch {
            when (notice) {
                is ScenarioNotice.Saved -> repository.delete(notice.scenario.id)
                is ScenarioNotice.Deleted -> repository.save(notice.scenario)
                else -> Unit
            }
        }
    }
}

@Composable
internal fun rememberScenarioActions(
    repository: ScenarioRepository,
    onEdit: (Scenario) -> Unit,
    notify: (ScenarioNotice) -> Unit,
): ScenarioActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exported by remember { mutableStateOf<Scenario?>(null) }
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_JSON),
    ) { uri ->
        val scenario = exported
        exported = null
        if (uri != null && scenario != null) {
            scope.launch { notify(exportScenario(context, uri, scenario)) }
        }
    }
    val fileName = stringResource(R.string.scenarios_export_scenario_file)
    val pickerMissing = stringResource(R.string.document_picker_missing)
    val editor = rememberUpdatedState(onEdit)
    return remember(repository, context, scope) {
        ScenarioActions(
            repository = repository,
            context = context,
            scope = scope,
            notify = notify,
            startExport = { scenario ->
                exported = scenario
                createDocument.launchOrElse(fileName) {
                    exported = null
                    notify(ScenarioNotice.Message(pickerMissing, failed = true))
                }
            },
            openEditor = { scenario -> editor.value(scenario) },
        )
    }
}

private suspend fun exportScenario(context: Context, uri: Uri, scenario: Scenario): ScenarioNotice.Message {
    val text = ScenarioFormat.encodeScenario(portableScenario(scenario))
    val saved = DocumentStore.writeText(context, uri, text)
    return ScenarioNotice.Message(
        text = context.getString(
            if (saved) R.string.scenarios_export_scenario_done else R.string.scenarios_export_failed,
        ),
        failed = !saved,
    )
}

internal const val MIME_JSON = "application/json"
