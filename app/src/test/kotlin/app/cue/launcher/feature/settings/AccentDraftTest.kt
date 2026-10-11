package app.cue.launcher.feature.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AccentDraftTest {

    @Test
    fun `a valid entry is what the field sends, a half-typed one sends nothing new`() {
        val draft = AccentDraft.of(0x112233)

        assertEquals(0x3A86FF, draft.typed("#3a86ff").sent)
        assertEquals(0x112233, draft.typed("#11223").sent)
    }

    @Test
    fun `a save landing after the next keystroke keeps what is being typed`() {
        val typed = AccentDraft.of(0x112233).typed("#3A86FF").typed("#3A86F")

        assertEquals("#3A86F", typed.synced(0x3A86FF).text)
    }

    @Test
    fun `a colour changed elsewhere replaces the text`() {
        val typed = AccentDraft.of(0x112233).typed("#3A86F")

        assertEquals(AccentDraft("#FFEB3B", 0xFFEB3B), typed.synced(0xFFEB3B))
    }

    @Test
    fun `an older save arriving late is put right by the latest one`() {
        val typed = AccentDraft.of(0x112233).typed("#3A86FF").typed("#3A86FE")

        val healed = typed.synced(0x3A86FF).synced(0x3A86FE)

        assertEquals("#3A86FE", healed.text)
        assertEquals(0x3A86FE, healed.sent)
    }
}
