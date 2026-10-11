package app.cue.launcher.feature.scenarios.aichat

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import app.cue.launcher.data.aichat.CachedInventory
import app.cue.launcher.data.aichat.CandidateCheck
import app.cue.launcher.data.aichat.InventoryGrounding
import app.cue.launcher.data.aichat.MAX_PASTED_CHARS
import app.cue.launcher.data.aichat.checkPasted
import app.cue.launcher.data.scenario.ScenarioFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Only the text, whether it was checked and whether it was cut are saved; the result is computed
// again on return. Opening a draft that passed is what ends a hand-off to the AI chat.
@Suppress("LongParameterList")
@Stable
internal class PasteState(
    private val checker: suspend (String) -> CandidateCheck,
    private val clear: () -> Unit,
    private val scope: CoroutineScope,
    text: String = "",
    checked: Boolean = false,
    truncated: Boolean = false,
    private val onDraftOpened: () -> Unit = {},
) {
    var text by mutableStateOf(text)
        private set

    var truncated by mutableStateOf(truncated)
        private set

    var checked by mutableStateOf(checked)
        private set

    var result by mutableStateOf<CandidateCheck?>(null)
        private set

    val checking: Boolean get() = checked && result == null

    val canCheck: Boolean get() = text.isNotBlank() && !checked

    private var job: Job? = null

    fun edit(value: String) {
        truncated = value.length > MAX_PASTED_CHARS
        text = value.take(MAX_PASTED_CHARS)
        checked = false
        result = null
        job?.cancel()
    }

    fun check() {
        checked = true
        result = null
        run()
    }

    fun start() {
        if (checking) run()
    }

    fun openDraft(open: (canonicalJson: String) -> Unit) {
        val scenario = (result as? CandidateCheck.Valid)?.scenario ?: return
        onDraftOpened()
        open(ScenarioFormat.canonicalJson(scenario))
    }

    fun stop() {
        job?.cancel()
        job = null
        clear()
    }

    private fun run() {
        val raw = text
        job?.cancel()
        job = scope.launch { result = checker(raw) }
    }

    companion object {
        @Suppress("LongParameterList")
        fun create(
            inventory: CachedInventory,
            scope: CoroutineScope,
            onDraftOpened: () -> Unit = {},
            text: String = "",
            checked: Boolean = false,
            truncated: Boolean = false,
        ): PasteState {
            val grounding = InventoryGrounding(inventory)
            return PasteState(
                checker = { checkPasted(it, grounding) },
                clear = inventory::clear,
                scope = scope,
                text = text,
                checked = checked,
                truncated = truncated,
                onDraftOpened = onDraftOpened,
            )
        }

        fun saver(
            inventory: CachedInventory,
            scope: CoroutineScope,
            onDraftOpened: () -> Unit = {},
        ): Saver<PasteState, Any> = listSaver(
            save = { listOf(it.text, it.checked, it.truncated) },
            restore = { saved ->
                create(inventory, scope, onDraftOpened, saved[0] as String, saved[1] as Boolean, saved[2] as Boolean)
            },
        )
    }
}
