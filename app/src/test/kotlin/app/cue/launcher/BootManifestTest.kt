package app.cue.launcher

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

// The manifest merger takes what a library declares unless the app's own manifest removes it, so
// these entries are the whole guarantee that nothing starts at boot.
class BootManifestTest {

    private val elements: List<Element> by lazy {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(File(REPO_ROOT, MANIFEST))
        val all = document.getElementsByTagName("*")
        (0 until all.length).map { all.item(it) as Element }
    }

    @Test
    fun `no boot permission survives the merge`() {
        val permissions = elements.filter {
            it.tagName == "uses-permission" && it.getAttributeNS(ANDROID, "name") == BOOT_COMPLETED
        }

        assertTrue(permissions.isNotEmpty() && permissions.all { it.getAttributeNS(TOOLS, "node") == "remove" }) {
            "$BOOT_COMPLETED must be declared only with tools:node=\"remove\""
        }
    }

    @Test
    fun `no receiver listens for boot`() {
        val actions = elements.filter { it.tagName == "action" }.map { it.getAttributeNS(ANDROID, "name") }

        assertTrue(actions.none { it.endsWith("BOOT_COMPLETED") }) { "a receiver listens for boot" }
    }

    private companion object {

        const val MANIFEST = "app/src/main/AndroidManifest.xml"

        const val ANDROID = "http://schemas.android.com/apk/res/android"

        const val TOOLS = "http://schemas.android.com/tools"

        const val BOOT_COMPLETED = "android.permission.RECEIVE_BOOT_COMPLETED"

        val REPO_ROOT: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "fixtures/scenarios").isDirectory }
    }
}
