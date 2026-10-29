package app.cue.launcher.system.bridge

import app.cue.launcher.data.bridge.BridgeSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.ByteArrayInputStream
import java.io.InputStream

class BridgeMessageVectorsTest {

    private val fixture = BridgeFixtures.load("messages.json")

    @Test
    fun `the limits and the version match the code`() {
        assertEquals(BRIDGE_PROTOCOL_VERSION, fixture.int("bridgeProtocolVersion"))
        assertEquals(BRIDGE_MAX_BODY_BYTES, fixture.int("maxBodyBytes"))
        assertEquals(BRIDGE_MAX_BINARY_BYTES, fixture.int("maxBinaryBytes"))
    }

    @Test
    fun `every message type has an example and every example a known type`() {
        val examples = fixture.objects("examples").map { it.str("type") }

        assertEquals(BridgeFraming.knownTypes, examples.toSet())
    }

    @Test
    fun `clipboard-set carries announce only when it is set`() {
        val bodies = fixture.objects("examples").filter { it.str("type") == "clipboard-set" }.map { it.str("body") }
        val messages = bodies.map { body ->
            bridgeJson.decodeFromString(BridgeMessage.serializer(), body) as BridgeMessage.ClipboardSet
        }

        assertEquals(listOf(null, true, null, null), messages.map { it.announce })
        assertEquals(listOf(false, false, true, false), messages.map { it.sensitive })
        assertEquals(listOf(null, null, null, 95_000L), messages.map { it.ageMs })
        assertEquals(
            """{"type":"clipboard-set","text":"a","sensitive":false}""",
            bridgeJson.encodeToString(BridgeMessage.serializer(), BridgeMessage.ClipboardSet("a", sensitive = false)),
        )
    }

    @Test
    fun `a notification carries its revision only when the phone gives one`() {
        val revisions = fixture.objects("examples").filter { it.str("type") == "notif-detail" }.map { example ->
            (bridgeJson.decodeFromString(BridgeMessage.serializer(), example.str("body")) as BridgeMessage.NotifDetail)
                .revision
        }

        assertEquals(listOf(null, 1_791_820_800_000L), revisions)
    }

    @Test
    fun `the phone's hello carries its clipboard switch, the Mac's none`() {
        val switches = fixture.objects("examples").filter { it.str("type") == "hello" }.map { example ->
            (bridgeJson.decodeFromString(BridgeMessage.serializer(), example.str("body")) as BridgeMessage.Hello)
                .clipboardFromMac
        }

        assertEquals(listOf(null, false), switches)
        assertEquals(
            BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION, clipboardFromMac = false),
            sessionHello(BridgeSettings(clipboardFromMac = false)),
        )
        assertEquals(true, sessionHello(BridgeSettings()).clipboardFromMac)
    }

    @TestFactory
    fun `examples decode and encode to the same frame`(): List<DynamicTest> = fixture.objects("examples").map { v ->
        DynamicTest.dynamicTest("${v.str("type")} ${v.str("body").length}") {
            val frame = v.hex("frame")
            val binary = if (v.containsKey("binary")) v.hex("binary") else null

            val inbound = readAll(ByteArrayInputStream(frame)).single()

            assertTrue(inbound is BridgeInbound.Message) { "$inbound" }
            val message = (inbound as BridgeInbound.Message).message
            assertEquals(v.str("type"), Json.parseToJsonElement(v.str("body")).jsonObject.str(BRIDGE_TYPE_FIELD))
            assertArrayEquals(binary, inbound.binary)
            assertEquals(v.str("frame"), BridgeFraming.encode(message, inbound.binary).toHexString())
            assertEquals(
                Json.parseToJsonElement(v.str("body")),
                Json.parseToJsonElement(bridgeJson.encodeToString(BridgeMessage.serializer(), message)),
            )
        }
    }

    @TestFactory
    fun `streams read as the vectors expect`(): List<DynamicTest> = fixture.objects("streams").map { v ->
        DynamicTest.dynamicTest(v.str("name")) {
            val input = ByteArrayInputStream(v.hex("stream"))
            v.objects("expect").forEach { expected ->
                when (expected.str("result")) {
                    "message" -> {
                        val inbound = BridgeFraming.read(input)
                        assertTrue(inbound is BridgeInbound.Message) { "$inbound" }
                        val message = (inbound as BridgeInbound.Message).message
                        assertEquals(expected.str("type"), typeOf(message))
                    }
                    "ignore" -> assertEquals(BridgeInbound.Unknown(expected.str("type")), BridgeFraming.read(input))
                    "reject" -> {
                        assertThrows(BridgeProtocolException::class.java) { BridgeFraming.read(input) }
                        return@dynamicTest
                    }
                    else -> error("unknown expectation ${expected.str("result")}")
                }
            }
            assertNull(BridgeFraming.read(input)) { "the stream did not end where the vector says" }
        }
    }

    @Test
    fun `a sidecar has to match the declared length`() {
        val message = BridgeMessage.IconData("00".repeat(FINGERPRINT_BYTES), 3)

        assertThrows(IllegalArgumentException::class.java) { BridgeFraming.encode(message, ByteArray(2)) }
        assertThrows(IllegalArgumentException::class.java) { BridgeFraming.encode(message) }
        assertThrows(IllegalArgumentException::class.java) {
            BridgeFraming.encode(BridgeMessage.Hello(BRIDGE_PROTOCOL_VERSION), ByteArray(1))
        }
    }

    @Test
    fun `a body over the limit is not encoded`() {
        val message = BridgeMessage.ClipboardSet("x".repeat(BRIDGE_MAX_BODY_BYTES), sensitive = false)

        assertThrows(IllegalArgumentException::class.java) { BridgeFraming.encode(message) }
    }

    @Test
    fun `a body at the limit is read`() {
        val empty = BridgeFraming.encode(BridgeMessage.ClipboardSet("", sensitive = false))
        val overhead = empty.size - BRIDGE_LENGTH_BYTES
        val message = BridgeMessage.ClipboardSet("x".repeat(BRIDGE_MAX_BODY_BYTES - overhead), sensitive = false)

        val frame = BridgeFraming.encode(message)

        assertEquals(BRIDGE_LENGTH_BYTES + BRIDGE_MAX_BODY_BYTES, frame.size)
        assertEquals(message, (BridgeFraming.read(ByteArrayInputStream(frame)) as BridgeInbound.Message).message)
    }

    @Test
    fun `a signal message carries a whole BLE frame`() {
        val frames = BridgeFixtures.load("frames.json")
        val first = frames.objects("vectors").first()
        val example = fixture.objects("examples").single { it.str("type") == "signal" }

        val message = (readAll(ByteArrayInputStream(example.hex("frame"))).single() as BridgeInbound.Message).message

        assertEquals(first.str("frame"), decodeBase64((message as BridgeMessage.Signal).frame).toHexString())
    }

    private fun readAll(input: InputStream): List<BridgeInbound> =
        generateSequence { BridgeFraming.read(input) }.toList()

    private fun typeOf(message: BridgeMessage): String =
        Json.parseToJsonElement(bridgeJson.encodeToString(BridgeMessage.serializer(), message))
            .jsonObject.str(BRIDGE_TYPE_FIELD)
}
