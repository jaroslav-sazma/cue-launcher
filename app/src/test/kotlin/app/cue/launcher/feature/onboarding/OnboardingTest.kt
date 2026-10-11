package app.cue.launcher.feature.onboarding

import app.cue.launcher.data.settings.LauncherSettings
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnboardingTest {

    @Test
    fun `a fresh install shows onboarding`() {
        assertTrue(onboardingVisible(LauncherSettings()))
    }

    @Test
    fun `a completed onboarding no longer pops up`() {
        assertFalse(onboardingVisible(LauncherSettings(onboardingSeen = true)))
    }

    @Test
    fun `unloaded settings are not treated as first launch`() {

        assertFalse(onboardingVisible(null))
    }
}
