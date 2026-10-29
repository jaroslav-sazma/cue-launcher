package app.cue.launcher.system.bridge

import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer

internal sealed interface BridgeInbound {

    class Message(val message: BridgeMessage, val binary: ByteArray?) : BridgeInbound

    data class Unknown(val type: String) : BridgeInbound
}

internal fun bridgeBody(message: BridgeMessage): ByteArray =
    bridgeJson.encodeToString(BridgeMessage.serializer(), message).encodeToByteArray()

// Tree decoding coerces "1" into an int and "true" into a boolean, so the JSON type of every member
// is checked first. A null optional member reads as absent; a null required one is caught by the
// decoder.
internal fun jsonMembersMatch(descriptor: SerialDescriptor, json: JsonObject): Boolean =
    (0 until descriptor.elementsCount).all { index ->
        val value = json[descriptor.getElementName(index)]
        value == null || value is JsonNull || jsonMatches(descriptor.getElementDescriptor(index), value)
    }

private fun jsonMatches(descriptor: SerialDescriptor, value: JsonElement): Boolean =
    when (val kind = descriptor.kind) {
        is PrimitiveKind -> value is JsonPrimitive && primitiveMatches(kind, value)
        StructureKind.LIST -> value is JsonArray && value.all { item ->
            item !is JsonNull && jsonMatches(descriptor.getElementDescriptor(0), item)
        }
        StructureKind.CLASS -> value is JsonObject && jsonMembersMatch(descriptor, value)
        else -> true
    }

private fun primitiveMatches(kind: PrimitiveKind, value: JsonPrimitive): Boolean = when {
    kind == PrimitiveKind.STRING -> value.isString
    value.isString -> false
    kind == PrimitiveKind.INT -> value.intOrNull != null
    kind == PrimitiveKind.LONG -> value.longOrNull != null
    kind == PrimitiveKind.BOOLEAN -> value.booleanOrNull != null
    else -> true
}

internal object BridgeFraming {

    private val descriptors: Map<String, SerialDescriptor> =
        BridgeMessage.serializer().descriptor.getElementDescriptor(1).let { subtypes ->
            (0 until subtypes.elementsCount).associate { index ->
                subtypes.getElementName(index) to subtypes.getElementDescriptor(index)
            }
        }

    val knownTypes: Set<String> = descriptors.keys

    fun encode(message: BridgeMessage, binary: ByteArray? = null): ByteArray {
        val declared = (message as? BridgeMessage.IconData)?.binaryBytes
        require(declared == binary?.size) { "the sidecar does not match the declared $BRIDGE_BINARY_FIELD" }
        require(declared == null || declared in 1..BRIDGE_MAX_BINARY_BYTES) { "$declared is not a sidecar length" }
        val body = bridgeBody(message)
        require(body.size <= BRIDGE_MAX_BODY_BYTES) { "a ${body.size}-byte body exceeds the frame limit" }
        val tail = binary ?: ByteArray(0)
        return ByteBuffer.allocate(BRIDGE_LENGTH_BYTES + body.size + tail.size)
            .putInt(body.size).put(body).put(tail).array()
    }

    // Returns null only at a clean end of stream between frames; everything else that is not a whole,
    // well-formed frame throws, and the caller closes the connection.
    fun read(input: InputStream): BridgeInbound? {
        val data = DataInputStream(input)
        val length = readLength(data) ?: return null
        val json = parseBody(ByteArray(length).also { readFully(data, it, 0) })
        val type = (json[BRIDGE_TYPE_FIELD] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw BridgeProtocolException("the frame has no string \"$BRIDGE_TYPE_FIELD\"")
        val binary = sidecarLength(json)?.let { size -> ByteArray(size).also { readFully(data, it, 0) } }
        return if (type in knownTypes) {
            val message = decode(type, json)
            BridgeInbound.Message(message, binary.takeIf { message is BridgeMessage.IconData })
        } else {
            BridgeInbound.Unknown(type)
        }
    }

    private fun readLength(data: DataInputStream): Int? {
        val first = data.read()
        if (first < 0) return null
        val header = ByteArray(BRIDGE_LENGTH_BYTES).also { it[0] = first.toByte() }
        readFully(data, header, 1)
        val length = ByteBuffer.wrap(header).int
        if (length !in 1..BRIDGE_MAX_BODY_BYTES) throw BridgeProtocolException("frame length $length is out of range")
        return length
    }

    private fun decode(type: String, json: JsonObject): BridgeMessage {
        if (!jsonMembersMatch(descriptors.getValue(type), json)) {
            throw BridgeProtocolException("\"$type\" carries a member of the wrong JSON type")
        }
        return try {
            bridgeJson.decodeFromJsonElement(BridgeMessage.serializer(), json)
        } catch (e: IllegalArgumentException) {
            throw BridgeProtocolException("malformed \"$type\" message", e)
        }
    }

    private fun parseBody(body: ByteArray): JsonObject = try {
        bridgeJson.parseToJsonElement(body.decodeToString(throwOnInvalidSequence = true)) as? JsonObject
    } catch (e: IllegalArgumentException) {
        throw BridgeProtocolException("the frame body is not JSON", e)
    } catch (e: CharacterCodingException) {
        throw BridgeProtocolException("the frame body is not UTF-8", e)
    } ?: throw BridgeProtocolException("the frame body is not a JSON object")

    private fun sidecarLength(json: JsonObject): Int? {
        val field = json[BRIDGE_BINARY_FIELD]?.takeUnless { it is JsonNull } ?: return null
        val size = (field as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        if (size == null || size !in 1..BRIDGE_MAX_BINARY_BYTES) {
            throw BridgeProtocolException("\"$BRIDGE_BINARY_FIELD\" is not a valid sidecar length")
        }
        return size
    }

    private fun readFully(data: DataInputStream, into: ByteArray, offset: Int) {
        try {
            data.readFully(into, offset, into.size - offset)
        } catch (e: EOFException) {
            throw BridgeProtocolException("the stream ended inside a frame", e)
        }
    }
}
