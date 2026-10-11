package app.cue.launcher.feature.scenarios.aichat

import androidx.compose.runtime.saveable.SaverScope
import app.cue.launcher.data.aichat.CachedInventory
import app.cue.launcher.data.aichat.CandidateCheck
import app.cue.launcher.data.aichat.CandidateProblem
import app.cue.launcher.data.aichat.InventorySnapshot
import app.cue.launcher.data.aichat.InventorySource
import app.cue.launcher.data.aichat.MAX_PASTED_CHARS
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioFormat
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PasteStateTest {

    private class Harness(
        scope: CoroutineScope,
        text: String = "",
        checked: Boolean = false,
        val result: CandidateCheck = RESULT,
    ) {
        val checkedTexts = mutableListOf<String>()
        var clears = 0
        var opened = 0
        var gate: CompletableDeferred<Unit>? = null

        val state = PasteState(
            checker = { raw ->
                checkedTexts += raw
                gate?.await()
                result
            },
            clear = { clears++ },
            scope = scope,
            text = text,
            checked = checked,
            onDraftOpened = { opened++ },
        )
    }

    @Test
    fun `opening a draft that passed ends the hand-off and hands over its canonical JSON`() = runTest {
        val harness = Harness(this, result = CandidateCheck.Valid(DRAFT))
        harness.state.edit("{}")
        harness.state.check()
        advanceUntilIdle()
        val drafts = mutableListOf<String>()

        harness.state.openDraft(drafts::add)

        assertEquals(1, harness.opened)
        assertEquals(listOf(ScenarioFormat.canonicalJson(DRAFT)), drafts)
    }

    @Test
    fun `an answer that did not pass opens nothing and keeps the hand-off`() = runTest {
        val harness = Harness(this)
        harness.state.openDraft { error("opened $it") }
        harness.state.edit("{}")
        harness.state.check()
        advanceUntilIdle()

        harness.state.openDraft { error("opened $it") }

        assertEquals(0, harness.opened)
    }

    @Test
    fun `text beyond the limit is cut and says so`() = runTest {
        val state = Harness(this).state

        state.edit("x".repeat(MAX_PASTED_CHARS + 1))

        assertEquals(MAX_PASTED_CHARS, state.text.length)
        assertTrue(state.truncated)
    }

    @Test
    fun `text within the limit is kept whole and clears the note`() = runTest {
        val state = Harness(this).state
        state.edit("x".repeat(MAX_PASTED_CHARS + 1))

        state.edit("x".repeat(MAX_PASTED_CHARS))

        assertEquals(MAX_PASTED_CHARS, state.text.length)
        assertFalse(state.truncated)
    }

    @Test
    fun `a check runs on the text and shows its result`() = runTest {
        val harness = Harness(this)
        harness.state.edit("{}")

        harness.state.check()
        assertTrue(harness.state.checking)
        assertFalse(harness.state.canCheck)
        advanceUntilIdle()

        assertEquals(RESULT, harness.state.result)
        assertFalse(harness.state.checking)
        assertEquals(listOf("{}"), harness.checkedTexts)
    }

    @Test
    fun `editing during a check cancels it`() = runTest {
        val harness = Harness(this).apply { gate = CompletableDeferred() }
        harness.state.edit("{}")
        harness.state.check()
        runCurrent()

        harness.state.edit("{ }")
        harness.gate?.complete(Unit)
        advanceUntilIdle()

        assertNull(harness.state.result)
        assertFalse(harness.state.checked)
        assertTrue(harness.state.canCheck)
    }

    @Test
    fun `a check cut short by leaving runs again on return`() = runTest {
        val harness = Harness(this).apply { gate = CompletableDeferred() }
        harness.state.edit("{}")
        harness.state.check()
        runCurrent()

        harness.state.stop()
        harness.gate = null
        advanceUntilIdle()
        assertNull(harness.state.result)
        assertEquals(1, harness.clears)

        harness.state.start()
        advanceUntilIdle()

        assertEquals(RESULT, harness.state.result)
        assertEquals(listOf("{}", "{}"), harness.checkedTexts)
    }

    @Test
    fun `an unchecked text is not checked on return`() = runTest {
        val harness = Harness(this, text = "{}")

        harness.state.start()
        advanceUntilIdle()

        assertTrue(harness.checkedTexts.isEmpty())
    }

    @Test
    fun `a checked text restored after a process death is checked again on start`() = runTest {
        val harness = Harness(this, text = "{}", checked = true)

        assertTrue(harness.state.checking)
        harness.state.start()
        advanceUntilIdle()

        assertEquals(RESULT, harness.state.result)
    }

    @Test
    fun `the text, the check and the cut survive a process death`() = runTest {
        val inventory = CachedInventory(InventorySource { InventorySnapshot.EMPTY })
        val saver = PasteState.saver(inventory, this)
        val state = PasteState.create(inventory, this)
        state.edit("y".repeat(MAX_PASTED_CHARS + 5))
        state.check()

        val saved = with(saver) { AnySaverScope.save(state) }
        val restored = requireNotNull(saver.restore(requireNotNull(saved)))
        state.stop()

        assertEquals(state.text, restored.text)
        assertTrue(restored.checked)
        assertTrue(restored.truncated)
        assertNull(restored.result)
    }

    private object AnySaverScope : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private companion object {
        val RESULT = CandidateCheck.Invalid(listOf(CandidateProblem.ScenarioCount(0)))

        val DRAFT = Scenario(
            id = "draft",
            name = "Draft",
            size = TileSize.GRID_1X1,
            conditions = emptyList(),
            content = TileContent.Template(
                TemplateLayout.ICON_TEXT,
                items = listOf(TemplateItem(actions = listOf(TapAction.App("com.maps")))),
            ),
        )
    }
}
