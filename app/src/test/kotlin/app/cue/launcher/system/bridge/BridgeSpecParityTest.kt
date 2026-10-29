package app.cue.launcher.system.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BridgeSpecParityTest {

    private val spec: String by lazy { File(BridgeFixtures.REPO_ROOT, SPEC).readText() }

    @Test
    fun `the spec header carries the protocol version of the code`() {
        val header = VERSION_HEADER.find(spec)

        assertTrue(header != null) { "$SPEC has no `bridgeProtocolVersion: N` line" }
        assertEquals(BRIDGE_PROTOCOL_VERSION, header!!.groupValues[1].toInt())
    }

    @Test
    fun `the spec describes every message type`() {
        BridgeFraming.knownTypes.forEach { type ->
            assertTrue(spec.contains("### `$type`")) { "$SPEC has no section for the message `$type`" }
        }
    }

    @Test
    fun `the spec lists every BLE frame type with its code`() {
        BleFrameType.entries.forEach { type ->
            val name = type.name.lowercase().replace('_', '-')
            assertTrue(spec.contains("| ${type.code} | `$name` |")) {
                "$SPEC does not list frame type ${type.code} `$name`"
            }
        }
    }

    @Test
    fun `the spec names the derivation labels and the service type`() {
        listOf(
            PAIRING_COMMIT_LABEL,
            PAIRING_TRANSCRIPT_LABEL,
            PAIRING_KBLE_INFO,
            PAIRING_SAS_INFO,
            PAIRING_CONFIRM_INFO,
            L2CAP_KEYS_INFO,
            BRIDGE_SERVICE_TYPE,
            "$BRIDGE_TXT_VERSION_KEY=$BRIDGE_PROTOCOL_VERSION",
            "$BRIDGE_TXT_PAIR_KEY=$BRIDGE_TXT_PAIR_ON",
        ).forEach { label -> assertTrue(spec.contains("`$label`")) { "$SPEC does not name `$label`" } }
    }

    @Test
    fun `the spec's GATT service and characteristics are the code's`() {
        listOf(
            "Service" to BRIDGE_GATT_SERVICE,
            "`doorbell` characteristic" to BRIDGE_GATT_DOORBELL,
            "`command` characteristic" to BRIDGE_GATT_COMMAND,
            "`psm` characteristic" to BRIDGE_GATT_PSM,
        ).forEach { (element, uuid) ->
            assertTrue(spec.contains("| $element | `$uuid` |")) { "$SPEC does not give $element as `$uuid`" }
        }
    }

    @Test
    fun `the code requests an MTU that fits a maximal frame in one ATT payload`() {
        val floor = Regex("""requests an ATT MTU of at least (\d+)""").find(spec.replace(Regex("\\s+"), " "))
        assertTrue(floor != null) { "$SPEC names no MTU floor" }
        val minimum = floor!!.groupValues[1].toInt()
        assertTrue(minimum - ATT_HEADER_BYTES >= BleFrameCodec.MAX_FRAME_BYTES)
        assertTrue(BridgeGattLink.REQUESTED_MTU >= minimum)
    }

    @Test
    fun `the spec defines every bye, pair-abort and reply-error reason and both proof inputs`() {
        val reasons = listOf(BYE_VERSION_MISMATCH, BYE_REPLACED) + PairAbortReason.entries.map { it.wire } +
            listOf(REPLY_ERROR_GONE, REPLY_ERROR_NO_REPLY_ACTION, REPLY_ERROR_SEND_FAILED)
        val proofs = PairingRole.entries.map { "UTF-8 \"${it.proofData}\"" }

        (reasons.map { "`$it`" } + proofs).forEach { term ->
            assertTrue(spec.contains(term)) { "$SPEC does not define $term" }
        }
    }

    @Test
    fun `the spec catalogs every vector file`() {
        val files = BridgeFixtures.DIR.listFiles { file -> file.extension == "json" }.orEmpty().map { it.name }

        assertTrue(files.isNotEmpty())
        files.forEach { name -> assertTrue(spec.contains("`$name`")) { "$SPEC does not describe $name" } }
    }

    private companion object {

        const val SPEC = "docs/bridge-protocol.md"

        const val ATT_HEADER_BYTES = 3

        val VERSION_HEADER = Regex("""^`bridgeProtocolVersion: (\d+)`$""", RegexOption.MULTILINE)
    }
}
