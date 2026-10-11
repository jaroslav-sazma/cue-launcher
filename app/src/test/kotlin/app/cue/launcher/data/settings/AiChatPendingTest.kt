package app.cue.launcher.data.settings

import app.cue.launcher.data.aichat.AiChatShare
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiChatPendingTest {

    private val repository = SettingsRepository(MemorySettingsStore(LauncherSettings(aiChatScenarios = true)))

    private suspend fun pending() = repository.settings.first().aiChatPending

    @Test
    fun `a send leaves the card pending until its close button`() = runTest {
        repository.setAiChatPending(true)
        assertTrue(pending())

        repository.setAiChatPending(false)

        assertFalse(pending())
    }

    @Test
    fun `switching the feature off clears the card, and switching it on does not bring it back`() = runTest {
        repository.setAiChatPending(true)

        repository.setAiChatScenarios(false)
        assertFalse(pending())
        repository.setAiChatScenarios(true)

        assertFalse(pending())
    }

    @Test
    fun `nothing turns pending while the feature is off`() = runTest {
        repository.setAiChatScenarios(false)

        repository.setAiChatPending(true)

        assertFalse(pending())
    }

    @Test
    fun `the choice of what to share is kept as made`() = runTest {
        val chosen = AiChatShare(bluetooth = false, homeAssistant = false)

        repository.setAiChatShare(chosen)

        assertEquals(chosen, repository.settings.first().aiChatShare)
    }
}
