package app.cue.launcher.system.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class CompanionManifestTest {

    private val elements: List<Element> by lazy {
        val manifest = File(BridgeFixtures.REPO_ROOT, "app/src/main/AndroidManifest.xml")
        assertTrue(manifest.isFile) { "the manifest is not in the repository" }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(manifest)
        val all = document.getElementsByTagName("*")
        (0 until all.length).map { all.item(it) as Element }
    }

    private val service: Element? by lazy {
        elements.firstOrNull { it.tagName == "service" && it.android("name") == SERVICE_NAME }
    }

    private fun Element.android(name: String): String = getAttributeNS(ANDROID, name)

    @Test
    fun `the companion service is declared disabled, so the system never binds it while the bridge is off`() {
        val declared = service
        assertTrue(declared != null) { "the manifest has no $SERVICE_NAME service" }
        assertEquals("false", declared!!.android("enabled"))
    }

    @Test
    fun `only the system can bind it, and it answers the companion service action`() {
        val declared = service!!
        assertEquals("true", declared.android("exported"))
        assertEquals("android.permission.BIND_COMPANION_DEVICE_SERVICE", declared.android("permission"))
        val actions = declared.getElementsByTagName("action")
        assertEquals(1, actions.length)
        assertEquals("android.companion.CompanionDeviceService", (actions.item(0) as Element).android("name"))
    }

    @Test
    fun `the gate switches the companion service together with Send to Mac`() {
        assertEquals(
            listOf(ProcessTextActivity::class.java, BridgeCompanionService::class.java),
            BridgeComponentGate.BRIDGE_GATED_COMPONENTS,
        )
        assertEquals(".system.bridge.${BridgeCompanionService::class.java.simpleName}", SERVICE_NAME)
    }

    @Test
    fun `the presence permissions are declared and nothing scans`() {
        val permissions = elements.filter { it.tagName == "uses-permission" }.map { it.android("name") }

        assertTrue(permissions.containsAll(PRESENCE_PERMISSIONS)) { "missing one of $PRESENCE_PERMISSIONS" }
        assertFalse("android.permission.BLUETOOTH_SCAN" in permissions)
        val feature = elements.firstOrNull {
            it.tagName == "uses-feature" && it.android("name") == "android.software.companion_device_setup"
        }
        assertEquals("false", feature?.android("required"))
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        const val SERVICE_NAME = ".system.bridge.BridgeCompanionService"
        val PRESENCE_PERMISSIONS = listOf(
            "android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE",
            "android.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND",
            "android.permission.REQUEST_COMPANION_USE_DATA_IN_BACKGROUND",
        )
    }
}
