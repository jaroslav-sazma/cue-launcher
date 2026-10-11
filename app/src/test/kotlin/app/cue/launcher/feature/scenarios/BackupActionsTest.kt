package app.cue.launcher.feature.scenarios

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackupActionsTest {

    @Test
    fun `size rounds up and is never zero`() {
        assertEquals(1, kilobytes(1))
        assertEquals(1, kilobytes(1024))
        assertEquals(2, kilobytes(1025))
        assertEquals(18, kilobytes(18_432))
    }

    @Test
    fun `an empty file reports one kilobyte`() {
        assertEquals(1, kilobytes(0))
    }
}
