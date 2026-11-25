package app.cue.launcher.engine.safe

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Arrays

class VaultVectorsTest {

    private data class Row(val path: String, val isDirectory: Boolean, val mtime: Long?, val content: String)

    @Test
    fun `the vectors are all there`() {
        assertEquals(9, VaultFixtures.vectors.objects("passwords").size)
        assertEquals(6, VaultFixtures.vectors.objects("positive").size)
        assertEquals(20, VaultFixtures.vectors.objects("negative").size)
        assertEquals(2, VaultFixtures.vectors.objects("accepted").size)
    }

    @TestFactory
    fun `every password canonicalizes to its expected bytes`(): List<DynamicTest> =
        VaultFixtures.vectors.objects("passwords").map { case ->
            DynamicTest.dynamicTest(case.str("description")) {
                assertEquals(case.str("expected"), VaultKeys.canonicalPassword(case.str("input")).toHexString())
            }
        }

    @TestFactory
    fun `every positive case opens to its entries`(): List<DynamicTest> =
        VaultFixtures.vectors.objects("positive").flatMap { case ->
            val inputs = case.getValue("inputs").jsonObject
            val expected = case.getValue("expected").jsonObject
            val credentials = listOf(
                Credential.Password(inputs.str("password")),
                Credential.Stored(inputs.hex("key_id"), inputs.hex("cek")),
            )
            credentials.map { credential ->
                DynamicTest.dynamicTest("${case.str("name")}, $credential") {
                    val file = VaultFixtures.bytes(expected.str("file"))
                    assertEquals(expected.str("sha256"), sha256(file))
                    val opened = openVault(file, credential)

                    assertEquals(expected.str("header"), opened.header.bytes.toHexString())
                    assertEquals(expected.long("segments"), openedLayout(file).segments)
                    assertEquals(expected.long("archive_size"), opened.reader.plaintextSize)
                    assertEquals(expected.str("archive_sha256"), sha256(opened.archive()))
                    if (credential is Credential.Password) {
                        val kek = VaultFixtures.kek(credential.value, opened.header)
                        assertEquals(expected.str("kek"), kek.toHexString())
                    }
                    assertEquals(expectedRows(inputs.objects("entries")), actualRows(opened))
                }
            }
        }

    // A cut file whose credential is wrong as well: from the length up front it is bad_header, while the
    // phone, which learns the length only after the key, may meet the credential's failure first.
    @TestFactory
    fun `every negative case fails with its category`(): List<DynamicTest> =
        VaultFixtures.vectors.objects("negative").flatMap { case ->
            val expected = case.getValue("expected").jsonObject
            val codes = listOfNotNull(expected.str("error"), expected["error_streaming"]?.jsonPrimitive?.content)
            listOf(true, false).map { layoutFirst ->
                DynamicTest.dynamicTest("${case.str("name")}, layout first: $layoutFirst") {
                    val file = VaultFixtures.bytes(expected.str("file"))
                    assertEquals(expected.str("sha256"), sha256(file))
                    val error = assertThrows(VaultException::class.java) {
                        openVault(file, credentialOf(case.getValue("credential").jsonObject), layoutFirst)
                    }
                    val allowed = if (layoutFirst) codes.take(1) else codes
                    assertTrue(error.failure.code in allowed, "${error.failure.code}: ${error.message}; want $allowed")
                }
            }
        }

    @TestFactory
    fun `every accepted case opens to its archive's entries as they are`(): List<DynamicTest> =
        VaultFixtures.vectors.objects("accepted").map { case ->
            val expected = case.getValue("expected").jsonObject
            val entries = case.getValue("mutation").jsonObject.objects("entries")
            DynamicTest.dynamicTest(case.str("name")) {
                val file = VaultFixtures.bytes(expected.str("file"))
                assertEquals(expected.str("sha256"), sha256(file))
                val opened = openVault(file, credentialOf(case.getValue("credential").jsonObject))
                assertEquals(rows(entries), actualRows(opened))
            }
        }

    @Test
    fun `a password unwraps the CEK without a separate KEK`() {
        val case = VaultFixtures.positive("long-path")
        val inputs = case.getValue("inputs").jsonObject
        val header = VaultHeader.read(ByteArrayInputStream(VaultFixtures.bytes("long-path.cuevault")))

        assertArrayEquals(inputs.hex("cek"), VaultKeys.cek(header, inputs.str("password")))
        assertEquals(inputs.str("vault_id"), header.vaultId.toHexString())
        assertEquals(inputs.str("key_id"), header.keyId.toHexString())
        assertEquals(inputs.long("written_at"), header.writtenAt)
        assertEquals(inputs.int("iterations"), header.iterations)
    }

    private fun rows(entries: List<JsonObject>): List<Row> =
        entries.map {
            val content = contentOf(it["content"]?.jsonObject).toHexString()
            Row(it.str("path"), it.str("type") == "dir", it.long("mtime"), content)
        }

    private fun expectedRows(entries: List<JsonObject>): List<Row> =
        rows(entries)
            .map { it.copy(path = Normalizer.normalize(it.path, Normalizer.Form.NFC)) }
            .sortedWith { a, b -> Arrays.compareUnsigned(a.path.encodeToByteArray(), b.path.encodeToByteArray()) }

    private fun actualRows(opened: OpenedVault): List<Row> = opened.index.entries.map {
        Row(it.path, it.isDirectory, it.mtime, opened.content(it).toHexString())
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()
}
