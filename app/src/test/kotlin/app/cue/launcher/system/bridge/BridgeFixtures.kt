package app.cue.launcher.system.bridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import java.io.File

internal object BridgeFixtures {

    val REPO_ROOT: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "fixtures/bridge").isDirectory }

    val DIR = File(REPO_ROOT, "fixtures/bridge")

    fun load(name: String): JsonObject = Json.parseToJsonElement(File(DIR, name).readText()).jsonObject
}

internal fun JsonObject.str(name: String): String = getValue(name).jsonPrimitive.content

internal fun JsonObject.hex(name: String): ByteArray = str(name).hexToByteArray()

internal fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.int

internal fun JsonObject.long(name: String): Long = getValue(name).jsonPrimitive.long

internal fun JsonObject.longOrNull(name: String): Long? = getValue(name).jsonPrimitive.longOrNull

internal fun JsonObject.objects(name: String): List<JsonObject> = getValue(name).jsonArray.map { it.jsonObject }
