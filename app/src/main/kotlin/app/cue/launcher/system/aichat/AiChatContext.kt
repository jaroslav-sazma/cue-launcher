package app.cue.launcher.system.aichat

import app.cue.launcher.data.aichat.AiChatOffer
import app.cue.launcher.data.aichat.AiChatShare
import app.cue.launcher.data.aichat.InventorySnapshot
import app.cue.launcher.data.aichat.InventorySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

// Read on the user's tap, while the screen that asked waits for it; nothing is registered and
// nothing outlives the call.
@Suppress("LongParameterList")
internal suspend fun collectAiChatContext(
    source: InventorySource,
    locale: Locale,
    route: PasteRoute,
    share: AiChatShare,
    offer: AiChatOffer,
    slots: WidgetSlots = emptyMap(),
): String = withContext(Dispatchers.Default) {
    aiChatContext(fullInventory(source, share.within(offer)), locale, route, share, offer, slots)
}

// The one message the user's chat app gets: who it talks for, the whole scenario format, the phone's
// inventory as far as the user shares it and the shape of the answer Cue: can read back. The same
// snapshot always gives the same text.
@Suppress("LongParameterList")
internal fun aiChatContext(
    inventory: InventorySnapshot,
    locale: Locale,
    route: PasteRoute,
    share: AiChatShare = AiChatShare.ALL,
    offer: AiChatOffer = AiChatOffer.ALL,
    slots: WidgetSlots = emptyMap(),
): String = listOf(
    INTRO,
    roleSection(locale),
    HOW_IT_WORKS,
    "## Scenario JSON reference\n${scenarioReference().trimEnd()}",
    LINKS,
    TEXT_VALUES,
    PLACEHOLDERS,
    CHECKS,
    examplesSection(),
    inventorySection(inventory, share, offer, slots).trimEnd(),
    finalAnswer(route),
).joinToString(separator = "\n\n", postfix = "\n")

private fun examplesSection(): String = buildString {
    appendLine("## Examples")
    appendLine("Each shows a finished scenario, written once the questions were answered: the request with the")
    appendLine("user's answers in it, the inventory lines it relied on and the complete scenario. Its values are")
    appendLine("that user's answers, not defaults to reuse; the scenario you write follows your user's answers")
    append("and this phone's inventory.")
    SCENARIO_EXAMPLES.forEachIndexed { index, example ->
        appendLine()
        appendLine()
        appendLine("### Example ${index + 1}: ${example.request}")
        appendLine("Inventory lines:")
        appendLine("```text")
        example.inventory.forEach(::appendLine)
        appendLine("```")
        appendLine("```json")
        appendLine(example.json)
        append("```")
    }
}

private val INTRO = """
    # Cue: scenario brief
    This message comes from Cue:, an Android home screen launcher, on behalf of its user. It carries
    only context: the user has not said yet what they want.
""".trimIndent()
