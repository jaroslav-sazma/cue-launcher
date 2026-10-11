package app.cue.launcher

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

class IncomingDocumentTest {

    @Test
    fun `a content uri is accepted for import`() {
        assertTrue(isImportableScheme("content"))
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["file", "http", "https", "android.resource", "intent", "CONTENT"])
    fun `anything but a content uri is refused`(scheme: String?) {
        assertFalse(isImportableScheme(scheme), "scheme $scheme must not be importable")
    }
}
