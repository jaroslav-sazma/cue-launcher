package app.cue.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuildSetupTest {

    @Test
    fun `applicationId matches the plan`() {
        val expected = if (BuildConfig.DEBUG) "app.cue.launcher.debug" else "app.cue.launcher"

        assertEquals(expected, BuildConfig.APPLICATION_ID)
    }
}
