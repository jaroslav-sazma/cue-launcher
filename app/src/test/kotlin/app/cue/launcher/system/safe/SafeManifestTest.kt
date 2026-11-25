package app.cue.launcher.system.safe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SafeManifestTest {

    private val provider: Element? by lazy {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "fixtures/vault").isDirectory }
        val manifest = File(root, "app/src/main/AndroidManifest.xml")
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val providers = factory.newDocumentBuilder().parse(manifest).getElementsByTagName("provider")
        (0 until providers.length).map { providers.item(it) as Element }
            .firstOrNull { it.android("name") == ".system.safe.${SafeDocumentsProvider::class.java.simpleName}" }
    }

    private fun Element.android(name: String): String = getAttributeNS(ANDROID, name)

    @Test
    fun `the provider is declared disabled, so a locked or never set up vault has none`() {
        assertNotNull(provider) { "the manifest declares no SafeDocumentsProvider" }
        assertEquals("false", provider!!.android("enabled"))
    }

    @Test
    fun `only the document UI reaches it directly, and only through grants beyond that`() {
        val declared = provider!!
        assertEquals("true", declared.android("exported"))
        assertEquals("true", declared.android("grantUriPermissions"))
        assertEquals("android.permission.MANAGE_DOCUMENTS", declared.android("permission"))
        assertEquals("\${applicationId}.vault", declared.android("authorities"))
        val action = declared.getElementsByTagName("action").item(0) as Element
        assertEquals("android.content.action.DOCUMENTS_PROVIDER", action.android("name"))
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
