package app.cue.launcher.designsystem

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ReducedMotionTest {

    @Test
    fun `disabled animations mean reduced motion`() {
        assertTrue(reducedMotionAt(0f))
    }

    @ParameterizedTest(name = "animatorDurationScale {0} → full motion")
    @ValueSource(floats = [0.5f, 1.0f, 2.0f, 10.0f])
    fun `enabled animations run at full speed`(scale: Float) {
        assertFalse(reducedMotionAt(scale))
    }
}
