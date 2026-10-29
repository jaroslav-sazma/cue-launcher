package app.cue.launcher.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.cue.launcher.data.aichat.AiChatShare
import app.cue.launcher.data.ha.HaConfigRepository
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.feature.scenarios.aichat.AiChatHandoffScreen
import app.cue.launcher.feature.scenarios.aichat.LocalAiChatInventory
import app.cue.launcher.feature.scenarios.aichat.PasteScenarioScreen
import app.cue.launcher.feature.scenarios.builder.SUMMARY_STEP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object AiChatHandoff : NavKey

@Serializable
data object PasteScenario : NavKey

// Each step replaces the one before rather than stacking on it: back from the draft returns to the
// tab it started from, not to a check that is already done.
internal fun MutableList<NavKey>.replaceTop(from: NavKey, to: NavKey) {
    if (lastOrNull() == from) this[lastIndex] = to
}

// A second quick tap arrives while the first screen is still on top; stacking it twice would leave a
// stale copy under the draft that replaceTop cannot reach.
internal fun MutableList<NavKey>.pushOnce(key: NavKey) {
    if (lastOrNull() != key) add(key)
}

// The settings are written from the scope of the whole settings screen: the chat app stops this
// screen right after a send, and opening a draft takes the paste screen off the stack at once, yet
// both must still be recorded.
@Composable
internal fun AiChatHandoffDestination(
    haConfigs: HaConfigRepository,
    settings: SettingsRepository,
    scope: CoroutineScope,
    backStack: NavBackStack<NavKey>,
    pop: () -> Unit,
) {
    val share by remember(settings) { settings.settings.map { it.aiChatShare }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(initialValue = null)
    val haConfigured by remember(haConfigs) { haConfigs.config.map { it.isConfigured }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(initialValue = null)
    val stored: AiChatShare = share ?: return
    val ha: Boolean = haConfigured ?: return
    AiChatHandoffScreen(
        inventory = LocalAiChatInventory.current,
        share = stored,
        haConfigured = ha,
        onShareChange = { chosen -> scope.launch { settings.setAiChatShare(chosen) } },
        onSent = { scope.launch { settings.setAiChatPending(true) } },
        onBack = pop,
        onPaste = { backStack.replaceTop(AiChatHandoff, PasteScenario) },
    )
}

@Composable
internal fun PasteScenarioDestination(
    haConfigs: HaConfigRepository,
    settings: SettingsRepository,
    scope: CoroutineScope,
    backStack: NavBackStack<NavKey>,
    pop: () -> Unit,
) {
    val haConfigured by remember(haConfigs) { haConfigs.config.map { it.isConfigured } }
        .collectAsStateWithLifecycle(initialValue = false)
    val bridgePaired by remember(settings) {
        settings.settings.map { it.bridge.enabled && it.bridge.pairing != null }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    PasteScenarioScreen(
        inventory = LocalAiChatInventory.current,
        haConfigured = haConfigured,
        onConfigureHa = { backStack.add(HomeAssistant) },
        bridgePaired = bridgePaired,
        onConfigureBridge = { backStack.add(Bridge) },
        onBack = pop,
        onOpenDraft = { json ->
            backStack.replaceTop(PasteScenario, ScenarioBuilder(scenarioJson = json, step = SUMMARY_STEP))
        },
        onDraftOpened = { scope.launch { settings.setAiChatPending(false) } },
    )
}
