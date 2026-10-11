package app.cue.launcher.feature.scenarios

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URLDecoder

class AboutLinksTest {

    private val facts = BugReportFacts(
        device = "Google Pixel 10 Pro",
        android = "Android 17, BP4A.261005.001",
        version = "1.0.0 (3) from Google Play",
    )

    @Test
    fun `every prefilled field is a field id of the bug report form`() {
        val ids = FIELD_ID.findAll(repoFile(BUG_REPORT_FORM).readText()).map { it.groupValues[1] }.toSet()
        val fields = bugReportParameters(facts).map { it.first } - TEMPLATE_PARAMETER

        fields.forEach { field ->
            assertTrue(field in ids) { "$BUG_REPORT_FORM has no field with id: $field (ids: $ids)" }
        }
    }

    @Test
    fun `the template parameter names the bug report form`() {
        val template = bugReportParameters(facts).toMap().getValue(TEMPLATE_PARAMETER)

        assertTrue(repoFile(".github/ISSUE_TEMPLATE/$template").isFile) {
            "the issue template $template is not in .github/ISSUE_TEMPLATE"
        }
    }

    @Test
    fun `the url opens the new issue form of the repository`() {
        assertEquals("$REPOSITORY_URL/issues/new", bugReportUrl(facts).substringBefore('?'))
    }

    @Test
    fun `every value survives the query encoding`() {
        val tricky = BugReportFacts(device = "A & B = C", android = "Android 17, a+b/c?d#e", version = "0.1.0 (1) ü")
        val query = bugReportUrl(tricky).substringAfter('?')
        val decoded = query.split('&').associate { pair ->
            val (name, value) = pair.split('=', limit = 2)
            name to URLDecoder.decode(value, Charsets.UTF_8)
        }

        assertEquals(bugReportParameters(tricky).toMap(), decoded)
        assertTrue(' ' !in query && '+' !in query) { "a space must be encoded as %20, not '+': $query" }
    }

    @Test
    fun `the manufacturer is named once`() {
        assertEquals("Google Pixel 10 Pro", deviceName("Google", "Pixel 10 Pro"))
        assertEquals("OnePlus 12", deviceName("OnePlus", "OnePlus 12"))
        assertEquals("motorola edge 50", deviceName("Motorola", "motorola edge 50"))
    }

    @Test
    fun `the android build reads like the form's placeholder`() {
        assertEquals("Android 17, BP4A.261005.001", androidBuild("17", "BP4A.261005.001"))
    }

    @Test
    fun `the version says where the app came from`() {
        assertEquals("1.0.0 (3) from Google Play", appVersion("1.0.0", 3, InstallSource.GOOGLE_PLAY))
        assertEquals("1.0.0 (3) from GitHub", appVersion("1.0.0", 3, InstallSource.GITHUB))
        assertEquals("1.0.0-debug (3) built from source", appVersion("1.0.0-debug", 3, InstallSource.DEBUG_BUILD))
    }

    @Test
    fun `only the Play Store counts as Google Play, and a debug build is never a download`() {
        assertEquals(InstallSource.GOOGLE_PLAY, installSource("com.android.vending", debug = false))
        assertEquals(InstallSource.GITHUB, installSource(null, debug = false))
        assertEquals(InstallSource.GITHUB, installSource("com.google.android.packageinstaller", debug = false))
        assertEquals(InstallSource.DEBUG_BUILD, installSource(null, debug = true))
    }

    private fun repoFile(path: String): File = File(REPO_ROOT, path).also {
        assertTrue(it.isFile) { "the file $path is not in the repository" }
    }

    private companion object {

        const val BUG_REPORT_FORM = ".github/ISSUE_TEMPLATE/bug_report.yml"

        val FIELD_ID = Regex("""^\s+id:\s*(\S+)\s*$""", RegexOption.MULTILINE)

        val REPO_ROOT: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "fixtures/scenarios").isDirectory }
    }
}
