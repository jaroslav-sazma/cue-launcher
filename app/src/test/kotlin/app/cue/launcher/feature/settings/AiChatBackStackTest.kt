package app.cue.launcher.feature.settings

import androidx.navigation3.runtime.NavKey
import app.cue.launcher.feature.scenarios.builder.SUMMARY_STEP
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AiChatBackStackTest {

    @Test
    fun `a double tap on paste pushes one paste screen`() {
        val stack = mutableListOf<NavKey>(ScenariosTab)

        stack.pushOnce(PasteScenario)
        stack.pushOnce(PasteScenario)

        assertEquals(listOf(ScenariosTab, PasteScenario), stack)
    }

    @Test
    fun `back from the draft opened after a double tap returns to the list`() {
        val stack = mutableListOf<NavKey>(ScenariosTab)
        val draft = ScenarioBuilder(scenarioJson = "{}", step = SUMMARY_STEP)

        stack.pushOnce(PasteScenario)
        stack.pushOnce(PasteScenario)
        stack.replaceTop(PasteScenario, draft)
        stack.removeAt(stack.lastIndex)

        assertEquals(listOf<NavKey>(ScenariosTab), stack)
    }

    @Test
    fun `a double tap on the hand-off pushes one hand-off screen`() {
        val stack = mutableListOf<NavKey>(ScenariosTab)

        stack.pushOnce(AiChatHandoff)
        stack.pushOnce(AiChatHandoff)

        assertEquals(listOf(ScenariosTab, AiChatHandoff), stack)
    }

    @Test
    fun `a double tap on the hand-off screen's paste replaces it once`() {
        val stack = mutableListOf<NavKey>(ScenariosTab, AiChatHandoff)

        stack.replaceTop(AiChatHandoff, PasteScenario)
        stack.replaceTop(AiChatHandoff, PasteScenario)

        assertEquals(listOf(ScenariosTab, PasteScenario), stack)
    }

    @Test
    fun `another screen on top is still pushed`() {
        val stack = mutableListOf<NavKey>(ScenariosTab, AiChatHandoff)

        stack.pushOnce(PasteScenario)

        assertEquals(listOf(ScenariosTab, AiChatHandoff, PasteScenario), stack)
    }
}
