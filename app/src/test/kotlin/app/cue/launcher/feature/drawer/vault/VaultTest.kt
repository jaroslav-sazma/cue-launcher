package app.cue.launcher.feature.drawer.vault

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.system.InstalledApp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.text.Collator
import java.util.Locale

class VaultTest {

    private val czech = Collator.getInstance(Locale.forLanguageTag("cs"))

    @Test
    fun `apps are split into sections by the first letter`() {
        val vault = vaultOf(listOf(app("Banka"), app("Alza"), app("Auto")), czech)

        assertEquals(listOf("A", "B"), vault.letters)
        assertEquals(listOf("Alza", "Auto"), vault.sections[0].apps.map(InstalledApp::label))
        assertEquals(listOf("Banka"), vault.sections[1].apps.map(InstalledApp::label))
    }

    @Test
    fun `diacritics fall under the base letter`() {
        val vault = vaultOf(listOf(app("Úkoly"), app("ČSOB Smart"), app("Cena")), czech)

        assertEquals(listOf("C", "U"), vault.letters)
        assertEquals(listOf("Cena", "ČSOB Smart"), vault.sections[0].apps.map(InstalledApp::label))
    }

    @Test
    fun `chrome belongs in section C, even though it sorts after H`() {
        val vault = vaultOf(listOf(app("Hodiny"), app("Chrome"), app("ČSOB Smart")), czech)

        assertEquals(listOf("C", "H"), vault.letters)
        assertEquals(listOf("ČSOB Smart", "Chrome"), vault.sections[0].apps.map(InstalledApp::label))
    }

    @Test
    fun `digits and symbols fall into a single section at the end`() {
        val vault = vaultOf(listOf(app("9GAG"), app("Zprávy"), app("+Test"), app("Alza")), czech)

        assertEquals(listOf("A", "Z", OTHER_LETTER), vault.letters)
        assertEquals(listOf("+Test", "9GAG"), vault.sections.last().apps.map(InstalledApp::label))
    }

    @Test
    fun `an empty list gives an empty vault`() {
        val vault = vaultOf(emptyList(), czech)

        assertTrue(vault.letters.isEmpty())
        assertEquals(0, vault.headerItemIndex(0))
    }

    @Test
    fun `the header position counts headers and rows of previous sections too`() {
        val vault = vaultOf(listOf(app("Alza"), app("Auto"), app("Banka"), app("Cena")), czech)

        assertEquals(0, vault.headerItemIndex(0))
        assertEquals(3, vault.headerItemIndex(1))
        assertEquals(5, vault.headerItemIndex(2))
    }

    @Test
    fun `the section letter is uppercase and without diacritics`() {
        assertEquals("U", vaultLetterOf("Úkoly"))
        assertEquals("A", vaultLetterOf("alza"))
        assertEquals(OTHER_LETTER, vaultLetterOf("1Password"))
        assertEquals(OTHER_LETTER, vaultLetterOf(""))
    }

    private fun app(
        label: String,
        packageName: String = "app.test.${label.hashCode()}",
        userSerial: Long = 0,
    ) = InstalledApp(
        component = ComponentRef(packageName, "$packageName.Main", userSerial),
        label = label,
    )
}
