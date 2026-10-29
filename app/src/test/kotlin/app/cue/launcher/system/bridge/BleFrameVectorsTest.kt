package app.cue.launcher.system.bridge

import app.cue.launcher.data.bridge.BRIDGE_REPLAY_WINDOW
import app.cue.launcher.data.bridge.BridgeReplayWindow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class BleFrameVectorsTest {

    private val fixture = BridgeFixtures.load("frames.json")
    private val key = fixture.hex("kBle")

    @Test
    fun `the frame limit matches the code`() {
        assertEquals(BleFrameCodec.MAX_FRAME_BYTES, fixture.int("maxFrameBytes"))
    }

    @Test
    fun `the frame key is the one the first pairing case derives`() {
        assertEquals(BridgeFixtures.load("pairing.json").objects("cases").first().str("kBle"), fixture.str("kBle"))
    }

    @TestFactory
    fun `a notification key hashes to its first eight SHA-256 bytes`(): List<DynamicTest> =
        fixture.objects("keyHashes").map { v ->
            DynamicTest.dynamicTest("\"${v.str("key")}\"") {
                assertEquals(v.str("hash"), notificationKeyHash(v.str("key")))
            }
        }

    @TestFactory
    fun `frames seal to the vectors`(): List<DynamicTest> = fixture.objects("vectors").map { v ->
        DynamicTest.dynamicTest(v.str("name")) {
            val direction = direction(v)
            assertEquals(v.str("nonce"), BleFrameCodec.nonce(direction, v.long("seq")).toHexString())

            val plaintext = v.str("plaintext").encodeToByteArray()

            val frame = BleFrameCodec.seal(key, direction, v.int("type"), v.long("seq"), plaintext)

            assertEquals(v.str("frame"), frame.toHexString())
        }
    }

    @TestFactory
    fun `frames open to the vectors`(): List<DynamicTest> = fixture.objects("vectors").map { v ->
        DynamicTest.dynamicTest(v.str("name")) {
            val opened = BleFrameCodec.open(key, direction(v), v.hex("frame"), BridgeReplayWindow())

            assertTrue(opened is BleOpened.Accepted) { "$opened" }
            val frame = (opened as BleOpened.Accepted).frame
            assertEquals(v.int("type"), frame.type)
            assertEquals(v.long("seq"), frame.seq)
            assertEquals(v.str("plaintext"), frame.plaintext.decodeToString())
        }
    }

    @TestFactory
    fun `signals decode as the vectors expect`(): List<DynamicTest> = fixture.objects("vectors").map { v ->
        DynamicTest.dynamicTest(v.str("name")) {
            val plaintext = v.str("plaintext").encodeToByteArray()
            val signal = decodeBleSignal(v.int("type"), plaintext)

            when (v.str("signal")) {
                "valid" -> {
                    assertNotNull(signal)
                    assertEquals(v.int("type"), signal!!.type.code)
                    assertArrayEquals(plaintext, encodeBleSignal(signal))
                    assertTrue(direction(v) in signal.type.senders)
                }
                "unknown-type" -> assertNull(BleFrameType.of(v.int("type")))
                "wrong-direction" -> assertTrue(signal != null && direction(v) !in signal.type.senders)
                else -> assertNull(signal)
            }
        }
    }

    // A frame whose type is ignored was still accepted, so the window holds its seq from then on.
    @TestFactory
    fun `an ignored signal still uses up its seq`(): List<DynamicTest> =
        fixture.objects("vectors").filter { it.str("signal") != "valid" }.map { v ->
            DynamicTest.dynamicTest(v.str("name")) {
                val frame = v.hex("frame")
                val seq = v.long("seq")

                assertTrue(BleFrameCodec.open(key, direction(v), frame, windowOf(seq - 1)) is BleOpened.Accepted)
                assertEquals(
                    BleOpened.Rejected(BleRejection.REPLAY),
                    BleFrameCodec.open(key, direction(v), frame, windowOf(seq)),
                )
            }
        }

    @TestFactory
    fun `rejected frames fail for the stated reason`(): List<DynamicTest> = fixture.objects("rejections").map { v ->
        DynamicTest.dynamicTest(v.str("name")) {
            val window = windowOf(*v.getValue("accepted").jsonArray.map { it.jsonPrimitive.long }.toLongArray())

            val opened = BleFrameCodec.open(key, direction(v), v.hex("frame"), window)

            assertEquals("fail", v.str("expect"))
            assertEquals(BleOpened.Rejected(BleRejection.valueOf(v.str("reason").uppercase())), opened)
        }
    }

    @Test
    fun `the replay window has the size the vectors give`() {
        assertEquals(BRIDGE_REPLAY_WINDOW, fixture.getValue("replayWindow").jsonObject.int("size"))
    }

    @TestFactory
    fun `the replay window decides as the vectors expect`(): List<DynamicTest> =
        fixture.getValue("replayWindow").jsonObject.objects("cases").map { v ->
            DynamicTest.dynamicTest(v.str("name")) {
                var window = BridgeReplayWindow()
                val decided = v.getValue("offered").jsonArray.map { it.jsonPrimitive.long }.map { seq ->
                    if (window.permits(seq)) {
                        window = window.record(seq)
                        "accept"
                    } else {
                        "replay"
                    }
                }

                assertEquals(v.getValue("expect").jsonArray.map { it.jsonPrimitive.content }, decided)
            }
        }

    @Test
    fun `seq is checked against the window before the AEAD and only read there`() {
        val frame = BleFrameCodec.seal(key, BleDirection.PHONE_TO_MAC, 1, 7, "{}".encodeToByteArray())
        val window = windowOf(8)

        assertTrue(BleFrameCodec.open(key, BleDirection.PHONE_TO_MAC, frame, window) is BleOpened.Accepted)
        assertEquals(windowOf(8), window)
        assertEquals(
            BleOpened.Rejected(BleRejection.REPLAY),
            BleFrameCodec.open(key, BleDirection.PHONE_TO_MAC, frame, windowOf(8, 7)),
        )
    }

    @Test
    fun `seq stops at the u32 limit`() {
        assertThrows(IllegalArgumentException::class.java) {
            BleFrameCodec.seal(key, BleDirection.PHONE_TO_MAC, 1, BleFrameCodec.MAX_SEQ + 1, "{}".encodeToByteArray())
        }
    }

    @Test
    fun `a plaintext over the frame limit is refused`() {
        val plaintext = ByteArray(BleFrameCodec.MAX_PLAINTEXT_BYTES + 1)

        assertThrows(IllegalArgumentException::class.java) {
            BleFrameCodec.seal(key, BleDirection.MAC_TO_PHONE, 5, 0, plaintext)
        }
    }

    @Test
    fun `a reply too long for a frame falls back to the tcp flag`() {
        val hash = notificationKeyHash("key")

        assertEquals(BleSignal.Reply(hash, "Thanks!"), replySignal(hash, "Thanks!"))
        assertEquals(BleSignal.Reply(hash, null), replySignal(hash, "x".repeat(BleFrameCodec.MAX_PLAINTEXT_BYTES)))
        assertEquals(
            """{"k":"$hash","tcp":true}""",
            encodeBleSignal(replySignal(hash, "x".repeat(BleFrameCodec.MAX_PLAINTEXT_BYTES))).decodeToString(),
        )
    }

    @Test
    fun `a null revision or age reads as none, and a number in quotes is not a number`() {
        val hash = notificationKeyHash("key")

        assertEquals(BleSignal.Dismiss(hash), decodeBleSignal(3, """{"k":"$hash","r":null}""".encodeToByteArray()))
        assertEquals(
            BleSignal.ClipboardSmall("x", sensitive = false),
            decodeBleSignal(5, """{"t":"x","s":false,"a":null}""".encodeToByteArray()),
        )
        assertNull(decodeBleSignal(5, """{"t":"x","s":"false"}""".encodeToByteArray()))
        assertNull(decodeBleSignal(3, """{"k":"$hash","r":1.5}""".encodeToByteArray()))
    }

    @Test
    fun `a lock-state needs its state as a boolean`() {
        val code = BleFrameType.LOCK_STATE.code

        assertEquals(BleSignal.LockState(locked = true), decodeBleSignal(code, """{"l":true}""".encodeToByteArray()))
        listOf("{}", """{"l":null}""", """{"l":1}""", """{"l":"false"}""").forEach { plaintext ->
            assertNull(decodeBleSignal(code, plaintext.encodeToByteArray()), plaintext)
        }
    }

    @Test
    fun `every frame type has a vector`() {
        val covered = fixture.objects("vectors").filter { it.str("signal") == "valid" }.map { it.int("type") }.toSet()

        assertEquals(BleFrameType.entries.map { it.code }.toSet(), covered)
    }

    private fun windowOf(vararg accepted: Long): BridgeReplayWindow =
        accepted.fold(BridgeReplayWindow()) { window, seq -> window.record(seq) }

    private fun direction(v: JsonObject): BleDirection = when (v.str("direction")) {
        "phone-to-mac" -> BleDirection.PHONE_TO_MAC
        "mac-to-phone" -> BleDirection.MAC_TO_PHONE
        else -> error("unknown direction ${v.str("direction")}")
    }
}
