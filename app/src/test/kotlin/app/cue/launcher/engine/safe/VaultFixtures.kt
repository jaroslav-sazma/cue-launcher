package app.cue.launcher.engine.safe

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.File

internal object VaultFixtures {

    val DIR: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "fixtures/vault") }
        .first { it.isDirectory }

    val vectors: JsonObject = Json.parseToJsonElement(File(DIR, "vectors.json").readText()).jsonObject

    fun bytes(name: String): ByteArray = File(DIR, name).readBytes()

    fun positive(name: String): JsonObject = vectors.objects("positive").single { it.str("name") == name }

    private val keks = HashMap<String, ByteArray>()

    // PBKDF2 at 100 000 iterations is the slow part of every case, so each password and salt derives once.
    fun kek(password: String, header: VaultHeader): ByteArray = synchronized(keks) {
        keks.getOrPut("$password\u0000${header.salt.toHexString()}\u0000${header.iterations}") {
            VaultKeys.kek(password, header)
        }
    }
}

internal sealed interface Credential {
    data class Password(val value: String) : Credential
    class Stored(val keyId: ByteArray, val cek: ByteArray) : Credential {
        override fun toString() = "stored key"
    }
}

internal fun credentialOf(spec: JsonObject): Credential =
    if ("password" in spec) {
        Credential.Password(spec.str("password"))
    } else {
        val key = spec.getValue("stored_key").jsonObject
        Credential.Stored(key.hex("key_id"), key.hex("cek"))
    }

// The file in memory, with every read and the closing counted.
internal class ByteArraySource(val file: ByteArray) : VaultSource {
    var reads = 0
    var closed = false

    override fun read(position: Long, buffer: ByteArray, length: Int) {
        if (position + length > file.size) throw EOFException("$length bytes at $position of ${file.size}")
        reads++
        file.copyInto(buffer, 0, position.toInt(), position.toInt() + length)
    }

    override fun close() {
        closed = true
    }
}

internal class OpenedVault(val reader: VaultReader, val index: VaultArchive, val header: VaultHeader) {

    fun content(entry: VaultEntry): ByteArray =
        ByteArray(entry.size.toInt()).also { reader.read(entry.offset, it, 0, it.size) }

    // The whole plaintext, read in pieces of the given size.
    fun archive(piece: Int = 4093): ByteArray {
        val archive = ByteArray(reader.plaintextSize.toInt())
        for (at in archive.indices step piece) {
            val chunk = ByteArray(minOf(piece, archive.size - at))
            reader.read(at.toLong(), chunk, 0, chunk.size)
            chunk.copyInto(archive, at)
        }
        return archive
    }
}

internal fun cekOf(header: VaultHeader, credential: Credential): ByteArray = when (credential) {
    is Credential.Password -> VaultKeys.unwrap(header, VaultFixtures.kek(credential.value, header))
    is Credential.Stored -> credential.cek.also { VaultKeys.checkStoredKey(header, credential.keyId) }
}

// The phone's order: the header from the first bytes, the key, and only then the layout from the
// length, which the phone learns once the file is copied. layoutFirst is the order of a reader that
// knows the length up front, which the spec holds to exactly the first category.
internal fun openVault(file: ByteArray, credential: Credential, layoutFirst: Boolean = false): OpenedVault {
    val header = VaultHeader.read(ByteArrayInputStream(file))
    val early = if (layoutFirst) VaultLayout.of(header, file.size.toLong()) else null
    val cek = cekOf(header, credential)
    val reader = VaultReader(early ?: VaultLayout.of(header, file.size.toLong()), ByteArraySource(file), cek)
    return OpenedVault(reader, reader.index(), header)
}

internal fun openedLayout(file: ByteArray): VaultLayout =
    VaultLayout.of(VaultHeader.read(ByteArrayInputStream(file)), file.size.toLong())

internal fun assertFailure(expected: VaultFailure, block: () -> Unit) {
    val error = assertThrows(VaultException::class.java, block)
    assertEquals(expected, error.failure, error.message)
}

internal fun contentOf(spec: JsonObject?): ByteArray {
    if (spec == null) return ByteArray(0)
    val (form, value) = spec.entries.single()
    return when (form) {
        "utf8" -> value.jsonPrimitive.content.encodeToByteArray()
        "hex" -> value.jsonPrimitive.content.hexToByteArray()
        "repeat" -> ByteArray(value.jsonObject.int("count")) { value.jsonObject.hex("byte").single() }
        "sequence" -> ByteArray(value.jsonObject.int("count")) { it.toByte() }
        else -> error("unknown content form $form")
    }
}

internal fun JsonObject.str(name: String): String = getValue(name).jsonPrimitive.content

internal fun JsonObject.hex(name: String): ByteArray = str(name).hexToByteArray()

internal fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.int

internal fun JsonObject.long(name: String): Long = getValue(name).jsonPrimitive.long

internal fun JsonObject.objects(name: String): List<JsonObject> = getValue(name).jsonArray.map { it.jsonObject }
